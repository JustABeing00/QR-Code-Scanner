# Keep rules for release builds (minification currently disabled, but rules stay for safety).
-keep class com.phonapps.qrscanner.data.db.** { *; }
-keep class androidx.camera.** { *; }
-dontwarn com.google.mlkit.**
