package com.example.progettopm2026.jsonConverter.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progettopm2026.jsonConverter.data.Menu
import com.example.progettopm2026.jsonConverter.data.MenuSource
import com.example.progettopm2026.jsonConverter.model.MenuConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class MenuUiState {
    data object Idle : MenuUiState()
    data object Loading : MenuUiState()
    data class Success(val menu: Menu) : MenuUiState()
    data class Error(val message: String) : MenuUiState()
}

class MenuViewModel(private val converter: MenuConverter) : ViewModel() {

    private val _uiState = MutableStateFlow<MenuUiState>(MenuUiState.Idle)
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()

    fun loadMenu(source: MenuSource) {
        viewModelScope.launch {
            _uiState.value = MenuUiState.Loading
            converter.convert(source)
                .onSuccess { menu ->
                    _uiState.value = MenuUiState.Success(menu)
                }
                .onFailure { throwable ->
                    _uiState.value = MenuUiState.Error(
                        throwable.message ?: "Unknown error during conversion"
                    )
                }
        }
    }

    /** Resets state, e.g. when the user dismisses an error or starts over. */
    fun reset() {
        _uiState.value = MenuUiState.Idle
    }
}
