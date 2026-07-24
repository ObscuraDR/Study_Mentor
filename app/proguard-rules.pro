# Kotlinx Serialization ------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.elenglish.studymentor.**$$serializer { *; }
-keepclassmembers class com.elenglish.studymentor.** {
    *** Companion;
}
-keepclasseswithmembers class com.elenglish.studymentor.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp ----------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation class retrofit2.Response
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Tink / androidx.security-crypto ---------------------------------------------
# Tink references Error Prone annotations that exist only at compile time, so
# they are legitimately absent from the runtime classpath.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
# Tink's KeysDownloader pulls in the Google HTTP client and Joda-Time. This app
# only uses local Keystore-backed encryption, so that code path is unreachable
# and R8 is free to strip it.
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# Room -----------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**
