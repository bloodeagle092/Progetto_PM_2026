package com.example.progettopm2026.jsonConverter.model

import com.example.progettopm2026.jsonConverter.data.Menu
import com.example.progettopm2026.jsonConverter.data.MenuSource

class MenuConverter(
    private val extractor: MenuExtractor,
    private val textCleaner: MenuTextCleaner,
    private val llmClient: MenuLlmClient,
    private val postProcessor: MenuPostProcessor
) {
    suspend fun convert(source: MenuSource): Result<Menu> = runCatching {
        val menu = when (source) {
            is MenuSource.ImageFile -> {
                val binary = extractor.loadBinarySource(source)
                llmClient.extractMenuFromImage(
                    bytes = binary.bytes,
                    mimeType = binary.mimeType,
                    fileName = binary.fileName
                )
            }

            is MenuSource.PdfFile -> {
                val binary = extractor.loadBinarySource(source)
                llmClient.extractMenuFromPdf(
                    bytes = binary.bytes,
                    fileName = binary.fileName
                )
            }

            is MenuSource.TextFile,
            is MenuSource.RawText,
            is MenuSource.WebUrl -> {
                val rawText = extractor.extract(source)
                require(rawText.isNotBlank()) { "No text could be extracted from source" }

                val cleanedText = textCleaner.clean(rawText)
                require(cleanedText.isNotBlank()) {
                    "Extracted text became empty after cleaning"
                }

                llmClient.extractMenuFromText(cleanedText)
            }
        }

        postProcessor.normalize(menu)
    }
}