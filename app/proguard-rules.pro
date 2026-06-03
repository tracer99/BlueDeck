# Add project specific ProGuard rules here.

# Keep Retrofit models
-keepclassmembers class com.blueandroid.data.models.** { *; }
-keep class com.blueandroid.data.models.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }
