-keep public class com.google.android.gms.* { public *; }
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}
-keep class com.filtershekanha.teledr.** { *; }
-keep class com.coremedia.** { *; }
-dontwarn com.coremedia.**
-dontwarn com.filtershekanha.teledr.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.common.cache.**
-dontwarn com.google.common.primitives.**
-dontwarn com.googlecode.mp4parser.**
# Use -keep to explicitly keep any other classes shrinking would remove
-dontoptimize
-dontobfuscate

# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**

# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*

# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.ConscryptPlatform