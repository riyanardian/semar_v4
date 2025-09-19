package com.example.semar_v4

data class JadwalModel(
    val relay: String,
    val dayOfWeek: Int, // 1 = Minggu, 2 = Senin, ..., 7 = Sabtu
    val hour: Int,
    val minute: Int,
    val status: String, // "ON" atau "OFF"
    var executedToday: Boolean = false,
    var enabled: Boolean = true // switch per-jadwal

)
