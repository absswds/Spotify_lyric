package com.example.spotifylyricsproxy.ui.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object LyricDisplayPreferences {

    private const val PREFS_NAME = "lyric_display"
    private const val KEY_BOLD = "bold_current"
    private const val KEY_DIM = "dim_level"
    private const val KEY_ALIGN = "alignment"
    private const val KEY_BLUR = "blur_enabled"
    private const val KEY_FONT_SIZE_CURRENT = "font_size_current"
    private const val KEY_FONT_SIZE_OTHER = "font_size_other"
    private const val KEY_MOBILE_STRATEGY = "mobile_data_strategy"
    private const val KEY_CHINESE_FORM = "chinese_form"

    private var prefs: SharedPreferences? = null

    private val _boldCurrentLine = mutableStateOf(true)
    private val _dimLevel = mutableStateOf("medium")
    private val _alignment = mutableStateOf("center")
    private val _blurEnabled = mutableStateOf(true)
    // Font sizes: stored as Float (sp), with min 12sp, max 36sp
    private val _fontSizeCurrent = mutableFloatStateOf(20f)
    private val _fontSizeOther = mutableFloatStateOf(15f)
    private val _mobileDataStrategy = mutableStateOf("ask")  // "ask" | "allow" | "deny"
    private val _chineseForm = mutableStateOf("original")  // "original" | "simplified" | "traditional"

    val boldCurrentLine: State<Boolean> = _boldCurrentLine
    val dimLevel: State<String> = _dimLevel
    val alignment: State<String> = _alignment
    val blurEnabled: State<Boolean> = _blurEnabled
    val fontSizeCurrent: State<Float> = _fontSizeCurrent
    val fontSizeOther: State<Float> = _fontSizeOther
    val mobileDataStrategy: State<String> = _mobileDataStrategy
    val chineseForm: State<String> = _chineseForm


    /** @deprecated Use [fontSizeCurrent] slider instead. Kept for dialog compat. */
    @Deprecated("Use fontSizeCurrent / fontSizeOther")
    val fontSize: State<String> = object : State<String> {
        override val value: String get() {
            val cur = _fontSizeCurrent.value
            return when {
                cur <= 14f -> "small"
                cur <= 18f -> "default"
                cur <= 24f -> "large"
                else -> "xlarge"
            }
        }
    }

    /** @deprecated Use [setFontSizeCurrent] / [setFontSizeOther] instead. */
    @Deprecated("Use setFontSizeCurrent / setFontSizeOther")
    fun setFontSize(value: String) {
        val cur = when (value) {
            "small" -> 16f
            "large" -> 24f
            "xlarge" -> 28f
            else -> 20f
        }
        val oth = when (value) {
            "small" -> 12f
            "large" -> 17f
            "xlarge" -> 20f
            else -> 15f
        }
        setFontSizeCurrent(cur)
        setFontSizeOther(oth)
    }

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _boldCurrentLine.value = prefs?.getBoolean(KEY_BOLD, true) ?: true
        _dimLevel.value = prefs?.getString(KEY_DIM, "medium") ?: "medium"
        _alignment.value = prefs?.getString(KEY_ALIGN, "center") ?: "center"
        _blurEnabled.value = prefs?.getBoolean(KEY_BLUR, true) ?: true
        _fontSizeCurrent.value = prefs?.getFloat(KEY_FONT_SIZE_CURRENT, 20f) ?: 20f
        _fontSizeOther.value = prefs?.getFloat(KEY_FONT_SIZE_OTHER, 15f) ?: 15f
        _mobileDataStrategy.value = prefs?.getString(KEY_MOBILE_STRATEGY, "ask") ?: "ask"
        _chineseForm.value = prefs?.getString(KEY_CHINESE_FORM, "original") ?: "original"
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

    fun setBlurEnabled(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_BLUR, value)?.apply()
        _blurEnabled.value = value
    }

    /** Set current-line font size in sp, clamped to [12f, 36f]. */
    fun setFontSizeCurrent(valueSp: Float) {
        val clamped = valueSp.coerceIn(12f, 36f)
        prefs?.edit()?.putFloat(KEY_FONT_SIZE_CURRENT, clamped)?.apply()
        _fontSizeCurrent.value = clamped
    }

    /** Set other-line font size in sp, clamped to [12f, 36f]. */
    fun setFontSizeOther(valueSp: Float) {
        val clamped = valueSp.coerceIn(12f, 36f)
        prefs?.edit()?.putFloat(KEY_FONT_SIZE_OTHER, clamped)?.apply()
        _fontSizeOther.value = clamped
    }

    /** Mobile data choice that persists for the current day: "allow" or "deny". */
    private const val KEY_MOBILE_TODAY_CHOICE = "mobile_today_choice"
    private const val KEY_MOBILE_TODAY_DATE = "mobile_today_date"
    private val _mobileTodayChoice = mutableStateOf<String?>(null)
    val mobileTodayChoice: State<String?> = _mobileTodayChoice

    /** Get today's stored mobile data decision, or null if not set today. */
    fun getTodayMobileDataChoice(): String? {
        val date = prefs?.getString(KEY_MOBILE_TODAY_DATE, null) ?: return null
        if (date != java.time.LocalDate.now().toString()) {
            prefs?.edit()?.remove(KEY_MOBILE_TODAY_CHOICE)?.remove(KEY_MOBILE_TODAY_DATE)?.apply()
            return null
        }
        val choice = prefs?.getString(KEY_MOBILE_TODAY_CHOICE, null)
        return if (choice in listOf("allow", "deny")) choice else null
    }

    /** Store today's mobile data decision. */
    fun setTodayMobileDataChoice(value: String) {
        if (value !in listOf("allow", "deny")) return
        prefs?.edit()
            ?.putString(KEY_MOBILE_TODAY_CHOICE, value)
            ?.putString(KEY_MOBILE_TODAY_DATE, java.time.LocalDate.now().toString())
            ?.apply()
        _mobileTodayChoice.value = value
    }

    @Composable
    fun todayMobileDataChoice(): String? = _mobileTodayChoice.value

    /** Strategy for mobile data: "ask" (default) | "allow" | "deny" */
    fun setMobileDataStrategy(value: String) {
        if (value !in listOf("ask", "allow", "deny")) return
        prefs?.edit()?.putString(KEY_MOBILE_STRATEGY, value)?.apply()
        _mobileDataStrategy.value = value
    }

    /** Set Chinese form: "original", "simplified" or "traditional". */
    fun setChineseForm(value: String) {
        if (value !in listOf("original", "simplified", "traditional")) return
        prefs?.edit()?.putString(KEY_CHINESE_FORM, value)?.apply()
        _chineseForm.value = value
    }

    @Composable
    fun resolvedConfig(): LyricDisplayConfig {
        val bold = _boldCurrentLine.value
        val dim = _dimLevel.value
        val align = _alignment.value
        val (pastAlpha, futureAlpha) = when (dim) {
            "low" -> 0.75f to 0.60f
            "high" -> 0.20f to 0.08f
            else -> 0.48f to 0.30f
        }
        return LyricDisplayConfig(
            currentLineSp = _fontSizeCurrent.value.sp,
            otherLineSp = _fontSizeOther.value.sp,
            currentLineWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium,
            pastLineAlpha = pastAlpha,
            futureLineAlpha = futureAlpha,
            textAlign = if (align == "start") TextAlign.Start else TextAlign.Center,
            blurEnabled = _blurEnabled.value,
            chineseForm = _chineseForm.value
        )
    }

    @Composable
    fun resolvedLandscapeConfig(): LyricDisplayConfig {
        val base = resolvedConfig()
        val curSp = (_fontSizeCurrent.value - 2f).coerceAtLeast(12f)
        val othSp = (_fontSizeOther.value - 1f).coerceAtLeast(12f)
        return base.copy(
            currentLineSp = curSp.sp,
            otherLineSp = othSp.sp
        )
    }
}


@Deprecated("Use LyricDisplayConfig with slider-based sizes")
data class CompactLyricPreviewConfig(
    val currentLineSp: TextUnit,
    val contextLineSp: TextUnit,
    val textAlign: TextAlign
)

@Composable
@Deprecated("Use resolvedConfig() which now uses slider sizes")
fun resolvedCompactPreviewConfig(): CompactLyricPreviewConfig {
    val align = LyricDisplayPreferences.alignment.value
    return CompactLyricPreviewConfig(
        currentLineSp = 18.sp,
        contextLineSp = 14.sp,
        textAlign = if (align == "start") TextAlign.Start else TextAlign.Center
    )
}

data class LyricDisplayConfig(
    val currentLineSp: TextUnit,
    val otherLineSp: TextUnit,
    val currentLineWeight: FontWeight,
    val pastLineAlpha: Float,
    val futureLineAlpha: Float,
    val textAlign: TextAlign,
    val blurEnabled: Boolean = true,
    val chineseForm: String = "original"
)
