package com.example.spotifylyricsproxy.spotify.webapi

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object SpotifyWebApiClient {

    private const val BASE_URL = "https://api.spotify.com/"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                android.util.Log.i("SpotifyApi", "${request.method} ${request.url}")
                val response = chain.proceed(request)
                if (response.code in 200..299) {
                    android.util.Log.i("SpotifyApi", "→ ${response.code}")
                } else {
                    val body = response.peekBody(2048)?.string() ?: ""
                    android.util.Log.i("SpotifyApi", "→ ${response.code} body=${body}")
                }
                response
            }
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: SpotifyWebApi by lazy {
        retrofit.create(SpotifyWebApi::class.java)
    }

    fun authHeader(accessToken: String) = "Bearer $accessToken"
}
