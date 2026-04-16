package com.example.progettopm2026.jsonConverter.viewModel

import androidx.lifecycle.ViewModel
import com.example.progettopm2026.jsonConverter.data.DataClassses
import com.example.progettopm2026.jsonConverter.model.MenuConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MenuViewModel(private val converter: MenuConverter) : ViewModel() {
    private val _menu = MutableStateFlow<DataClassses.Menu?>(null)
    val menu: StateFlow<DataClassses.Menu?> = _menu.asStateFlow()
    fun loadMenu(source: DataClassses.MenuSource) {
        viewModelScope.launch {
            converter.convert(source)
                .onSuccess { _menu.value = it }
                .onFailure { /* emit error state */ }
        }
    }
}