package com.example.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

@SuppressLint("MissingPermission")
class DeviceScanAdapter(
    private val onDeviceClicked: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<DeviceScanAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<BluetoothDevice>()
    private val deviceAddresses = mutableSetOf<String>()

    fun addDevice(device: BluetoothDevice) {
        if (!deviceAddresses.contains(device.address)) {
            devices.add(device)
            deviceAddresses.add(device.address)
            notifyItemInserted(devices.size - 1)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return DeviceViewHolder(view)
    }

    override fun getItemCount(): Int = devices.size

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text1: TextView = itemView.findViewById(android.R.id.text1)
        private val text2: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(device: BluetoothDevice) {
            text1.text = device.name ?: "Unknown Device"
            text2.text = device.address
            itemView.setOnClickListener { onDeviceClicked(device) }
        }
    }
}