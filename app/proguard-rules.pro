# ReelDrop ProGuard/R8 rules.
# R8 is currently disabled for release builds (see app/build.gradle.kts) so that the
# shipped APK is guaranteed to behave exactly like the tested debug build.
# These rules are ready for the day minification is enabled.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# kotlinx.serialization
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
