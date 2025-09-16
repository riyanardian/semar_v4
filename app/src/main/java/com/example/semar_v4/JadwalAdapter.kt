package com.example.semar_v4

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class JadwalAdapter(private val listJadwal: List<JadwalModel>) :
    RecyclerView.Adapter<JadwalAdapter.JadwalViewHolder>() {

    class JadwalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvJadwal: TextView = itemView.findViewById(R.id.tvJadwal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JadwalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_jadwal, parent, false)
        return JadwalViewHolder(view)
    }

    override fun onBindViewHolder(holder: JadwalViewHolder, position: Int) {
        val jadwal = listJadwal[position]

        // gabungkan info relay, hari, jam, dan status jadi satu string
        val dayName = when(jadwal.dayOfWeek) {
            1 -> "Minggu"
            2 -> "Senin"
            3 -> "Selasa"
            4 -> "Rabu"
            5 -> "Kamis"
            6 -> "Jumat"
            7 -> "Sabtu"
            else -> "-"
        }

        holder.tvJadwal.text = "${jadwal.relay} | $dayName | ${jadwal.hour}:${jadwal.minute.toString().padStart(2,'0')} | ${jadwal.status}"
    }

    override fun getItemCount(): Int = listJadwal.size
}
