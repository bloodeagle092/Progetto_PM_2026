package com.example.progettopm2026.jsonConverter.model

class MenuTextCleaner {
    fun clean(raw: String): String {
        return raw
            .replace("\u0000", " ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { line ->
                val lower = line.lowercase()
                lower.contains("cookie") ||
                        lower.contains("privacy policy") ||
                        lower.contains("terms of service") ||
                        lower.contains("accept all") ||
                        lower.contains("follow us on") ||
                        lower.contains("facebook") ||
                        lower.contains("instagram")
            }
            .joinToString("\n")
            .trim()
    }
}