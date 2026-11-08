# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.pypath.app.**$$serializer { *; }
-keepclassmembers class com.pypath.app.** { *** Companion; }
-keepclasseswithmembers class com.pypath.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Google Sign-In (Credential Manager): keep the Play services provider found by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
