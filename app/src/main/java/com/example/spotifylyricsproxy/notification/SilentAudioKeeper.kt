package com.example.spotifylyricsproxy.notification

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Plays silence while Spotify plays on another Connect device. The phone then outputs no
 * audio, and ColorOS "Hans" freezes the app within seconds, stalling the media card lyrics;
 * an app with an active audio stream is not frozen. No audio focus is requested, so other
 * apps are not interrupted.
 */
class SilentAudioKeeper {
    private var track: AudioTrack? = null

    fun start() {
        if (track != null) return
        val rate = 8_000
        val samples = rate // one second of 16-bit mono silence, looped
        track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples * 2)
                .build()
                .apply {
                    write(ShortArray(samples), 0, samples)
                    setLoopPoints(0, samples, -1)
                    play()
                }
        }.onFailure { Log.w(TAG, "Silent track failed", it) }.getOrNull()
        Log.i(TAG, "Silent keep-alive started: ${track != null}")
    }

    fun stop() {
        val t = track ?: return
        track = null
        runCatching { t.stop() }
        t.release()
        Log.i(TAG, "Silent keep-alive stopped")
    }

    private companion object {
        const val TAG = "SilentAudioKeeper"
    }
}
