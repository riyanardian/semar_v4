
package com.example.semar_v4

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class JadwalAdapter(
    private val list: MutableList<JadwalModel>,
    private val showSwitch: Boolean, // flag untuk tampilkan switch
    private val onDeleteClick: (Int) -> Unit,
    private val onSwitchChange: (JadwalModel, Boolean) -> Unit
) : RecyclerView.Adapter<JadwalAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRelay: TextView = view.findViewById(R.id.tvItemName)
        val tvJamMulai: TextView = view.findViewById(R.id.tvJamMulai)
        val tvJamMati: TextView = view.findViewById(R.id.tvJamMati)
        val tvHari: TextView = view.findViewById(R.id.tvHari)
        val switchEnable: Switch = view.findViewById(R.id.switchEnable)
        val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_jadwal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        holder.tvRelay.text = "Relay ${item.relay}"
        holder.tvJamMulai.text = "Jam Mulai: ${item.getJamMulai()}"
        holder.tvJamMati.text = "Jam Mati: ${item.getJamMati()}"
        holder.tvHari.text = "Hari Aktif: ${item.getHariString()}"

        // --- SWITCH ---
        if (showSwitch) {
            holder.switchEnable.visibility = View.VISIBLE
            holder.switchEnable.isChecked = item.enabled // ambil dari model
            holder.switchEnable.setOnCheckedChangeListener { _, isChecked ->
                item.enabled = isChecked
                onSwitchChange(item, isChecked)
            }
        } else {
            holder.switchEnable.visibility = View.GONE
        }

        // --- DELETE ---
        holder.btnDelete.setOnClickListener {
            onDeleteClick(position)
        }
    }

    override fun getItemCount(): Int = list.size
}
