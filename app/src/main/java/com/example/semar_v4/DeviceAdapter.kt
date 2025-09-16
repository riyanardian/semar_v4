package com.example.semar_v4

import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage

class DeviceAdapter(
    private val devices: MutableList<DeviceModel>,
    private val onClick: (DeviceModel) -> Unit,
    private val onSwitchChanged: (DeviceModel, Boolean) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.deviceName)
        val type: TextView = view.findViewById(R.id.deviceType)
        val chip: TextView = view.findViewById(R.id.chipId)
        val switchDevice: Switch = view.findViewById(R.id.switchDevice)
        val btnDeleteDevice: ImageView = view.findViewById(R.id.btnDeleteDevice)
        val runhourRelay2: TextView = view.findViewById(R.id.tvRunhourRelay2) // ✅ tambahkan ini


        fun bind(device: DeviceModel) {
            name.text = device.name
            type.text = device.type
            chip.text = device.chipId
            runhourRelay2.text = "Runhour Relay 2: ${device.runhourRelay2}" // ✅ binding

            // set status awal
            switchDevice.setOnCheckedChangeListener(null)
            switchDevice.isChecked = device.status
            switchDevice.setOnCheckedChangeListener { _, isChecked ->
                device.status = isChecked
                onSwitchChanged(device, isChecked)
            }

            itemView.setOnClickListener { onClick(device) }
            btnDeleteDevice.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    (itemView.context as BerandaActivity).deleteDeviceAt(pos)
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

    // update status device dari MQTT
    fun updateDeviceStatus(chipId: String, isOn: Boolean) {
        val index = devices.indexOfFirst { it.chipId == chipId }
        if (index != -1) {
            devices[index].status = isOn
            notifyItemChanged(index)
        }
    }
    fun updateDeviceRunhour(chipId: String, runhour: String) {
        val index = devices.indexOfFirst { it.chipId == chipId }
        if (index != -1) {
            devices[index].runhourRelay2 = runhour
            notifyItemChanged(index)
        }
    }

}
