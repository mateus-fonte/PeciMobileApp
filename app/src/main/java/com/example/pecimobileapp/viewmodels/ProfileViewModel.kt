package com.example.pecimobileapp.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.viewmodels.ProfilePreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class ProfileViewModel(private val context: Context) : ViewModel() {

    // Estado observável
    var nome by mutableStateOf("Maria")
    var identificador by mutableStateOf("Sabinada")
    var anoNascimento by mutableStateOf(1992)
    var fcMaxManual by mutableStateOf<Int?>(null)

    init {
        loadProfile()
    }

    // 🧠 Função auxiliar para tratar exceções nos Flow
    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                e.printStackTrace() // Pode trocar por Timber.e() ou log
            }
        }
    }

    private fun loadProfile() {
        launchSafely {
            ProfilePreferences.nomeFlow(context).collectLatest { nome = it }
        }
        launchSafely {
            ProfilePreferences.identificadorFlow(context).collectLatest { identificador = it }
        }
        launchSafely {
            ProfilePreferences.anoNascimentoFlow(context).collectLatest { anoNascimento = it }
        }
        launchSafely {
            ProfilePreferences.fcMaxManualFlow(context).collectLatest { fcMaxManual = it }
        }
    }

    // Funções para alterar e salvar
    fun updateNome(novoNome: String) {
        nome = novoNome
        viewModelScope.launch {
            ProfilePreferences.saveNome(context, novoNome)
        }
    }

    fun updateIdentificador(novoIdentificador: String) {
        identificador = novoIdentificador
        viewModelScope.launch {
            ProfilePreferences.saveIdentificador(context, novoIdentificador)
        }
    }

    fun updateAnoNascimento(novoAno: Int) {
        anoNascimento = novoAno
        viewModelScope.launch {
            ProfilePreferences.saveAnoNascimento(context, novoAno)
        }
    }

    fun updateFcMaxManual(novoFcMax: Int?) {
        fcMaxManual = novoFcMax
        viewModelScope.launch {
            ProfilePreferences.saveFcMaxManual(context, novoFcMax)
        }
    }

    // Propriedades calculadas
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