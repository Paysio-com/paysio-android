# Paysio Mobile SDK consumer rules.

# VGS Collect's documented keep rule when minifyEnabled is on
# (docs.verygoodsecurity.com, Android SDK > Integration > R8/ProGuard).
-keep public class com.google.android.material** { *; }

# Keep the SDK's public surface and the kotlinx.serialization-generated code.
-keep class com.paysio.sdk.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontwarn org.jetbrains.annotations.**
