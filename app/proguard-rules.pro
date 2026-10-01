# Gson / Retrofit read these classes by reflection: keep their field names.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.example.spotifylyricsproxy.** { *; }

# Spotify SDKs
-keep class com.spotify.** { *; }
-dontwarn com.spotify.**
