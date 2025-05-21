package com.example.pecimobileapp.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Cria o DataStore
private val Context.dataStore by preferencesDataStore(name = "profile_prefs")

object ProfilePreferences {

    // 🔑 Chaves
    val nomeKey = stringPreferencesKey("nome")
    val sobrenomeKey = stringPreferencesKey("sobrenome")
    val anoNascimentoKey = intPreferencesKey("ano_nascimento")
    val fcMaxManualKey = intPreferencesKey("fc_max_manual")
    val userIdKey = stringPreferencesKey("user_id") // Novo campo

    // 💾 Funções para gravar

    suspend fun saveNome(context: Context, nome: String?) {
        context.dataStore.edit {
            if (!nome.isNullOrBlank()) it[nomeKey] = nome
            else it.remove(nomeKey)
        }
    }

    suspend fun saveSobrenome(context: Context, sobrenome: String?) {
        context.dataStore.edit {
            if (!sobrenome.isNullOrBlank()) it[sobrenomeKey] = sobrenome
            else it.remove(sobrenomeKey)
        }
    }

    suspend fun saveAnoNascimento(context: Context, ano: Int?) {
        context.dataStore.edit {
            if (ano != null) it[anoNascimentoKey] = ano
            else it.remove(anoNascimentoKey)
        }
    }

    suspend fun saveFcMaxManual(context: Context, fcMaxManual: Int?) {
        context.dataStore.edit { prefs ->
            if (fcMaxManual != null) prefs[fcMaxManualKey] = fcMaxManual
            else prefs.remove(fcMaxManualKey)
        }
    }

    suspend fun saveUserId(context: Context, id: String) {
        context.dataStore.edit { it[userIdKey] = id }
    }

    suspend fun clearAll(context: Context) {
        context.dataStore.edit { it.clear() }
    }


    // 📤 Funções para ler

    fun nomeFlow(context: Context): Flow<String?> = context.dataStore.data.map {
        it[nomeKey]
    }

    fun sobrenomeFlow(context: Context): Flow<String?> = context.dataStore.data.map {
        it[sobrenomeKey]
    }

    fun anoNascimentoFlow(context: Context): Flow<Int?> = context.dataStore.data.map {
        it[anoNascimentoKey]
    }

    fun fcMaxManualFlow(context: Context): Flow<Int?> = context.dataStore.data.map {
        it[fcMaxManualKey]
    }

    fun userIdFlow(context: Context): Flow<String?> = context.dataStore.data.map {
        it[userIdKey]
    }
}
