package com.example.progettopm2026.jsonConverter.model

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.progettopm2026.jsonConverter.data.MenuSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.Locale

interface TextExtractor {
    suspend fun extract(source: MenuSource): String
}

data class BinaryMenuInput(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String
)

class MenuExtractor(private val context: Context) : TextExtractor {

    override suspend fun extract(source: MenuSource): String =
        when (source) {
            is MenuSource.RawText -> source.text
            is MenuSource.TextFile -> extractFromTextFile(source.uri)
            is MenuSource.WebUrl -> extractFromUrl(source.url)

            is MenuSource.ImageFile,
            is MenuSource.PdfFile -> {
                throw UnsupportedOperationException(
                    "ImageFile and PdfFile now use direct multimodal input. Use loadBinarySource()."
                )
            }
        }

    suspend fun loadBinarySource(source: MenuSource): BinaryMenuInput =
        when (source) {
            is MenuSource.ImageFile -> loadBinaryFromUri(
                uri = source.uri,
                fallbackMimeType = "image/jpeg",
                fallbackExtension = "jpg"
            )

            is MenuSource.PdfFile -> loadBinaryFromUri(
                uri = source.uri,
                fallbackMimeType = "application/pdf",
                fallbackExtension = "pdf"
            )

            else -> throw IllegalArgumentException(
                "Binary loading is only supported for ImageFile and PdfFile"
            )
        }

