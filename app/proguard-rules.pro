# Keep WebView JS interfaces if added in the future
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep SplashPromo data classes
-keep class com.ararabr.app.** { *; }
