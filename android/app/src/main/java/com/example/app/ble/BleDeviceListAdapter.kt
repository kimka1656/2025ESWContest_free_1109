package com.example.app.ble // 실제 패키지명으로 변경하세요

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.app.R // 실제 R 클래스 경로로 변경하세요

class BleDeviceListAdapter(
    private val devices: MutableList<BluetoothDevice>,
    private val onItemClicked: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BleDeviceListAdapter.DeviceViewHolder>() {

    companion object {
        private const val TAG = "BleDeviceListAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return DeviceViewHolder(view)
    }

    @SuppressLint("MissingPermission") // 권한은 Activity 레벨에서 확인 가정
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        try {
            holder.deviceName.text = device.name ?: "Unknown Device"
            holder.deviceAddress.text = device.address
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException for device ${device.address}: ${e.message}")
            // 권한 문제 발생 시 UI에 표시할 내용
            holder.deviceName.text = "Permission Issue"
            holder.deviceAddress.text = device.address
            // 또는 사용자에게 권한 요청을 다시 하도록 유도할 수 있지만, 어댑터에서는 적절하지 않음.
        }

        holder.itemView.setOnClickListener {
            onItemClicked(device)
        }
    }

    override fun getItemCount(): Int = devices.size

    @SuppressLint("NotifyDataSetChanged")
    fun addDevice(device: BluetoothDevice) {
        if (!devices.any { it.address == device.address }) {
            devices.add(device)
            // notifyItemInserted(devices.size - 1) // 특정 아이템 추가 알림
            notifyDataSetChanged() // 간단하게 전체 새로고침 (중복 방지 로직과 함께 사용 시 문제 적음)
            Log.d(TAG, "Device added: ${try {device.name ?: "Unknown"} catch (e: SecurityException){"Permission Issue"}} (${device.address}), Count: ${devices.size}")
        } else {
            // 이미 목록에 있는 기기라면 업데이트 (예: 이름이 변경된 경우)
            val index = devices.indexOfFirst { it.address == device.address }
            if (index != -1) {
                // device.name이 null이 아니면서, 기존 이름과 다를 경우에만 업데이트 (선택적)
                val currentName = try { devices[index].name } catch (e: SecurityException) { null }
                val newName = try { device.name } catch (e: SecurityException) { null }

                if (newName != null && currentName != newName) {
                    devices[index] = device // 객체를 교체하거나 필요한 필드만 업데이트
                    notifyItemChanged(index)
                    Log.d(TAG, "Device updated: $newName (${device.address})")
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearDevices() {
        devices.clear()
        notifyDataSetChanged()
        Log.d(TAG, "Devices cleared.")
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        val deviceAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)
    }
}