    private suspend fun extractFromTextFile(uri: Uri): String =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: throw IOException("Cannot open file: $uri")
        }

    private suspend fun loadBinaryFromUri(
        uri: Uri,
        fallbackMimeType: String,
        fallbackExtension: String
    ): BinaryMenuInput = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot open binary file: $uri")

        val mimeType = resolveMimeType(uri) ?: fallbackMimeType
        val fileName = resolveFileName(uri, mimeType, fallbackExtension)

        BinaryMenuInput(
            bytes = bytes,
            mimeType = mimeType,
            fileName = fileName
        )
    }

    private fun resolveMimeType(uri: Uri): String? {
        val contentType = context.contentResolver.getType(uri)
        if (!contentType.isNullOrBlank()) return contentType

        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }

        return if (extension != null) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        } else {
            null
        }
    }

    private fun resolveFileName(
        uri: Uri,
        mimeType: String,
        fallbackExtension: String
    ): String {
        val raw = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBefore('?')
            ?.trim()

        val hasExtension = raw?.contains('.') == true
        if (!raw.isNullOrBlank() && hasExtension) return raw

        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.ifBlank { null }
            ?: fallbackExtension

        val baseName = raw
            ?.replace(Regex("""[^\w\-]+"""), "_")
            ?.takeIf { it.isNotBlank() }
            ?: "menu_input"

        return "$baseName.$extension"
    }

    private suspend fun extractFromUrl(url: String): String =
        withContext(Dispatchers.IO) {
            val document = Jsoup.connect(url)
                .userAgent(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                .timeout(30_000)
                .maxBodySize(0)
                .followRedirects(true)
                .get()

            val restaurantName = extractRestaurantName(document)
            cleanDocument(document)

            val candidates = collectMenuCandidates(document)
            val bestText = pickBestCandidateText(candidates)
            val fallbackText = cleanExtractedText(document.body()?.text().orEmpty())

            val coreText = when {
                bestText.isNotBlank() && bestText.length >= 80 -> bestText
                fallbackText.isNotBlank() -> fallbackText
                else -> throw IOException("No usable text found at URL: $url")
            }

            buildStructuredUrlText(
                restaurantName = restaurantName,
                sourceUrl = url,
                menuText = coreText
            ).take(20_000)
        }

    private fun buildStructuredUrlText(
        restaurantName: String?,
        sourceUrl: String,
        menuText: String
    ): String {
        val sb = StringBuilder()

        if (!restaurantName.isNullOrBlank()) {
            sb.append("RESTAURANT NAME\n")
            sb.append(restaurantName.trim())
            sb.append("\n\n")
        }

        sb.append("SOURCE URL\n")
        sb.append(sourceUrl.trim())
        sb.append("\n\n")

        sb.append("MENU CONTENT\n")
        sb.append(menuText.trim())

        return sb.toString().trim()
    }

    private fun extractRestaurantName(document: Document): String? {
        val candidates = listOfNotNull(
            document.selectFirst("meta[property=og:site_name]")?.attr("content"),
            document.selectFirst("meta[property=og:title]")?.attr("content"),
            document.selectFirst("meta[name=application-name]")?.attr("content"),
            document.title(),
            document.selectFirst("h1")?.text()
        )

        for (raw in candidates) {
            val cleaned = normalizeRestaurantNameCandidate(raw)
            if (cleaned != null) return cleaned
        }

        return null
    }

    private fun normalizeRestaurantNameCandidate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        var value = raw.trim()

        val parts = value.split("|", " - ", " – ", " — ")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        value = parts.maxByOrNull { scoreRestaurantNameCandidate(it) } ?: value

        value = value
            .replace(Regex("""(?i)\b(menu|dinner|lunch|pranzo|cena)\b"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        if (value.isBlank()) return null

        val lower = value.lowercase(Locale.ROOT)
        if (lower.length < 3) return null
        if (lower == "home") return null
        if (lower == "menu") return null
        if (lower.contains("cookie")) return null
        if (lower.contains("privacy")) return null
        if (lower.contains("reservation")) return null
        if (lower.contains("book now")) return null
        if (lower.contains("contact")) return null

        return value
    }

    private fun scoreRestaurantNameCandidate(text: String): Int {
        val lower = text.lowercase(Locale.ROOT)
        var score = 0

        if (text.any { it.isLetter() }) score += 5
        if (text.length in 4..40) score += 5
        if (Regex("""['’]""").containsMatchIn(text)) score += 2
        if (lower.contains("trattoria")) score += 4
        if (lower.contains("osteria")) score += 4
        if (lower.contains("ristorante")) score += 2

        if (lower.contains("menu")) score -= 4
        if (lower.contains("dinner")) score -= 3
        if (lower.contains("lunch")) score -= 3
        if (lower.contains("cookie")) score -= 10
        if (lower.contains("privacy")) score -= 10
        if (lower.contains("reservation")) score -= 8

        return score
    }

    private fun cleanDocument(document: Document) {
        document.select(
            """
            script, style, noscript, svg, canvas, iframe,
            footer, nav, aside, form,
            .cookie, .cookies, .cookie-banner, .cookie-consent,
            .gdpr, .privacy, .newsletter, .social, .share,
            .modal, .popup, .banner, .ads, .advertisement
            """.trimIndent()
        ).remove()
    }

    private fun collectMenuCandidates(document: Document): List<Element> {
        val selectors = listOf(
            "[class*=menu]",
            "[id*=menu]",
            "[class*=food]",
            "[id*=food]",
            "[class*=dish]",
            "[id*=dish]",
            "[class*=carta]",
            "[id*=carta]",
            "[class*=listino]",
            "[class*=dinner]",
            "[id*=dinner]",
            "main",
            "article",
            "section"
        )

        val results = linkedSetOf<Element>()
        for (selector in selectors) {
            results.addAll(document.select(selector))
        }

        return results.toList()
    }

    private fun pickBestCandidateText(candidates: List<Element>): String {
        if (candidates.isEmpty()) return ""

        return candidates
            .map { element ->
                val text = cleanExtractedText(element.text())
                val score = scoreCandidate(text)
                text to score
            }
            .filter { (text, score) -> text.isNotBlank() && score > 0 }
            .sortedByDescending { (_, score) -> score }
            .firstOrNull()
            ?.first
            .orEmpty()
    }

    private fun scoreCandidate(text: String): Int {
        val lower = text.lowercase(Locale.ROOT)
        var score = 0

        val menuWords = listOf(
            "menu", "antipasti", "primi", "secondi", "dolci",
            "dessert", "bevande", "cocktail", "vino", "birra",
            "ingredienti", "allergeni", "salse", "pite", "zuppe"
        )

        val junkWords = listOf(
            "cookie", "privacy", "login", "newsletter",
            "facebook", "instagram", "tiktok", "terms",
            "reservation", "book now", "contact us"
        )

        menuWords.forEach { if (lower.contains(it)) score += 8 }
        junkWords.forEach { if (lower.contains(it)) score -= 10 }

        val priceMatches = Regex("""(?:€|\$|£)\s?\d+([.,]\d{1,2})?|\d+([.,]\d{1,2})?\s?(€|\$|£)""")
            .findAll(text)
            .count()

        score += priceMatches * 5
        score += minOf(text.length / 80, 30)

        return score
    }

    private fun cleanExtractedText(raw: String): String {
        val blockedPatterns = listOf(
            "cookie",
            "privacy policy",
            "terms of service",
            "accept all",
            "reject all",
            "newsletter",
            "follow us",
            "facebook",
            "instagram",
            "tiktok",
            "book now",
            "reserve now",
            "contact us",
            "home page",
            "open hours",
            "opening hours"
        )

        return raw
            .replace('\u0000', ' ')
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { line ->
                val lower = line.lowercase(Locale.ROOT)
                blockedPatterns.any { blocked -> lower.contains(blocked) }
            }
            .filterNot { line -> line.length <= 2 }
            .filterNot { line -> isMostlyJunk(line) }
            .distinct()
            .joinToString("\n")
            .trim()
    }

    private fun isMostlyJunk(line: String): Boolean {
        val letters = line.count { it.isLetter() }
        val digits = line.count { it.isDigit() }
        val symbols = line.count { !it.isLetterOrDigit() && !it.isWhitespace() }

        if (line.length >= 4 && letters == 0 && digits == 0) return true
        if (symbols > letters + digits) return true

        return false
    }
}