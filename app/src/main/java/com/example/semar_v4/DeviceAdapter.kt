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
    private val onDeleteClicked: (Int) -> Unit  // callback hapus device
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.deviceName)
        val type: TextView = view.findViewById(R.id.deviceType)
        val chip: TextView = view.findViewById(R.id.chipId)
        val switchDevice: Switch = view.findViewById(R.id.switchDevice)
        val btnDeleteDevice: ImageView = view.findViewById(R.id.btnDeleteDevice)
        val runhourRelay2: TextView = view.findViewById(R.id.tvRunhourRelay2)

        fun bind(device: DeviceModel) {
            name.text = device.name
            type.text = device.type
            chip.text = device.chipId
            runhourRelay2.text = "Runhour Relay 2: ${device.runhourRelay2}"

            // 🔹 set status awal switch
            switchDevice.setOnCheckedChangeListener(null)
            switchDevice.isChecked = device.status
            switchDevice.setOnCheckedChangeListener { _, isChecked ->
                device.status = isChecked
                onSwitchChanged(device, isChecked)
            }

            // 🔹 klik item → detail
            itemView.setOnClickListener { onClick(device) }

            btnDeleteDevice.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDeleteClicked(pos)  // ✅ tidak perlu cast ke BerandaActivity
                }
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        DeviceViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        )

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount() = devices.size

    // 🔹 Update realtime berdasarkan chipId
    fun updateDevice(chipId: String, type: String, value: String) {
        val index = devices.indexOfFirst { it.chipId == chipId }
        if (index != -1) {
            val device = devices[index]
            when (type) {
                "relay2" -> device.status = (value == "1" || value.equals("on", true))
                "runhour" -> device.runhourRelay2 = value
            }
            notifyItemChanged(index)
        }
    }
}
