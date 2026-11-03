# Add project specific ProGuard rules here.
-keep class haddad.three.notas.widget.** extends androidx.glance.appwidget.action.ActionCallback { *; }
-keep class haddad.three.notas.widget.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class haddad.three.notas.widget.** extends androidx.glance.appwidget.GlanceAppWidget { *; }

# Kotlinx Serialization preservation for sync and cloud backup DTOs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers enum * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class * implements kotlinx.serialization.KSerializer {
    <init>(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
