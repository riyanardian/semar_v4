package com.example.semar_v4

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class JadwalModel(
    var relay: String = "",
    var days: List<Int> = emptyList(),   // contoh: [2,3,4] = Senin, Selasa, Rabu
    var startHour: Int = 0,
    var startMinute: Int = 0,
    var endHour: Int = 0,
    var endMinute: Int = 0,
    var enabled: Boolean = false,
    var executedToday: Boolean = false
) {

    // Format jam mulai (HH:mm = 24 jam, hh:mm a = 12 jam dengan AM/PM)
    fun getJamMulai(): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
        }
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
    }

    // Format jam mati
    fun getJamMati(): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
        }
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
    }

    // Konversi list hari ke string (misal: Senin, Rabu, Jumat)
    fun getHariString(): String {
        if (days.isEmpty()) return "-"
        val hariList = days.map {
            when (it) {
                Calendar.MONDAY -> "Senin"
                Calendar.TUESDAY -> "Selasa"
                Calendar.WEDNESDAY -> "Rabu"
                Calendar.THURSDAY -> "Kamis"
                Calendar.FRIDAY -> "Jumat"
                Calendar.SATURDAY -> "Sabtu"
                Calendar.SUNDAY -> "Minggu"
                else -> "-"
            }
        }
        return hariList.joinToString(", ")
    }
}
