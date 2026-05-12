package com.example.progettopm2026.llmSearch

import com.example.progettopm2026.llmSearch.search.SearchResult

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data class Success(val query: String, val results: List<SearchResult>) : SearchUiState()
    data class Empty(val query: String) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
