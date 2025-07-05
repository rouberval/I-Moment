package com.example.moment.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.moment.ble.DiscoveredDevice
import com.example.moment.databinding.ItemDiscoveredDeviceBinding

class DiscoveredDeviceAdapter(private val onItemClicked: (DiscoveredDevice) -> Unit) :
    ListAdapter<DiscoveredDevice, DiscoveredDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDiscoveredDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device)
        holder.itemView.setOnClickListener {
            onItemClicked(device)
        }
    }

    class DeviceViewHolder(private val binding: ItemDiscoveredDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: DiscoveredDevice) {
            binding.textViewDeviceAdvertisedName.text = device.advertisedName ?: "N/A"
            binding.textViewDeviceAddress.text = device.deviceAddress
            binding.textViewDeviceRssi.text = "RSSI: ${device.rssi} dBm"
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<DiscoveredDevice>() {
        override fun areItemsTheSame(oldItem: DiscoveredDevice, newItem: DiscoveredDevice): Boolean {
            return oldItem.deviceAddress == newItem.deviceAddress
        }

        override fun areContentsTheSame(oldItem: DiscoveredDevice, newItem: DiscoveredDevice): Boolean {
            // Compare all relevant fields that might change and require UI update
            return oldItem.advertisedName == newItem.advertisedName &&
                   oldItem.rssi == newItem.rssi &&
                   oldItem.deviceName == newItem.deviceName // if you display deviceName too
        }
    }
}
