-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

# project specific.
-keep,includedescriptorclasses class id.cachet.wallet.generated.models.**$$serializer { *; }
-keepclassmembers class id.cachet.wallet.generated.models.** { *** Companion; }
-keepclasseswithmembers class id.cachet.wallet.generated.models.** { kotlinx.serialization.KSerializer serializer(...); }
