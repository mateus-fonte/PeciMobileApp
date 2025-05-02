package com.example.pecimobileapp.ui

import androidx.compose.runtime.*
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.viewmodels.ProfilePreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class ProfileViewModel(private val context: Context) : ViewModel() {

    var nome by mutableStateOf("Maria")
    var identificador by mutableStateOf("Sabinada") // antes: apelido
    var anoNascimento by mutableStateOf(1992)
    var fcMaxManual by mutableStateOf<Int?>(null)

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            ProfilePreferences.nomeFlow(context).collectLatest { nome = it }
        }
        viewModelScope.launch {
            ProfilePreferences.apelidoFlow(context).collectLatest { identificador = it }
        }
        viewModelScope.launch {
            ProfilePreferences.anoNascimentoFlow(context).collectLatest { anoNascimento = it }
        }
        viewModelScope.launch {
            ProfilePreferences.fcMaxManualFlow(context).collectLatest { fcMaxManual = it }
        }
    }

    // Funções para alterar e salvar

    fun updateNome(novoNome: String) {
        nome = novoNome
        viewModelScope.launch { ProfilePreferences.saveNome(context, novoNome) }
    }

    fun updateApelido(novoIdentificador: String) {
        identificador = novoIdentificador
        viewModelScope.launch { ProfilePreferences.saveApelido(context, novoIdentificador) }
    }

    fun updateAnoNascimento(novoAno: Int) {
        anoNascimento = novoAno
        viewModelScope.launch { ProfilePreferences.saveAnoNascimento(context, novoAno) }
    }

    fun updateFcMaxManual(novoFcMax: Int?) {
        fcMaxManual = novoFcMax
        viewModelScope.launch { ProfilePreferences.saveFcMaxManual(context, novoFcMax) }
    }

    // Funções calculadas (ficam iguais)

    val idade: Int
        get() {
            val anoAtual = Calendar.getInstance().get(Calendar.YEAR)
            return anoAtual - anoNascimento
        }

    val fcMaxCalculada: Int
        get() = (208 - 0.7 * idade).toInt()

    val fcMax: Int
        get() = fcMaxManual ?: fcMaxCalculada

    val zonas: List<Pair<String, IntRange>>
        get() = listOf(
            "Zona 1" to (fcMax * 0.50).toInt()..(fcMax * 0.60).toInt(),
            "Zona 2" to (fcMax * 0.60).toInt()..(fcMax * 0.70).toInt(),
            "Zona 3" to (fcMax * 0.70).toInt()..(fcMax * 0.80).toInt(),
            "Zona 4" to (fcMax * 0.80).toInt()..(fcMax * 0.90).toInt(),
            "Zona 5" to (fcMax * 0.90).toInt()..fcMax
        )
}