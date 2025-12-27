-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.android.sound.helper.AudioForwardService { *; }
-keep class com.android.sound.helper.MainActivity { *; }
-keep class com.android.sound.helper.** { *; }

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile