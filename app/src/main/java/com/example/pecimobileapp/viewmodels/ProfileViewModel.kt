package com.example.pecimobileapp.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pecimobileapp.viewmodels.ProfilePreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class ProfileViewModel(private val context: Context) : ViewModel() {

    // Estado observável
    var nome by mutableStateOf<String?>(null)
    var sobrenome by mutableStateOf<String?>(null)
    var anoNascimento by mutableStateOf<Int?>(null)
    var fcMaxManual by mutableStateOf<Int?>(null)
    var userId by mutableStateOf<String?>(null)

    init {
        loadProfile()
    }

    // Função auxiliar para tratar exceções nos Flow
    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadProfile() {
        launchSafely {
            ProfilePreferences.nomeFlow(context).collectLatest { nome = it }
        }
        launchSafely {
            ProfilePreferences.sobrenomeFlow(context).collectLatest { sobrenome = it }
        }
        launchSafely {
            ProfilePreferences.anoNascimentoFlow(context).collectLatest { anoNascimento = it }
        }
        launchSafely {
            ProfilePreferences.fcMaxManualFlow(context).collectLatest { fcMaxManual = it }
        }
        launchSafely {
            ProfilePreferences.userIdFlow(context).collectLatest { userId = it }
        }
    }

    // Funções para alterar e salvar
    fun updateNome(novoNome: String?) {
        nome = novoNome
        viewModelScope.launch {
            ProfilePreferences.saveNome(context, novoNome)
        }
    }

    fun updateSobrenome(novoSobrenome: String?) {
        sobrenome = novoSobrenome
        viewModelScope.launch {
            ProfilePreferences.saveSobrenome(context, novoSobrenome)
        }
    }

    fun updateAnoNascimento(novoAno: Int?) {
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

    fun clearProfile() {
        viewModelScope.launch {
            ProfilePreferences.clearAll(context)

            // Limpa os estados locais também
            nome = null
            sobrenome = null
            anoNascimento = null
            fcMaxManual = null
            userId = null

        }
    }


    // Função para gerar userId apenas se ainda não existir e perfil estiver preenchido
    fun generateUserIdIfNeeded() {
        if (!isProfileIncomplete && userId == null) {
            val novoId = UUID.randomUUID().toString()
            userId = novoId
            viewModelScope.launch {
                ProfilePreferences.saveUserId(context, novoId)
                Log.d("ProfileViewModel", "Novo userId gerado: $novoId")
            }
        } else {
            Log.d("ProfileViewModel", "userId já existe ou perfil incompleto")
        }
    }

    // Propriedades calculadas
    val idade: Int?
        get() {
            val anoAtual = Calendar.getInstance().get(Calendar.YEAR)
            return anoNascimento?.let { anoAtual - it }
        }

    val fcMaxCalculada: Int?
        get() = idade?.let { (208 - 0.7 * it).toInt() }

    val fcMax: Int?
        get() = fcMaxManual ?: fcMaxCalculada

    val zonas: List<Pair<String, IntRange>>
        get() = fcMax?.let { fc ->
            listOf(
                "Zona 1" to (fc * 0.50).toInt()..(fc * 0.60).toInt(),
                "Zona 2" to (fc * 0.60).toInt()..(fc * 0.70).toInt(),
                "Zona 3" to (fc * 0.70).toInt()..(fc * 0.80).toInt(),
                "Zona 4" to (fc * 0.80).toInt()..(fc * 0.90).toInt(),
                "Zona 5" to (fc * 0.90).toInt()..fc
            )
        } ?: emptyList()

    // Verificação de perfil incompleto
    val isProfileIncomplete: Boolean
        get() {
            val anoAtual = Calendar.getInstance().get(Calendar.YEAR)
            return nome.isNullOrBlank()
                    || sobrenome.isNullOrBlank()
                    || anoNascimento !in 1920..anoAtual
        }
}
