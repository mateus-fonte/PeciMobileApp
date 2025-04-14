package com.example.pecimobileapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pecimobileapp.models.HistoryRecord

@Composable
fun HistoricoScreen(onBackClick: () -> Unit) {
    // Lista de registros fake para apresentação, utilizando a data class HistoryRecord importada do diretório models
    val fakeHistory = listOf(
        HistoryRecord(
            date = "23/07/2023",
            duration = 23,
            avgHeartRate = 78.0 ,
            avgTemperature = 36.2
        ),
        HistoryRecord(
            date = "22/07/2023",
            duration = 30,
            avgHeartRate = 82.0 ,
            avgTemperature = 36.4
        ),
        HistoryRecord(
            date = "21/07/2023",
            duration = 45,
            avgHeartRate = 75.0,
            avgTemperature = 36.0
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Histórico de Atividades",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(fakeHistory) { record ->
                    HistoryRecordCard(record = record)
                }
            }
        }
    }
}

@Composable
fun HistoryRecordCard(record: HistoryRecord) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Data: ${record.date}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Duração: ${record.duration}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Frequência Cardíaca Média: ${record.avgHeartRate}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Temperatura Média: ${record.avgTemperature}",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
