# --- 1. JNI SAFETY (WAJIB) ---
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- 2. ADB ENTRY POINTS (WAJIB) ---
-keep class com.aaudio.forwarder.AudioForwardService {
    <init>(...);
}
-keep class com.aaudio.forwarder.MainActivity {
    <init>(...);
}

# --- 3. PERFORMANCE OPTIMIZATION ---
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# --- 4. DEBUGGING INFO ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile