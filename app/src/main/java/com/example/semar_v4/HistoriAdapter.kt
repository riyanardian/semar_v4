package com.example.semar_v4

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoriAdapter(private val list: List<HistoriModel>) :
    RecyclerView.Adapter<HistoriAdapter.HistoriViewHolder>() {

    class HistoriViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtRelay: TextView = itemView.findViewById(R.id.txtRelay)
        val txtStatus: TextView = itemView.findViewById(R.id.txtStatus)
        val txtMode: TextView = itemView.findViewById(R.id.txtMode)
        val txtWaktu: TextView = itemView.findViewById(R.id.txtWaktu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoriViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_histori, parent, false) // pastikan nama file xml sesuai
        return HistoriViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoriViewHolder, position: Int) {
        val data = list[position]
        holder.txtRelay.text = data.relay
        holder.txtStatus.text = data.status
        holder.txtMode.text = data.mode
        holder.txtWaktu.text = data.waktu
    }

    override fun getItemCount(): Int = list.size
}
