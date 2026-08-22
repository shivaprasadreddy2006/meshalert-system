package com.inevitables.blehelper.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.inevitables.blehelper.R;
import com.inevitables.blehelper.mesh.DiscoveredBleDevice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    public interface OnDeviceClickListener {
        void onConnectClick(DiscoveredBleDevice device);
    }

    private final List<DiscoveredBleDevice> mDevices = new ArrayList<>();
    private final Map<String, Integer> mIndexMap = new HashMap<>();
    private final Map<String, Long> mLastUpdateTimes = new HashMap<>();
    private final OnDeviceClickListener mListener;

    public DeviceAdapter(OnDeviceClickListener listener) {
        this.mListener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < mDevices.size()) {
            return mDevices.get(position).getAddress().hashCode();
        }
        return RecyclerView.NO_ID;
    }

    public synchronized void setDevices(List<DiscoveredBleDevice> devices) {
        mDevices.clear();
        mIndexMap.clear();
        mLastUpdateTimes.clear();
        if (devices != null) {
            for (int i = 0; i < devices.size(); i++) {
                DiscoveredBleDevice d = devices.get(i);
                mDevices.add(d);
                mIndexMap.put(d.getAddress().toUpperCase(), i);
            }
        }
        notifyDataSetChanged();
    }

    public synchronized void addOrUpdateDevice(DiscoveredBleDevice device) {
        if (device == null || device.getAddress() == null) return;

        String key = device.getAddress().toUpperCase();
        Integer index = mIndexMap.get(key);

        long now = System.currentTimeMillis();
        Long lastTime = mLastUpdateTimes.get(key);

        if (index != null && index >= 0 && index < mDevices.size()) {
            // Throttle UI row redraws: update at most once every 600ms per row unless device properties changed
            DiscoveredBleDevice existing = mDevices.get(index);
            boolean rssiChangedSignificantly = Math.abs(existing.getRssi() - device.getRssi()) >= 4;
            if (lastTime == null || (now - lastTime) >= 600 || rssiChangedSignificantly) {
                mLastUpdateTimes.put(key, now);
                mDevices.set(index, device);
                notifyItemChanged(index);
            }
        } else {
            // New device found
            mLastUpdateTimes.put(key, now);
            int newIndex = mDevices.size();
            mDevices.add(device);
            mIndexMap.put(key, newIndex);
            notifyItemInserted(newIndex);
        }
    }

    public synchronized void clear() {
        mDevices.clear();
        mIndexMap.clear();
        mLastUpdateTimes.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DiscoveredBleDevice device = mDevices.get(position);
        holder.tvName.setText(device.getName());
        holder.tvAddress.setText(device.getAddress());
        holder.tvRssi.setText(String.format("%d dBm", device.getRssi()));
        holder.tvQuality.setText(device.getSignalQuality());

        int rssi = device.getRssi();
        if (rssi >= -60) {
            holder.tvRssi.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.signal_excellent));
        } else if (rssi >= -75) {
            holder.tvRssi.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.signal_good));
        } else if (rssi >= -85) {
            holder.tvRssi.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.signal_fair));
        } else {
            holder.tvRssi.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.signal_poor));
        }

        if (device.isMeshProxy()) {
            holder.badgeMesh.setVisibility(View.VISIBLE);
            holder.badgeMesh.setText("Mesh Proxy (0x1828)");
            holder.badgeBle.setVisibility(View.GONE);
        } else if (device.isMeshProvisioning()) {
            holder.badgeMesh.setVisibility(View.VISIBLE);
            holder.badgeMesh.setText("Mesh Provisioning (0x1827)");
            holder.badgeBle.setVisibility(View.GONE);
        } else {
            holder.badgeMesh.setVisibility(View.GONE);
            holder.badgeBle.setVisibility(View.VISIBLE);
        }

        holder.btnConnect.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onConnectClick(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mDevices.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress, tvRssi, tvQuality, badgeMesh, badgeBle;
        MaterialButton btnConnect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_device_name);
            tvAddress = itemView.findViewById(R.id.tv_device_address);
            tvRssi = itemView.findViewById(R.id.tv_rssi);
            tvQuality = itemView.findViewById(R.id.tv_signal_quality);
            badgeMesh = itemView.findViewById(R.id.badge_mesh_proxy);
            badgeBle = itemView.findViewById(R.id.badge_ble);
            btnConnect = itemView.findViewById(R.id.btn_connect);
        }
    }
}
