package com.example.progettopm2026.llmSearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progettopm2026.llmSearch.search.MenuSearchService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchService: MenuSearchService
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun search(query: String) {
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading

            runCatching {
                searchService.search(query)
            }.onSuccess { results ->
                _uiState.value = if (results.isEmpty()) {
                    SearchUiState.Empty(query.trim())
                } else {
                    SearchUiState.Success(query.trim(), results)
                }
            }.onFailure { throwable ->
                _uiState.value = SearchUiState.Error(throwable.message ?: "Search failed")
            }
        }
    }

    fun reset() {
        _uiState.value = SearchUiState.Idle
    }
}
