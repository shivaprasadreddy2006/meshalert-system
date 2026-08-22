package com.inevitables.blehelper;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.inevitables.blehelper.databinding.FragmentSecondBinding;
import com.inevitables.blehelper.mesh.BleConstants;
import com.inevitables.blehelper.mesh.BleDiagnosticsHelper;
import com.inevitables.blehelper.mesh.BleMeshManager;
import com.inevitables.blehelper.mesh.BleMeshServerManager;
import com.inevitables.blehelper.mesh.DiscoveredBleDevice;
import com.inevitables.blehelper.mesh.MeshPacket;
import com.inevitables.blehelper.service.BleMeshBackgroundService;
import com.inevitables.blehelper.ui.DeviceAdapter;

import java.util.Locale;

public class SecondFragment extends Fragment implements BleMeshManager.BleMeshListener {

    private FragmentSecondBinding binding;
    private BleMeshManager mMeshManager;
    private BleMeshServerManager mServerManager;
    private DeviceAdapter mDeviceAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSecondBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMeshManager = BleMeshManager.getInstance(requireContext());
        mServerManager = BleMeshServerManager.getInstance(requireContext());

        setupAdapters();
        setupListeners();
        refreshDiagnostics();
        updateConnectionUi(mMeshManager.getConnectionState(), null, mMeshManager.getCurrentDevice());

