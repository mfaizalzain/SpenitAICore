# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.fmz.spenit.**$$serializer { *; }
-keepclassmembers class com.fmz.spenit.** { *** Companion; }
-keepclasseswithmembers class com.fmz.spenit.** { kotlinx.serialization.KSerializer serializer(...); }
