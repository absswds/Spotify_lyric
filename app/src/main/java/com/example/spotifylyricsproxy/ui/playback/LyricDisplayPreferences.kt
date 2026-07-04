package com.example.spotifylyricsproxy.ui.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Persists the user's lyric display preferences so they survive process death.
 *
 * Call [init] once from MainActivity.onCreate, then read any [State] field
 * in a Composable to react to changes.
 */
object LyricDisplayPreferences {

    private const val PREFS_NAME = "lyric_display"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_BOLD = "bold_current"
    private const val KEY_DIM = "dim_level"
    private const val KEY_ALIGN = "alignment"

    private const val DEFAULT_FONT_SIZE = "default"
    private const val DEFAULT_BOLD = true
    private const val DEFAULT_DIM = "medium"
    private const val DEFAULT_ALIGN = "center"

    private var prefs: SharedPreferences? = null

    private val _fontSize = mutableStateOf(DEFAULT_FONT_SIZE)
    private val _boldCurrentLine = mutableStateOf(DEFAULT_BOLD)
    private val _dimLevel = mutableStateOf(DEFAULT_DIM)
    private val _alignment = mutableStateOf(DEFAULT_ALIGN)

    val fontSize: State<String> = _fontSize
    val boldCurrentLine: State<Boolean> = _boldCurrentLine
    val dimLevel: State<String> = _dimLevel
    val alignment: State<String> = _alignment

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _fontSize.value = prefs?.getString(KEY_FONT_SIZE, DEFAULT_FONT_SIZE) ?: DEFAULT_FONT_SIZE
        _boldCurrentLine.value = prefs?.getBoolean(KEY_BOLD, DEFAULT_BOLD) ?: DEFAULT_BOLD
        _dimLevel.value = prefs?.getString(KEY_DIM, DEFAULT_DIM) ?: DEFAULT_DIM
        _alignment.value = prefs?.getString(KEY_ALIGN, DEFAULT_ALIGN) ?: DEFAULT_ALIGN
    }

    fun setFontSize(value: String) {
        if (value !in listOf("small", "default", "large", "xlarge")) return
        prefs?.edit()?.putString(KEY_FONT_SIZE, value)?.apply()
        _fontSize.value = value
    }

    fun setBoldCurrentLine(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_BOLD, value)?.apply()
        _boldCurrentLine.value = value
    }

    fun setDimLevel(value: String) {
        if (value !in listOf("low", "medium", "high")) return
        prefs?.edit()?.putString(KEY_DIM, value)?.apply()
        _dimLevel.value = value
    }

    fun setAlignment(value: String) {
        if (value !in listOf("center", "start")) return
        prefs?.edit()?.putString(KEY_ALIGN, value)?.apply()
        _alignment.value = value
    }

    /**
     * Resolve the raw preferences into concrete display values for list-based
     * lyric views (expanded full-screen and landscape panels).
     */
    @Composable
    fun resolvedConfig(): LyricDisplayConfig {
        val fs = _fontSize.value
        val bold = _boldCurrentLine.value
        val dim = _dimLevel.value
        val align = _alignment.value

        val currentSp = when (fs) {
            "small" -> 18.sp
            "large" -> 24.sp
            "xlarge" -> 28.sp
            else -> 20.sp
        }
        val otherSp = when (fs) {
            "small" -> 13.sp
            "large" -> 17.sp
            "xlarge" -> 20.sp
            else -> 15.sp
        }
        val (pastAlpha, futureAlpha) = when (dim) {
            "low" -> 0.60f to 0.45f
            "high" -> 0.35f to 0.18f
            else -> 0.48f to 0.30f
        }
        return LyricDisplayConfig(
            currentLineSp = currentSp,
            otherLineSp = otherSp,
            currentLineWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            pastLineAlpha = pastAlpha,
            futureLineAlpha = futureAlpha,
            textAlign = if (align == "start") TextAlign.Start else TextAlign.Center
        )
    }
}

data class LyricDisplayConfig(
    val currentLineSp: TextUnit,
    val otherLineSp: TextUnit,
    val currentLineWeight: FontWeight,
    val pastLineAlpha: Float,
    val futureLineAlpha: Float,
    val textAlign: TextAlign
)
