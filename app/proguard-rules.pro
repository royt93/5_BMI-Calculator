# ============================================================
# AppLovin MAX
# ============================================================
-keep class com.applovin.** { *; }
-dontwarn com.applovin.**

# ============================================================
# AdMob / Google Mobile Ads
# ============================================================
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# ============================================================
# Room Database — giữ entities và DAOs khỏi bị rename
# ============================================================
-keep class com.samsunggalaxy.data.** { *; }
-keepclassmembers class com.samsunggalaxy.data.** { *; }

# ============================================================
# DataBinding — giữ generated binding classes
# ============================================================
-keep class com.samsunggalaxy.databinding.** { *; }

# ============================================================
# AdmobWrapper SDK
# ============================================================
-keep class com.roy.sdkadbmob.** { *; }
-dontwarn com.roy.sdkadbmob.**

# ============================================================
# Kotlin Coroutines
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ============================================================
# MPAndroidChart
# ============================================================
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ============================================================
# Navigation Component
# ============================================================
-keep class androidx.navigation.** { *; }

# ============================================================
# Enum classes — tránh bị strip
# ============================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# Parcelable
# ============================================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ============================================================
# Serializable
# ============================================================
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
