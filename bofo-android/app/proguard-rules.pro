# BOFO release rules (Stage 9, R8). Written WITHOUT a build: if a release build crashes or a screen is
# blank, read docs/RELEASE.md > "If the release build misbehaves" and send the first error.

# Readable crash reports: keep file names + line numbers, hide the original file name.
# Upload app/build/outputs/mapping/release/mapping.txt to Play Console with each release.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# MascotView animates the property "mascotAngle" by NAME (ObjectAnimator.ofFloat(this, "mascotAngle", ...)).
# R8 would rename / remove the setter, and the animation would silently stop.
-keepclassmembers class com.bofo.finder.ui.MascotView {
    public void setMascotAngle(float);
}

# Fragments are created by the Navigation component from the class names in nav_graph.xml. AAPT already
# generates keep rules for them; this is a second safety net (they have a public no-arg constructor).
-keep public class com.bofo.finder.ui.*Fragment { public <init>(); }
-keep public class com.bofo.finder.ui.*Sheet { public <init>(); }

# View binding classes are only used through generated code; nothing else to keep.
# org.json is the platform's own copy (android.jar), not shipped in the APK.
# Glide, OkHttp, MapLibre, Credential Manager and Play Services ship their own consumer rules.
# The lines below only silence warnings about optional classes that are not on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn org.maplibre.**
