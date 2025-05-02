package com.example.pecimobileapp.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Cria o DataStore
private val Context.dataStore by preferencesDataStore(name = "profile_prefs")

object ProfilePreferences {

    // Chaves
    val nomeKey = stringPreferencesKey("nome")
    val apelidoKey = stringPreferencesKey("apelido")
    val anoNascimentoKey = intPreferencesKey("ano_nascimento")
    val fcMaxManualKey = intPreferencesKey("fc_max_manual")

    // Funções para gravar

    suspend fun saveNome(context: Context, nome: String) {
        context.dataStore.edit { it[nomeKey] = nome }
    }

    suspend fun saveApelido(context: Context, apelido: String) {
        context.dataStore.edit { it[apelidoKey] = apelido }
    }

    suspend fun saveAnoNascimento(context: Context, ano: Int) {
        context.dataStore.edit { it[anoNascimentoKey] = ano }
    }

    suspend fun saveFcMaxManual(context: Context, fcMaxManual: Int?) {
        context.dataStore.edit { prefs ->
            if (fcMaxManual != null) prefs[fcMaxManualKey] = fcMaxManual
            else prefs.remove(fcMaxManualKey)
        }
    }

    // Funções para ler

    fun nomeFlow(context: Context): Flow<String> = context.dataStore.data.map {
        it[nomeKey] ?: "Maria"
    }

    fun apelidoFlow(context: Context): Flow<String> = context.dataStore.data.map {
        it[apelidoKey] ?: "Sabinada"
    }

    fun anoNascimentoFlow(context: Context): Flow<Int> = context.dataStore.data.map {
        it[anoNascimentoKey] ?: 1992
    }

    fun fcMaxManualFlow(context: Context): Flow<Int?> = context.dataStore.data.map {
        it[fcMaxManualKey]
    }
}
