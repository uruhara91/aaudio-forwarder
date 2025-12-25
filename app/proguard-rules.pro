# AAudio Forwarder ProGuard Rules

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep service & activity
-keep class com.aaudio.forwarder.AudioForwardService {
    public <methods>;
    native <methods>;
}

-keep class com.aaudio.forwarder.MainActivity {
    public <methods>;
}

# Keep audio & media projection classes
-keep class android.media.** { *; }
-keep class android.media.projection.** { *; }

# Optimize but don't obfuscate for debugging
-dontobfuscate
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile