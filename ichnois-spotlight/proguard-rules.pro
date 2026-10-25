##---------------Begin: proguard configuration for ichnois-spotlight  ----------

# The SDK checks at runtime if this class is available via Class.forName
-keep class io.ichnois.spotlight.SpotlightIntegration { <init>(...); }

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

##---------------End: proguard configuration for ichnois-spotlight  ----------
