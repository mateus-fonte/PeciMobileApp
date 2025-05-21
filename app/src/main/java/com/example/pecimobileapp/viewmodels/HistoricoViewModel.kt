package com.example.pecimobileapp.viewmodels

import androidx.lifecycle.ViewModel
import com.example.pecimobileapp.models.HistoryRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HistoricoViewModel : ViewModel() {
    // Mantém a lista de registros históricos
    private val _historicoList = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val historicoList: StateFlow<List<HistoryRecord>> get() = _historicoList

    // Função para adicionar um registro histórico
    fun addRecord(record: HistoryRecord) {
        _historicoList.value = _historicoList.value + record
    }

    // Exemplo opcional: função para limpar os registros
    fun clearHistory() {
        _historicoList.value = emptyList()
    }
}