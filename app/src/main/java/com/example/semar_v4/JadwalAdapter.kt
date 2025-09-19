package com.example.semar_v4

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class JadwalAdapter(
    private val listJadwal: MutableList<JadwalModel>,
    private val onDeleteClick: (position: Int) -> Unit,
    private val onSwitchChange: (Int, Boolean) -> Unit,

) : RecyclerView.Adapter<JadwalAdapter.JadwalViewHolder>() {

    inner class JadwalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvJadwal: TextView = itemView.findViewById(R.id.tvJadwal)
        val btnDeleteDevice: ImageView = itemView.findViewById(R.id.btnDeleteDevice)
        val switchEnable: Switch = itemView.findViewById(R.id.switchEnable) // switch per-jadwal

        fun bind(jadwal: JadwalModel) {
            val dayName = when (jadwal.dayOfWeek) {
                1 -> "Minggu"
                2 -> "Senin"
                3 -> "Selasa"
                4 -> "Rabu"
                5 -> "Kamis"
                6 -> "Jumat"
                7 -> "Sabtu"
                else -> "-"
            }

            tvJadwal.text = "${jadwal.relay} | $dayName | ${jadwal.hour}:${jadwal.minute.toString().padStart(2, '0')} | ${jadwal.status}"

            btnDeleteDevice.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDeleteClick(pos)
                }
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JadwalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_jadwal, parent, false)
        return JadwalViewHolder(view)
    }

    override fun onBindViewHolder(holder: JadwalViewHolder, position: Int) {
        holder.bind(listJadwal[position])
        val jadwal = listJadwal[position]

        holder.switchEnable.isChecked = jadwal.enabled
        holder.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            onSwitchChange(position, isChecked)
        }
    }

    override fun getItemCount(): Int = listJadwal.size
}
