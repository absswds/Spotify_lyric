package com.example.spotifylyricsproxy.lyrics

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device lyrics translation using ML Kit.
 *
 * Detects the source language of lyrics text and translates it to
 * the user's target language. All processing happens on-device —
 * no network calls, no API keys, no privacy concerns.
 *
 * ML Kit translation models (~30 MB each) are downloaded on first use
 * via Google Play Services. Subsequent translations are offline.
 */
class TranslationService(private val context: Context) {

    companion object {
        private const val TAG = "TranslationService"
    }

    private val languageIdentifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
    )

    private val translators = mutableMapOf<String, Translator>()

    private val traditionalizer: android.icu.text.Transliterator by lazy {
        android.icu.text.Transliterator.getInstance("Simplified-Traditional")
    }
    private val cnLock = Any()

    /** Normalize a BCP-47 tag to the code ML Kit actually supports (zh variants -> zh). */
    fun normalizeLang(code: String): String = if (code.startsWith("zh")) "zh" else code

    /** Convert simplified Chinese text to traditional (used when target is zh-TW). */
    fun toTraditional(text: String): String = synchronized(cnLock) {
        try {
            traditionalizer.transliterate(text)
        } catch (e: Exception) {
            Log.w(TAG, "Simplified->Traditional failed", e)
            text
        }
    }

    /**
     * Detect the language code of [text] (e.g. "en", "zh", "ja", "ko").
     * Returns null when detection is inconclusive.
     */
    suspend fun detectLanguage(text: String): String? {
        if (text.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val result = languageIdentifier.identifyLanguage(text)
                TasksCompat.await(result)
            } catch (e: Exception) {
                Log.w(TAG, "Language detection failed", e)
                null
            }
        }
    }

    /**
     * Translate [text] from [sourceLang] to [targetLang].
     * Downloads the translation model if this is the first use for
     * this language pair.
     *
     * ML Kit only ships a simplified-Chinese model ("zh"); a zh-TW target is
     * handled by translating to zh first, then converting to traditional.
     */
    suspend fun translate(text: String, sourceLang: String, targetLang: String): String? {
        if (text.isBlank()) return null

        val toTraditional = targetLang == "zh-TW" || targetLang == "zh-Hant" || targetLang == "zh-HK"
        val source = TranslateLanguage.fromLanguageTag(normalizeLang(sourceLang)) ?: return null
        val target = TranslateLanguage.fromLanguageTag(if (toTraditional) "zh" else normalizeLang(targetLang)) ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val translator = getOrCreateTranslator(source, target)
                val conditions = DownloadConditions.Builder().requireWifi().build()
                TasksCompat.await(translator.downloadModelIfNeeded(conditions))
                val result = TasksCompat.await(translator.translate(text))
                if (toTraditional) toTraditional(result) else result
            } catch (e: Exception) {
                Log.w(TAG, "Translation failed for $sourceLang->$targetLang", e)
                null
            }
        }
    }

    private fun getOrCreateTranslator(source: String, target: String): Translator {
        val key = "${source}_${target}"
        return translators.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
            Translation.getClient(options)
        }
    }

    /** Release all translators. Call from ViewModel.onCleared(). */
    fun onCleared() {
        translators.values.forEach { it.close() }
        translators.clear()
    }

    /**
     * Thin wrapper around com.google.android.gms.tasks.Tasks.await
     * to avoid a direct dependency on the gms-tasks artifact for callers.
     */
    private object TasksCompat {
        suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T {
            return withContext(Dispatchers.IO) {
                com.google.android.gms.tasks.Tasks.await(task)
            }
        }
    }
}
