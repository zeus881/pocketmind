package com.sanjay.pocketmind.feature.translate

import com.sanjay.pocketmind.data.documents.Language

/** Everything the Translate screen can ask for; [TranslateViewModel] implements it (tests pass a no-op). */
interface TranslateActions {
    fun onInputChange(text: String)
    fun setFrom(language: Language)
    fun setTo(language: Language)
    fun swap()
    fun translate()
    fun stop()
}
