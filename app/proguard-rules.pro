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
# --- YouCloud ---------------------------------------------------------------------------------
# R8 is on for the speed of the code it optimises, Compose's above all; names are kept, so stack
# traces stay readable and whatever finds a class or field by its name still finds it.
-dontobfuscate
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable

# Gson reads and writes the models by reflection: the saved library, the queue, the APIs' JSON.
-keep class com.example.myapplication.data.** { *; }
# Listening together sends its messages as JSON with Gson, read by field name on the other phone.
-keep class com.example.myapplication.together.TogetherMessage { *; }
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }

# NewPipe's extractor runs YouTube's player script in Rhino, which is reflection throughout.
-keep class org.schabi.newpipe.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn java.beans.**
-dontwarn javax.script.**

# yt-dlp for Android: its results are read with Jackson, the Python it unpacks with commons-compress.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
