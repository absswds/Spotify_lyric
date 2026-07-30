package com.example.spotifylyricsproxy.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Whether the current default network is metered (mobile data / hotspot). */
enum class MeteredState {
    /** Connection is unmetered (typical WiFi, ethernet). Online sources are okay. */
    UNMETERED,
    /** Connection is metered (mobile data, WiFi hotspot). Conserve data. */
    METERED,
    /** No active network at all. */
    NONE
}

object ConnectivityObserver {

    /**
     * Emits [MeteredState] whenever the default network's metered status changes.
     *
     * Uses [ConnectivityManager.registerDefaultNetworkCallback] (the official
     * recommended approach) and checks [NetworkCapabilities.NET_CAPABILITY_NOT_METERED].
     */
    fun observe(context: Context): Flow<MeteredState> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun currentState(): MeteredState {
            val activeNetwork = cm.activeNetwork ?: return MeteredState.NONE
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return MeteredState.NONE
            return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                MeteredState.UNMETERED
            } else {
                // metered — mobile data, hotspot, or any capped connection
                MeteredState.METERED
            }
        }

        // Emit the initial state immediately
        trySend(currentState())

        val callback = object : NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(currentState()) }
            override fun onLost(network: Network) { trySend(currentState()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(currentState())
            }
        }

        cm.registerDefaultNetworkCallback(callback)

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
