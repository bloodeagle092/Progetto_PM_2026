package com.example.progettopm2026.jsonConverter.model

import android.content.Context
import android.net.Uri
import com.example.progettopm2026.jsonConverter.data.DataClassses
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

interface TextExtractor{
    suspend fun extract(source: DataClassses.MenuSource): String
}
class MenuExtractor(private val context: Context,private val textExtractor: TextExtractor):
    TextExtractor {
    override suspend fun extract(source: DataClassses.MenuSource): String =
        when(source){
            is DataClassses.MenuSource.RawText -> source.text
            is DataClassses.MenuSource.ImageFile -> textExtractor.extract(source)
            is DataClassses.MenuSource.PdfFile -> textExtractor.extract(source)
            is DataClassses.MenuSource.TextFile -> textExtractor.extract(source)
            is DataClassses.MenuSource.WebUrl -> textExtractor.extract(source)
        }
    private suspend fun extract(uri: Uri): String =
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
            val document = PDDocument.load(
                context.contentResolver.openInputStream(uri)
            )
            try {
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                if (text.trim().length < 20) {
                    renderAndOcrPdf(document)
                } else {
                    text
                }
            } finally {
                document.close()
            }
        }
    private suspend fun renderAndOcrPdf(doc: PDDocument): String = TODO()

    private suspend fun extractFromUrl(url: String): String {
        return withContext(Dispatchers.IO) {
            val html = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10_000)
                .get()
            html.text()
        }
    }

}