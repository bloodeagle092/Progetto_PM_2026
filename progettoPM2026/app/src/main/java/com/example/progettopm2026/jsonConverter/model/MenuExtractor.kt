package com.example.progettopm2026.jsonConverter.model

import android.content.Context
import android.net.Uri
import com.example.progettopm2026.jsonConverter.data.MenuSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface TextExtractor {
    suspend fun extract(source: MenuSource): String
}
class MenuExtractor(private val context: Context) : TextExtractor {

    override suspend fun extract(source: MenuSource): String =
        when (source) {
            is MenuSource.RawText   -> source.text
            is MenuSource.TextFile  -> extractFromTextFile(source.uri)
            is MenuSource.ImageFile -> extractFromImage(source.uri)
            is MenuSource.PdfFile   -> extractFromPdf(source.uri)
            is MenuSource.WebUrl    -> extractFromUrl(source.url)
        }
    private suspend fun extractFromTextFile(uri: Uri): String =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: throw IOException("Cannot open file: $uri")
        }

    private suspend fun extractFromImage(uri: Uri): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result.text) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
            cont.invokeOnCancellation { recognizer.close() }
        }

    private suspend fun extractFromPdf(uri: Uri): String =
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open PDF: $uri")
            val document = PDDocument.load(inputStream)
            try {
                val text = PDFTextStripper().getText(document)
                if (text.trim().length < 20) {
                    renderAndOcrPdf(document)
                } else {
                    text
                }
            } finally {
                document.close()
            }
        }
    private suspend fun renderAndOcrPdf(doc: PDDocument): String {
        throw UnsupportedOperationException(
            "Scanned PDF support not yet implemented. Use a text-based PDF for now."
        )
    }
    private suspend fun extractFromUrl(url: String): String =
        withContext(Dispatchers.IO) {
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10_000)
                .get()
                .text()
        }
}
