# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Gson and Data layer rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { *; }

# Keep JSoup (used for HTML parsing in fallbacks and search)
-keep class org.jsoup.** { *; }

# Keep all source code from this project to prevent crashes with Compose Navigation,
# Hilt injection, DataStore reflection, and Enums, while still allowing R8 to 
# heavily minify and obfuscate the massive external libraries (which fulfills Google Play requirements).
-keep class com.ferlagod.rocinante.** { *; }

# Fix for AndroidX WorkManager Room database crash (R8 full mode strips the reflection-instantiated _Impl)
-keep class androidx.work.impl.WorkDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }