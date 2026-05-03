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
-keep,includedescriptorclasses class com.fmz.spenitaicore.**$$serializer { *; }
-keepclassmembers class com.fmz.spenitaicore.** { *** Companion; }
-keepclasseswithmembers class com.fmz.spenitaicore.** { kotlinx.serialization.KSerializer serializer(...); }
