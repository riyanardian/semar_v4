package com.example.semar_v4

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val devices: MutableList<DeviceModel>,
    private val onClick: (DeviceModel) -> Unit,
    private val onSwitchChanged: (DeviceModel, Boolean) -> Unit,
    private val onDeleteClicked: (Int) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.deviceName)
        val type: TextView = view.findViewById(R.id.deviceType)
        val chip: TextView = view.findViewById(R.id.chipId)
        val btnDeleteDevice: ImageView = view.findViewById(R.id.btnDeleteDevice)

        fun bind(device: DeviceModel) {
            name.text = device.name
            type.text = device.type
            chip.text = device.chipId


            itemView.setOnClickListener { onClick(device) }
            btnDeleteDevice.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDeleteClicked(pos)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        DeviceViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false))

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount() = devices.size

}

