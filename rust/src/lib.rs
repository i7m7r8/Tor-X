#![allow(non_snake_case)]

use jni::JNIEnv;
use jni::objects::{JClass, JString, JObject, JValue};
use jni::sys::{jint, jlong, jboolean, jbyteArray};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::net::{IpAddr, SocketAddr};
use std::str::FromStr;
use tokio::sync::RwLock;
use tokio::net::TcpStream;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use bytes::{BytesMut, Bytes};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use parking_lot::Mutex;
use lazy_static::lazy_static;
use log::{info, warn, error, debug};
use std::time::{SystemTime, Duration};
use sha2::{Sha256, Digest};
use base64::{Engine, engine::general_purpose};
use std::pin::Pin;
use std::task::{Context, Poll};
use tokio::io::{AsyncRead, AsyncWrite};

mod sni;
mod tor;
mod vpn;
mod crypto;
mod packet;
mod state;
mod error;

pub use sni::SNITunnel;
pub use tor::TorBridge;
pub use vpn::VPNService;
pub use error::TorXError;

const MAX_BUFFER_SIZE: usize = 65536;
const SOCKET_TIMEOUT_SECS: u64 = 30;
const MAX_CONNECTIONS: usize = 256;
const PACKET_POOL_SIZE: usize = 512;

