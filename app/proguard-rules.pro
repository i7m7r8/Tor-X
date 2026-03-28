# Preserve TOR-X core classes
-keep class com.torx.core.** { *; }
-keep class com.torx.service.** { *; }
-keep class com.torx.ui.** { *; }

# Preserve JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve Kotlin metadata
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class kotlin.** { *; }

# Preserve Compose
-keep class androidx.compose.** { *; }

# Preserve Lifecycle
-keep class androidx.lifecycle.** { *; }

# ViewModels
-keepclasseswithmembernames class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# Serialization
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Remove logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
