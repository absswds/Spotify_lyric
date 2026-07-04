package com.example.spotifylyricsproxy.spotify.webapi

import android.app.Activity
import android.content.Intent
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

class SpotifyWebApiAuth(
    private val clientId: String,
    private val redirectUri: String
) {
    companion object {
        const val AUTH_REQUEST_CODE = 0x20
        const val SCOPES = "playlist-read-private playlist-read-collaborative"
    }

    private var accessToken: String? = null

    fun authorize(activity: Activity) {
        val request = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.TOKEN,
            redirectUri
        )
            .setScopes(arrayOf(SCOPES))
            .build()
        AuthorizationClient.openLoginActivity(activity, AUTH_REQUEST_CODE, request)
    }

    fun handleResponse(resultCode: Int, data: Intent?): Boolean {
        val response = AuthorizationClient.getResponse(resultCode, data)
        return when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                accessToken = response.accessToken
                true
            }
            else -> false
        }
    }

    fun getAccessToken(): String? = accessToken

    fun isAuthorized(): Boolean = accessToken != null

    fun clearToken() {
        accessToken = null
    }
}
