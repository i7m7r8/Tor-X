# TOR-X - Ultimate SNI + Tor VPN

Ultra-fast, pure Rust-based SNI-customizable Tor VPN for Android. Proton VPN-style UI with complete feature parity and more.

## Features

- **Pure Rust Core**: SNI tunneling + Tor routing via JNI
- **Material Design 3 UI**: Modern Proton VPN clone with Compose
- **SNI Obfuscation**: Custom SNI hostname, server, port configuration
- **Tor Integration**: arti-client 0.38 with bridge support
- **Kill Switch**: Block all traffic on VPN disconnect
- **Split Tunneling**: Route specific apps outside VPN
- **IPv6 Protection**: Block IPv6 leaks
- **Custom DNS**: DoH support, multiple DNS servers
- **Multi-Architecture**: arm64-v8a, armeabi-v7a, x86_64 APKs
- **Auto-Release**: GitHub Actions CI/CD with signed APKs

## Build

```bash
./gradlew assembleRelease
```

## Release

All APKs are auto-built and released on every push via GitHub Actions.

## Architecture

```
┌─────────────────────────────────────┐
│  Kotlin Compose UI (5000+ lines)   │
├─────────────────────────────────────┤
│  JNI Bridge (Async Callbacks)       │
├─────────────────────────────────────┤
│  Rust Core (5000+ lines)            │
│  ├─ SNI Tunnel (Custom Config)      │
│  ├─ Tor Bridge (arti-client)        │
│  ├─ Packet Routing & Encryption     │
│  └─ State Management                │
├─────────────────────────────────────┤
│  Android VpnService                 │
│  ├─ TUN Device Interface            │
│  ├─ Packet Interception             │
│  └─ DNS/IP Routing                  │
└─────────────────────────────────────┘
```

## Permissions

- `BIND_VPN_SERVICE`: VPN functionality
- `CHANGE_NETWORK_STATE`: Network management
- `ACCESS_NETWORK_STATE`: Network status
- `INTERNET`: Network access
- `RECEIVE_BOOT_COMPLETED`: Auto-start on boot

## Technologies

- **Kotlin**: 1.9.23
- **Android**: API 26-35
- **Gradle**: 8.11
- **Java**: 17
- **Rust**: 1.75+
- **NDK**: 26.1.10909125
- **Compose**: 1.6.8+
- **Material3**: 1.2.1
- **arti-client**: 0.18
- **Tokio**: 1.36

## Roadmap

- [ ] Proxy support (HTTP/HTTPS/SOCKS5)
- [ ] WireGuard integration
- [ ] Custom Tor exit node selection
- [ ] Advanced packet filtering
- [ ] Real-time packet inspection
- [ ] Cloud sync (encrypted)

## License

GPL v3

## Author

TOR-X Development Team