        // Sync node-mode switch states
        binding.switchMeshServer.setChecked(
                mServerManager.isAdvertising() || mServerManager.isGattServerRunning());
    }

    @Override
    public void onResume() {
        super.onResume();
        mMeshManager.addListener(this);
        refreshDiagnostics();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMeshManager.removeListener(this);
    }

    private void setupAdapters() {
        mDeviceAdapter = new DeviceAdapter(device -> {
            mMeshManager.connect(device);
            Toast.makeText(requireContext(), "Connecting to " + device.getName(), Toast.LENGTH_SHORT).show();
        });
        binding.rvDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvDevices.setAdapter(mDeviceAdapter);
    }

    private void setupListeners() {
        // Request Permissions Button
        binding.btnRequestPermissions.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).requestBlePermissions();
            }
        });

        // Ignore Battery Optimization Button
        binding.btnIgnoreBattery.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).requestIgnoreBatteryOptimizations();
            }
        });

        // Bluetooth Chip Click to Enable
        binding.chipBluetooth.setOnClickListener(v -> {
            if (!mMeshManager.isBluetoothEnabled() && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).requestEnableBluetooth();
            }
        });

        // Mesh Server Mode Toggle
        binding.switchMeshServer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                if (isChecked) {
                    mServerManager.startServer();
                } else {
                    mServerManager.stopServer();
                }
            }
        });

        // WakeLock Toggle — persists via background service if running
        binding.switchWakelock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // No-op if service not running; the setting will be applied on next service start
        });

        // Scanner Controls
        binding.btnScan.setOnClickListener(v -> {
            if (mMeshManager.isScanning()) {
                mMeshManager.stopScan();
            } else {
                mDeviceAdapter.clear();
                binding.tvEmptyScanner.setVisibility(View.GONE);
                boolean meshOnly = binding.cbFilterMesh.isChecked();
                mMeshManager.startScan(meshOnly);
            }
        });

        // Disconnect Button
        binding.btnDisconnect.setOnClickListener(v -> mMeshManager.disconnect());

        // Quick Mesh Actions
        binding.btnFilterConfig.setOnClickListener(v -> {
            boolean ok = mMeshManager.sendProxyConfigSetFilter(BleConstants.FILTER_TYPE_WHITELIST);
            if (!ok) {
                Toast.makeText(requireContext(), "Connect to Mesh node first", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnMeshPing.setOnClickListener(v -> {
            boolean ok = mMeshManager.sendMeshPing(0x0001, 0xFFFF);
            if (!ok) {
                Toast.makeText(requireContext(), "Connect to Mesh node first", Toast.LENGTH_SHORT).show();
            }
        });

        // Send Custom Hex PDU
        binding.btnSendCustomPdu.setOnClickListener(v -> {
            String hex = binding.etHex.getText() != null ? binding.etHex.getText().toString().trim() : "";
            if (hex.isEmpty()) {
                binding.tilHex.setError("Enter hex bytes");
                return;
            }
            binding.tilHex.setError(null);
            boolean ok = mMeshManager.sendCustomHexPayload(hex);
            if (ok) {
                binding.etHex.setText("");
            } else {
                Toast.makeText(requireContext(), "Failed to send packet", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshDiagnostics() {
        if (binding == null || getContext() == null) return;
        Context context = requireContext();

        BleDiagnosticsHelper.HardwareInfo hw = BleDiagnosticsHelper.getHardwareInfo(context);
        binding.chipBluetooth.setText(hw.isBluetoothEnabled ? R.string.status_bt_enabled : R.string.status_bt_disabled);
        binding.chipBluetooth.setTextColor(ContextCompat.getColor(context, hw.isBluetoothEnabled ? R.color.status_connected : R.color.status_disconnected));

        binding.chipBle.setText(hw.isBleSupported ? R.string.status_ble_supported : R.string.status_ble_unsupported);
        binding.chipBle.setTextColor(ContextCompat.getColor(context, hw.isBleSupported ? R.color.status_connected : R.color.status_disconnected));

        binding.chipPhy2m.setVisibility(hw.isLe2MPhySupported ? View.VISIBLE : View.GONE);
        binding.chipPhyCoded.setVisibility(hw.isLeCodedPhySupported ? View.VISIBLE : View.GONE);
        binding.chipExtAdv.setVisibility(hw.isExtendedAdvSupported ? View.VISIBLE : View.GONE);

        BleDiagnosticsHelper.PermissionInfo perm = BleDiagnosticsHelper.checkPermissions(context);
        if (!perm.missingPermissions.isEmpty()) {
            binding.layoutPermissionWarning.setVisibility(View.VISIBLE);
            binding.tvPermissionStatus.setText(String.format("Missing permissions: %s", perm.missingPermissions));
        } else {
            binding.layoutPermissionWarning.setVisibility(View.GONE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.layoutBatteryWarning.setVisibility(perm.isIgnoringBatteryOptimizations ? View.GONE : View.VISIBLE);
        } else {
            binding.layoutBatteryWarning.setVisibility(View.GONE);
        }
    }

    private void updateConnectionUi(int state, String message, DiscoveredBleDevice device) {
        if (binding == null || getContext() == null) return;
        Context context = requireContext();

        switch (state) {
            case BleMeshManager.STATE_READY:
                binding.tvConnBadge.setText(R.string.conn_state_ready);
                binding.tvConnBadge.setTextColor(ContextCompat.getColor(context, R.color.status_connected));
                binding.tvConnBadge.setBackgroundResource(R.drawable.bg_badge_mesh);
                binding.btnDisconnect.setVisibility(View.VISIBLE);
                break;
            case BleMeshManager.STATE_CONNECTING:
                binding.tvConnBadge.setText(R.string.conn_state_connecting);
                binding.tvConnBadge.setTextColor(ContextCompat.getColor(context, R.color.status_connecting));
                binding.tvConnBadge.setBackgroundResource(R.drawable.bg_badge_generic);
                binding.btnDisconnect.setVisibility(View.VISIBLE);
                break;
            case BleMeshManager.STATE_DISCOVERING_SERVICES:
            case BleMeshManager.STATE_REQUESTING_MTU:
            case BleMeshManager.STATE_CONFIGURING_CCCD:
                binding.tvConnBadge.setText(message != null ? message : getString(R.string.conn_state_discovering));
                binding.tvConnBadge.setTextColor(ContextCompat.getColor(context, R.color.status_connecting));
                binding.btnDisconnect.setVisibility(View.VISIBLE);
                break;
            case BleMeshManager.STATE_ERROR:
                binding.tvConnBadge.setText(message != null ? message : getString(R.string.conn_state_error));
                binding.tvConnBadge.setTextColor(ContextCompat.getColor(context, R.color.status_disconnected));
                binding.btnDisconnect.setVisibility(View.GONE);
                break;
            case BleMeshManager.STATE_DISCONNECTED:
            default:
                binding.tvConnBadge.setText(R.string.conn_state_disconnected);
                binding.tvConnBadge.setTextColor(ContextCompat.getColor(context, R.color.status_disconnected));
                binding.tvConnBadge.setBackgroundResource(R.drawable.bg_badge_generic);
                binding.btnDisconnect.setVisibility(View.GONE);
                binding.tvMetricRssi.setText("-- dBm");
                break;
        }

        if (device != null) {
            binding.tvConnectedDeviceInfo.setText(String.format("%s (%s)", device.getName(), device.getAddress()));
        } else {
            binding.tvConnectedDeviceInfo.setText(R.string.label_no_device_connected);
        }
    }

    // ================= BleMeshManager Listener Callbacks =================

    @Override
    public void onScanResult(DiscoveredBleDevice device) {
        if (binding == null) return;
        mDeviceAdapter.addOrUpdateDevice(device);
        binding.tvEmptyScanner.setVisibility(mDeviceAdapter.getItemCount() > 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onScanStateChanged(boolean isScanning) {
        if (binding == null) return;
        binding.pbScanning.setVisibility(isScanning ? View.VISIBLE : View.GONE);
        binding.btnScan.setText(isScanning ? R.string.btn_stop_scan : R.string.btn_start_scan);
    }

    @Override
    public void onConnectionStateChanged(int state, String message, DiscoveredBleDevice device) {
        updateConnectionUi(state, message, device);
    }

    @Override
    public void onRssiUpdated(int rssi, String quality) {
        if (binding == null || getContext() == null) return;
        binding.tvMetricRssi.setText(String.format(Locale.getDefault(), "%d dBm (%s)", rssi, quality));

        int colorRes;
        if (rssi >= -60) {
            colorRes = R.color.signal_excellent;
        } else if (rssi >= -75) {
            colorRes = R.color.signal_good;
        } else if (rssi >= -85) {
            colorRes = R.color.signal_fair;
        } else {
            colorRes = R.color.signal_poor;
        }
        binding.tvMetricRssi.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
    }

    @Override
    public void onMtuUpdated(int mtu) {
        if (binding == null) return;
        binding.tvMetricMtu.setText(String.format(Locale.getDefault(), "%d B", mtu));
    }

    @Override
    public void onPhyUpdated(int txPhy, int rxPhy) {
        if (binding == null) return;
        String phyStr = "LE 1M";
        if (txPhy == 2) phyStr = "LE 2M";
        else if (txPhy == 3) phyStr = "LE Coded";
        binding.tvMetricPhy.setText(phyStr);
    }

    @Override
    public void onPacketTransmitted(MeshPacket packet) {}

    @Override
    public void onPacketReceived(MeshPacket packet) {}

    @Override
    public void onAlertReceived(int alertId, int level, String sender, String message) {}

    @Override
    public void onLog(String tag, String message, int level) {}

    @Override
    public void onStatisticsUpdated(int txCount, int rxCount, long totalBytes, int errorCount) {
        if (binding == null) return;
        binding.tvStatTx.setText(String.format(Locale.getDefault(), "TX Packets: %d", txCount));
        binding.tvStatRx.setText(String.format(Locale.getDefault(), "RX Packets: %d", rxCount));
        binding.tvStatBytes.setText(String.format(Locale.getDefault(), "Bytes: %d B (Err: %d)", totalBytes, errorCount));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
