# JNI PROTECTION
-keep class com.aaudio.forwarder.AudioForwardService { *; }

# Jaga Activity
-keep class com.aaudio.forwarder.MainActivity { *; }

# OPTIMIZATION TWEAKS
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''