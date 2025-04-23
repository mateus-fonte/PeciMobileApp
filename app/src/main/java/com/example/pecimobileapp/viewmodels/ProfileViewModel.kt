package com.example.pecimobileapp.ui
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import java.util.*

class ProfileViewModel : ViewModel() {

    var nome by mutableStateOf("Maria")
    var apelido by mutableStateOf("Sabinada")
    var peso by mutableStateOf(62f)
    var anoNascimento by mutableStateOf(1992)  // apenas o ano
    var fcMaxManual by mutableStateOf<Int?>(null)

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
