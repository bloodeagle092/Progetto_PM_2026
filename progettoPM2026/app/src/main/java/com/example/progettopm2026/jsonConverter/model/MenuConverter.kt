package com.example.progettopm2026.jsonConverter.model

import com.example.progettopm2026.jsonConverter.data.DataClassses

class MenuConverter(
    private val extractor: TextExtractor,
    private val llmClient: MenuLlmClient
) {
    suspend fun convert(source: DataClassses.MenuSource): Result<DataClassses.Menu> = runCatching {
        val text = extractor.extract(source)
        require(text.isNotBlank()) { "No text could be extracted from source" }
        llmClient.extractMenu(text)
    }
}