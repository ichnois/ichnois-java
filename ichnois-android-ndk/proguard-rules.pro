##---------------Begin: proguard configuration for NDK  ----------

# The Android SDK checks at runtime if this class is available via Class.forName
-keep class io.ichnois.android.ndk.IchnoisCoreNdk { *; }

# The JNI layer uses this class through reflection
-keep class io.ichnois.protocol.DebugImage { *; }

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames,includedescriptorclasses class io.ichnois.** {
    native <methods>;
}

# don't warn jetbrains annotations
-dontwarn org.jetbrains.annotations.**

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

##---------------End: proguard configuration for NDK  ----------