lazy_static! {
    static ref RUNTIME: tokio::runtime::Runtime = tokio::runtime::Runtime::new().unwrap();
    static ref VPN_STATE: Arc<VPNState> = Arc::new(VPNState::new());
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct SNIConfig {
    pub server: String,
    pub port: u16,
    pub sni_hostname: String,
    pub custom_host: Option<String>,
    pub enabled: bool,
    pub use_ipv6: bool,
    pub timeout_ms: u64,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct TorConfig {
    pub enabled: bool,
    pub bridge_lines: Vec<String>,
    pub exit_country: Option<String>,
    pub use_meek: bool,
    pub pt_exec_path: Option<String>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct VPNConfig {
    pub name: String,
    pub sni_config: SNIConfig,
    pub tor_config: TorConfig,
    pub dns_servers: Vec<String>,
    pub kill_switch: bool,
    pub split_tunnel: bool,
    pub split_apps: Vec<String>,
    pub block_ipv6: bool,
    pub mtu: u16,
}

pub struct VPNState {
    config: RwLock<VPNConfig>,
    connected: AtomicBool,
    bytes_received: AtomicU64,
    bytes_sent: AtomicU64,
    active_connections: Mutex<HashMap<u64, ConnectionHandle>>,
    sni_tunnel: RwLock<Option<SNITunnel>>,
    tor_bridge: RwLock<Option<TorBridge>>,
    jni_callbacks: Mutex<JNICallbacks>,
}

#[derive(Clone)]
struct JNICallbacks {
    on_state_change: Option<Arc<Box<dyn Fn(bool) + Send + Sync>>>,
    on_stats_update: Option<Arc<Box<dyn Fn(u64, u64, u64) + Send + Sync>>>,
    on_error: Option<Arc<Box<dyn Fn(String) + Send + Sync>>>,
}

impl Default for JNICallbacks {
    fn default() -> Self {
        Self {
            on_state_change: None,
            on_stats_update: None,
            on_error: None,
        }
    }
}

pub struct ConnectionHandle {
    id: u64,
    local_addr: SocketAddr,
    remote_addr: SocketAddr,
    established: SystemTime,
    bytes_sent: u64,
    bytes_received: u64,
    active: bool,
}

impl VPNState {
    pub fn new() -> Self {
        Self {
            config: RwLock::new(VPNConfig {
                name: "Default".to_string(),
                sni_config: SNIConfig {
                    server: String::new(),
                    port: 443,
                    sni_hostname: String::new(),
                    custom_host: None,
                    enabled: false,
                    use_ipv6: false,
                    timeout_ms: 5000,
                },
                tor_config: TorConfig {
                    enabled: false,
                    bridge_lines: vec![],
                    exit_country: None,
                    use_meek: false,
                    pt_exec_path: None,
                },
                dns_servers: vec!["1.1.1.1".to_string(), "1.0.0.1".to_string()],
                kill_switch: true,
                split_tunnel: false,
                split_apps: vec![],
                block_ipv6: true,
                mtu: 1500,
            }),
            connected: AtomicBool::new(false),
            bytes_received: AtomicU64::new(0),
            bytes_sent: AtomicU64::new(0),
            active_connections: Mutex::new(HashMap::new()),
            sni_tunnel: RwLock::new(None),
            tor_bridge: RwLock::new(None),
            jni_callbacks: Mutex::new(JNICallbacks::default()),
        }
    }

    pub async fn connect(&self) -> Result<(), TorXError> {
        let config = self.config.read().await;
        
        // Step 1: Establish SNI tunnel
        if config.sni_config.enabled {
            info!("Initiating SNI tunnel to {}", config.sni_config.server);
            let sni_tunnel = SNITunnel::new(config.sni_config.clone()).await?;
            let mut tunnel = self.sni_tunnel.write().await;
            *tunnel = Some(sni_tunnel);
        }
        
        // Step 2: Bridge through Tor
        if config.tor_config.enabled {
            info!("Initializing Tor circuit");
            let tor_bridge = TorBridge::new(config.tor_config.clone()).await?;
            let mut bridge = self.tor_bridge.write().await;
            *bridge = Some(tor_bridge);
        }
        
        self.connected.store(true, Ordering::SeqCst);
        info!("VPN connection established");
        
        drop(config);
        self.notify_state_change(true);
        Ok(())
    }

    pub async fn disconnect(&self) -> Result<(), TorXError> {
        info!("Disconnecting VPN");
        
        // Close SNI tunnel
        let mut sni = self.sni_tunnel.write().await;
        if let Some(tunnel) = sni.take() {
            drop(tunnel);
        }
        
        // Close Tor bridge
        let mut tor = self.tor_bridge.write().await;
        if let Some(bridge) = tor.take() {
            drop(bridge);
        }
        
        self.connected.store(false, Ordering::SeqCst);
        
        // Clear connections
        self.active_connections.lock().clear();
        
        self.notify_state_change(false);
        Ok(())
    }

    pub fn is_connected(&self) -> bool {
        self.connected.load(Ordering::Relaxed)
    }

    pub fn get_stats(&self) -> (u64, u64) {
        let rx = self.bytes_received.load(Ordering::Relaxed);
        let tx = self.bytes_sent.load(Ordering::Relaxed);
        (rx, tx)
    }

    pub async fn update_config(&self, config: VPNConfig) -> Result<(), TorXError> {
        let mut cfg = self.config.write().await;
        *cfg = config;
        Ok(())
    }

    pub async fn get_config(&self) -> VPNConfig {
        self.config.read().await.clone()
    }

    fn notify_state_change(&self, connected: bool) {
        if let Some(cb) = &self.jni_callbacks.lock().on_state_change {
            cb(connected);
        }
    }

    fn notify_stats(&self, duration_secs: u64) {
        let (rx, tx) = self.get_stats();
        if let Some(cb) = &self.jni_callbacks.lock().on_stats_update {
            cb(rx, tx, duration_secs);
        }
    }

    fn notify_error(&self, error: String) {
        if let Some(cb) = &self.jni_callbacks.lock().on_error {
            cb(error);
        }
    }

    pub async fn route_packet(&self, packet: Bytes) -> Result<Bytes, TorXError> {
        // Route through SNI tunnel first
        let mut packet = packet;
        
        if let Some(tunnel) = self.sni_tunnel.read().await.as_ref() {
            packet = tunnel.process_packet(packet).await?;
        }
        
        // Then through Tor bridge
        if let Some(bridge) = self.tor_bridge.read().await.as_ref() {
            packet = bridge.process_packet(packet).await?;
        }
        
        Ok(packet)
    }
}

impl Clone for VPNConfig {
    fn clone(&self) -> Self {
        Self {
            name: self.name.clone(),
            sni_config: self.sni_config.clone(),
            tor_config: self.tor_config.clone(),
            dns_servers: self.dns_servers.clone(),
            kill_switch: self.kill_switch,
            split_tunnel: self.split_tunnel,
            split_apps: self.split_apps.clone(),
            block_ipv6: self.block_ipv6,
            mtu: self.mtu,
        }
    }
}

// JNI Exports
#[no_mangle]
pub extern "C" fn Java_com_torx_core_VPNCore_initNative(
    env: JNIEnv,
    _class: JClass,
) -> jlong {
    android_logger::init_once(
        android_logger::Config::default()
            .with_min_level(log::Level::Debug),
    );
    
    info!("TOR-X Core initialized");
    Box::into_raw(Box::new(VPN_STATE.clone())) as jlong
}

#[no_mangle]
pub extern "C" fn Java_com_torx_core_VPNCore_connect(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    let state = unsafe { &*(handle as *const Arc<VPNState>) };
    
    let runtime = &RUNTIME;
    match runtime.block_on(state.connect()) {
        Ok(_) => 1,
        Err(e) => {
            error!("Connection failed: {}", e);
            0
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_torx_core_VPNCore_disconnect(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    let state = unsafe { &*(handle as *const Arc<VPNState>) };
    
    let runtime = &RUNTIME;
    match runtime.block_on(state.disconnect()) {
        Ok(_) => 1,
        Err(e) => {
            error!("Disconnect failed: {}", e);
            0
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_torx_core_VPNCore_isConnected(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    let state = unsafe { &*(handle as *const Arc<VPNState>) };
    state.is_connected() as jboolean
}

#[no_mangle]
pub extern "C" fn Java_com_torx_core_VPNCore_getStats(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jbyteArray {
    let state = unsafe { &*(handle as *const Arc<VPNState>) };
    let (rx, tx) = state.get_stats();
    
    let stats = format!("{},{}", rx, tx);
    let bytes = stats.as_bytes();
    
    let arr = env.new_byte_array(bytes.len() as i32).unwrap();
    env.set_byte_array_region(&arr, 0, unsafe { std::mem::transmute(bytes) })
        .unwrap();
    
    arr
}

#[no_mangle]
pub extern "C" fn Java_com_torx_core_VPNCore_setSNIServer(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    server: JString,
    port: jint,
    hostname: JString,
) -> jint {
    let state = unsafe { &*(handle as *const Arc<VPNState>) };
    
    let server_str: String = env.get_string(&server)
        .unwrap_or_default()
        .into();
    let hostname_str: String = env.get_string(&hostname)
        .unwrap_or_default()
        .into();
    
    let runtime = &RUNTIME;
    let future = async {
        let mut cfg = state.config.write().await;
        cfg.sni_config.server = server_str;
        cfg.sni_config.port = port as u16;
        cfg.sni_config.sni_hostname = hostname_str;
        cfg.sni_config.enabled = true;
    };
    
    runtime.block_on(future);
    1
}

#[no_mangle]
pub extern "C" fn Java_com_torx_core_VPNCore_setTorConfig(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    bridge_lines: JString,
    use_meek: jboolean,
) -> jint {
    let state = unsafe { &*(handle as *const Arc<VPNState>) };
    
    let bridges: String = env.get_string(&bridge_lines)
        .unwrap_or_default()
        .into();
    
    let runtime = &RUNTIME;
    let future = async {
        let mut cfg = state.config.write().await;
        cfg.tor_config.bridge_lines = bridges
            .lines()
            .map(|s| s.to_string())
            .collect();
        cfg.tor_config.use_meek = use_meek != 0;
        cfg.tor_config.enabled = true;
    };
    
    runtime.block_on(future);
    1
}

#[no_mangle]
pub extern "C" fn Java_com_torx_core_VPNCore_setKillSwitch(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    enabled: jboolean,
) -> jint {
    let state = unsafe { &*(handle as *const Arc<VPNState>) };
    
    let runtime = &RUNTIME;
    let future = async {
        let mut cfg = state.config.write().await;
        cfg.kill_switch = enabled != 0;
    };
    
    runtime.block_on(future);
    1
}

#[no_mangle]
pub extern "C" fn Java_com_torx_core_VPNCore_setSplitTunnel(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    enabled: jboolean,
    apps: JString,
) -> jint {
    let state = unsafe { &*(handle as *const Arc<VPNState>) };
    
    let app_list: String = env.get_string(&apps)
        .unwrap_or_default()
        .into();
    
    let runtime = &RUNTIME;
    let future = async {
        let mut cfg = state.config.write().await;
        cfg.split_tunnel = enabled != 0;
        cfg.split_apps = app_list
            .split(',')
            .map(|s| s.trim().to_string())
            .collect();
    };
    
    runtime.block_on(future);
    1
}

#[no_mangle]
pub extern "C" fn Java_com_torx_core_VPNCore_routePacket(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    packet: jbyteArray,
) -> jbyteArray {
    let state = unsafe { &*(handle as *const Arc<VPNState>) };
    
    let packet_len = env.get_array_length(&packet).unwrap_or(0) as usize;
    let mut buf = vec![0u8; packet_len];
    env.get_byte_array_region(&packet, 0, &mut buf).ok();
    
    let packet_bytes = Bytes::from(buf);
    
    let runtime = &RUNTIME;
    let result = runtime.block_on(state.route_packet(packet_bytes));
    
    match result {
        Ok(routed) => {
            let arr = env.new_byte_array(routed.len() as i32).unwrap();
            env.set_byte_array_region(&arr, 0, &routed).ok();
            arr
        }
        Err(_) => {
            env.new_byte_array(0).unwrap()
        }
    }
}

// SNI Module
mod sni {
    use super::*;

    pub struct SNITunnel {
        config: SNIConfig,
        stream: Option<TcpStream>,
        buffer: BytesMut,
    }

    impl SNITunnel {
        pub async fn new(config: SNIConfig) -> Result<Self, TorXError> {
            let addr = format!("{}:{}", config.server, config.port)
                .parse::<SocketAddr>()
                .map_err(|e| TorXError::ConfigError(e.to_string()))?;

            info!("Connecting to SNI server: {}", addr);
            
            let stream = TcpStream::connect(addr).await
                .map_err(|e| TorXError::NetworkError(e.to_string()))?;

            stream.set_nodelay(true)
                .map_err(|e| TorXError::NetworkError(e.to_string()))?;

            info!("SNI tunnel established: {}", addr);

            Ok(Self {
                config,
                stream: Some(stream),
                buffer: BytesMut::with_capacity(MAX_BUFFER_SIZE),
            })
        }

        pub async fn process_packet(&self, packet: Bytes) -> Result<Bytes, TorXError> {
            // Obfuscate packet with SNI metadata
            let sni_frame = self.create_sni_frame(&packet)?;
            Ok(sni_frame)
        }

        fn create_sni_frame(&self, data: &Bytes) -> Result<Bytes, TorXError> {
            let mut frame = BytesMut::with_capacity(data.len() + 64);
            
            // Frame header
            frame.extend_from_slice(b"\x16\x03\x01"); // TLS handshake version
            
            let len = (data.len() + 5) as u16;
            frame.extend_from_slice(&len.to_be_bytes());
            frame.extend_from_slice(b"\x01"); // Client hello
            
            let sni_len = self.config.sni_hostname.len() as u16;
            frame.extend_from_slice(&sni_len.to_be_bytes());
            frame.extend_from_slice(self.config.sni_hostname.as_bytes());
            
            frame.extend_from_slice(data);
            
            Ok(frame.freeze())
        }
    }

    impl Drop for SNITunnel {
        fn drop(&mut self) {
            info!("SNI tunnel closed");
        }
    }
}

// Tor Module
mod tor {
    use super::*;

    pub struct TorBridge {
        config: TorConfig,
        circuits: Vec<TorCircuit>,
    }

    pub struct TorCircuit {
        id: u64,
        established: SystemTime,
        bytes_transferred: u64,
    }

    impl TorBridge {
        pub async fn new(config: TorConfig) -> Result<Self, TorXError> {
            info!("Initializing Tor bridge with {} bridge lines", config.bridge_lines.len());

            let mut circuits = Vec::new();
            
            // Create initial circuits
            for _ in 0..3 {
                let circuit = TorCircuit {
                    id: uuid::Uuid::new_v4().as_u128() as u64,
                    established: SystemTime::now(),
                    bytes_transferred: 0,
                };
                circuits.push(circuit);
            }

            info!("Tor bridge ready with {} circuits", circuits.len());

            Ok(Self {
                config,
                circuits,
            })
        }

        pub async fn process_packet(&self, packet: Bytes) -> Result<Bytes, TorXError> {
            // Route through least-used circuit
            let circuit = self.circuits.iter()
                .min_by_key(|c| c.bytes_transferred)
                .ok_or_else(|| TorXError::CircuitError("No circuits available".to_string()))?;

            // Onion encrypt packet
            let onion_packet = self.onion_encrypt(&packet)?;
            
            Ok(onion_packet)
        }

        fn onion_encrypt(&self, data: &Bytes) -> Result<Bytes, TorXError> {
            let mut encrypted = BytesMut::with_capacity(data.len() + 32);
            
            // Layer encryption (simplified, real impl uses full onion routing)
            let digest = Sha256::digest(data);
            encrypted.extend_from_slice(&digest);
            encrypted.extend_from_slice(data);
            
            Ok(encrypted.freeze())
        }
    }

    impl Drop for TorBridge {
        fn drop(&mut self) {
            info!("Tor bridge closed");
        }
    }
}

// VPN Service Module
mod vpn {
    use super::*;

    pub struct VPNService {
        mtu: u16,
        dns_servers: Vec<IpAddr>,
    }

    impl VPNService {
        pub fn new(config: &VPNConfig) -> Result<Self, TorXError> {
            let dns_servers = config.dns_servers.iter()
                .filter_map(|ip| IpAddr::from_str(ip).ok())
                .collect();

            Ok(Self {
                mtu: config.mtu,
                dns_servers,
            })
        }

        pub fn get_config_bytes(&self) -> Bytes {
            let mut cfg = BytesMut::new();
            
            cfg.extend_from_slice(b"VERSION=2\n");
            cfg.extend_from_slice(format!("MTU={}\n", self.mtu).as_bytes());
            
            for dns in &self.dns_servers {
                cfg.extend_from_slice(format!("DNS={}\n", dns).as_bytes());
            }
            
            cfg.freeze()
        }
    }
}

// Crypto Module
mod crypto {
    use super::*;
    use hmac::{Hmac, Mac};

    pub struct CryptoEngine {
        key: [u8; 32],
    }

    impl CryptoEngine {
        pub fn new(key: &[u8]) -> Result<Self, TorXError> {
            let mut k = [0u8; 32];
            if key.len() < 32 {
                k[..key.len()].copy_from_slice(key);
            } else {
                k.copy_from_slice(&key[..32]);
            }
            Ok(Self { key: k })
        }

        pub fn hmac(&self, data: &[u8]) -> [u8; 32] {
            type HmacSha256 = Hmac<Sha256>;
            let mut mac = HmacSha256::new_from_slice(&self.key).unwrap();
            mac.update(data);
            let result = mac.finalize();
            let mut out = [0u8; 32];
            out.copy_from_slice(&result.into_bytes());
            out
        }
    }
}

// Packet Module
mod packet {
    use super::*;

    pub struct PacketProcessor {
        pool: Vec<Bytes>,
    }

    impl PacketProcessor {
        pub fn new(pool_size: usize) -> Self {
            Self {
                pool: Vec::with_capacity(pool_size),
            }
        }

        pub fn process(&self, data: Bytes) -> Result<Bytes, TorXError> {
            // Basic packet validation
            if data.is_empty() {
                return Err(TorXError::PacketError("Empty packet".to_string()));
            }
            Ok(data)
        }
    }
}

// State Module
mod state {
    use super::*;

    #[derive(Clone, Debug)]
    pub struct ConnectionStats {
        pub duration: Duration,
        pub bytes_sent: u64,
        pub bytes_received: u64,
        pub packets_sent: u64,
        pub packets_received: u64,
    }
}

// Error Module
mod error {
    use std::fmt;

    #[derive(Debug, Clone)]
    pub enum TorXError {
        NetworkError(String),
        ConfigError(String),
        CircuitError(String),
        PacketError(String),
        CryptoError(String),
        IOError(String),
    }

    impl fmt::Display for TorXError {
        fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
            match self {
                TorXError::NetworkError(e) => write!(f, "Network: {}", e),
                TorXError::ConfigError(e) => write!(f, "Config: {}", e),
                TorXError::CircuitError(e) => write!(f, "Circuit: {}", e),
                TorXError::PacketError(e) => write!(f, "Packet: {}", e),
                TorXError::CryptoError(e) => write!(f, "Crypto: {}", e),
                TorXError::IOError(e) => write!(f, "IO: {}", e),
            }
        }
    }

    impl std::error::Error for TorXError {}
}
