##---------------Begin: proguard configuration for android-core  ----------

##---------------Begin: proguard configuration for androidx.core  ----------
-keep class androidx.core.app.FrameMetricsAggregator { <init>(...); }
-keep interface androidx.core.view.ScrollingView { *; }
##---------------End: proguard configuration for androidx.core  ----------

##---------------Begin: proguard configuration for androidx.lifecycle  ----------
-keep interface androidx.lifecycle.DefaultLifecycleObserver { *; }
-keep class androidx.lifecycle.ProcessLifecycleOwner { <init>(...); }
##---------------End: proguard configuration for androidx.lifecycle  ----------

# To mitigate the issue on R8 site (https://issuetracker.google.com/issues/235733922)
# which comes through AGP 7.3.0-betaX and 7.4.0-alphaX
-keepclassmembers enum io.ichnois.** { *; }

# To filter out io.ichnois frames from stacktraces
-keeppackagenames io.ichnois.**

# don't warn jetbrains annotations
-dontwarn org.jetbrains.annotations.**
# don't warn about missing classes, as we are checking for their presence at runtime
-dontwarn io.ichnois.android.timber.IchnoisCoreTimberIntegration
-dontwarn io.ichnois.android.fragment.FragmentLifecycleIntegration
-dontwarn io.ichnois.compose.gestures.ComposeGestureTargetLocator
-dontwarn io.ichnois.compose.viewhierarchy.ComposeViewHierarchyExporter

# androidx.activity is a compileOnly dependency, used by the user feedback screenshot picker
# its presence is checked at runtime before use
-dontwarn androidx.activity.ComponentActivity
-dontwarn androidx.activity.result.**

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

# Preserve distinct runtime identities for custom Throwables. R8 horizontal class merging can
# otherwise merge unrelated exception classes, causing the runtime type and retraced frames to
# disagree. Unused Throwables may still be removed, and retained Throwables may still be obfuscated.
-keep,allowshrinking,allowobfuscation class * extends java.lang.Throwable

# Keep Classnames for integrations
-keepnames class * implements io.ichnois.Integration

-dontwarn io.ichnois.apollo.IchnoisCoreApolloInterceptor
-keepnames class io.ichnois.apollo.IchnoisCoreApolloInterceptor

-dontwarn io.ichnois.apollo3.IchnoisCoreApollo3HttpInterceptor
-keepnames class io.ichnois.apollo3.IchnoisCoreApollo3HttpInterceptor

-dontwarn io.ichnois.android.okhttp.IchnoisCoreOkHttpInterceptor
-keepnames class io.ichnois.android.okhttp.IchnoisCoreOkHttpInterceptor

-dontwarn io.ichnois.android.navigation.IchnoisCoreNavigationListener
-keepnames class io.ichnois.android.navigation.IchnoisCoreNavigationListener

-keepnames class io.ichnois.android.core.ScreenshotEventProcessor
-keepnames class io.ichnois.android.core.ViewHierarchyEventProcessor

# Keep any custom option classes like IchnoisCoreAndroidOptions, as they're loaded via reflection
# Also keep method names, as they're e.g. used by native via JNI calls
-keep class * extends io.ichnois.IchnoisCoreOptions { *; }

-keepnames class io.ichnois.android.core.ApplicationNotResponding


##---------------End: proguard configuration for android-core  ----------

##---------------Begin: proguard configuration for ichnois-apollo-3  ----------

# don't warn about missing classes, as it depends on the ichnois-apollo-3 jar dependency.
-dontwarn io.ichnois.apollo3.IchnoisCoreApollo3ClientException

# we don't want this class to be obfuscated, otherwise issue's titles are obfuscated as well.
-keep class io.ichnois.apollo3.IchnoisCoreApollo3ClientException { <init>(...); }

##---------------End: proguard configuration for ichnois-apollo-3  ----------

##---------------Begin: proguard configuration for ichnois-okhttp  ----------

# we don't want this class to be obfuscated, otherwise issue's titles are obfuscated as well.
-keepnames class io.ichnois.exception.IchnoisCoreHttpClientException

##---------------End: proguard configuration for ichnois-okhttp  ----------

##---------------Begin: proguard configuration for ichnois-android-replay  ----------
-dontwarn io.ichnois.android.replay.ReplayIntegration
-dontwarn io.ichnois.android.replay.DefaultReplayBreadcrumbConverter
-dontwarn io.ichnois.android.replay.util.MaskRenderer
-dontwarn io.ichnois.android.replay.util.ViewsKt
-dontwarn io.ichnois.android.replay.viewhierarchy.ViewHierarchyNode$Companion
-dontwarn io.ichnois.android.replay.viewhierarchy.ViewHierarchyNode
-keepnames class io.ichnois.android.replay.ReplayIntegration
##---------------End: proguard configuration for ichnois-android-replay  ----------

##---------------Begin: proguard configuration for ichnois-android-distribution  ----------
-dontwarn io.ichnois.android.distribution.DistributionIntegration
-keepnames class io.ichnois.android.distribution.DistributionIntegration
##---------------End: proguard configuration for ichnois-android-distribution  ----------

##---------------Begin: proguard configuration for ichnois-spotlight  ----------
-dontwarn io.ichnois.spotlight.SpotlightIntegration
-keepnames class io.ichnois.spotlight.SpotlightIntegration
##---------------End: proguard configuration for ichnois-spotlight  ----------
