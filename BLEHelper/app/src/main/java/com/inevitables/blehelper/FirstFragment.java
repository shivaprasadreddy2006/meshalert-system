package com.inevitables.blehelper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.inevitables.blehelper.databinding.FragmentFirstBinding;
import com.inevitables.blehelper.mesh.BleConstants;
import com.inevitables.blehelper.mesh.BleMeshManager;
import com.inevitables.blehelper.mesh.DiscoveredBleDevice;
import com.inevitables.blehelper.mesh.MeshPacket;
import com.inevitables.blehelper.net.WebBridgeManager;
import com.inevitables.blehelper.service.BleMeshBackgroundService;
import com.inevitables.blehelper.ui.LogAdapter;

public class FirstFragment extends Fragment implements BleMeshManager.BleMeshListener, WebBridgeManager.WebBridgeListener {

    private FragmentFirstBinding binding;
    private BleMeshManager mMeshManager;
    private WebBridgeManager mWebBridge;
    private LogAdapter mLogAdapter;

    private BleMeshBackgroundService mBgService;
    private boolean mIsBound = false;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BleMeshBackgroundService.LocalBinder binder = (BleMeshBackgroundService.LocalBinder) service;
            mBgService = binder.getService();
            mIsBound = true;
            updateServiceUi();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBgService = null;
            mIsBound = false;
            updateServiceUi();
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMeshManager = BleMeshManager.getInstance(requireContext());
        mWebBridge = WebBridgeManager.getInstance(requireContext());

        setupAdapters();
        setupListeners();
        bindBgService();

        // Initial Web Bridge state
        binding.etServerIp.setText(mWebBridge.getServerHost());
        binding.etServerPort.setText(String.valueOf(mWebBridge.getServerTcpPort()));
        binding.switchAutoForward.setChecked(mWebBridge.isAutoForwardEnabled());
        updateWebBridgeUi(mWebBridge.isConnected(), mWebBridge.isConnected() ? "Connected" : "Disconnected");

        // Automatically start listening for Mesh Alerts as soon as dashboard opens
        mMeshManager.ensureListening();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMeshManager.addListener(this);
        mWebBridge.addListener(this);
        updateServiceUi();
        updateWebBridgeUi(mWebBridge.isConnected(), mWebBridge.isConnected() ? "Connected" : "Disconnected");
        mMeshManager.ensureListening();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMeshManager.removeListener(this);
        mWebBridge.removeListener(this);
    }

    private void setupAdapters() {
        mLogAdapter = new LogAdapter();
        LinearLayoutManager logLayoutManager = new LinearLayoutManager(requireContext());
        logLayoutManager.setStackFromEnd(true);
        binding.rvLogs.setLayoutManager(logLayoutManager);
        binding.rvLogs.setAdapter(mLogAdapter);
    }

    private void setupListeners() {
        // Server IP text watcher
        binding.etServerIp.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.length() > 0) {
                    mWebBridge.setServerHost(s.toString().trim());
                }
            }
        });

        // Server Port text watcher
        binding.etServerPort.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.length() > 0) {
                    try {
                        int port = Integer.parseInt(s.toString().trim());
                        mWebBridge.setServerTcpPort(port);
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        // Auto-forward toggle
        binding.switchAutoForward.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mWebBridge.setAutoForwardEnabled(isChecked);
        });

        // Connect Web Bridge Button
        binding.btnConnectWebBridge.setOnClickListener(v -> {
            if (mWebBridge.isConnected()) {
                mWebBridge.disconnect();
            } else {
                binding.btnConnectWebBridge.setEnabled(false);
                mWebBridge.connect((success, message) -> {
                    if (binding != null) {
                        binding.btnConnectWebBridge.setEnabled(true);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // Test Web Bridge Button
        binding.btnTestWebBridge.setOnClickListener(v -> {
            binding.btnTestWebBridge.setEnabled(false);
            String testMsg = "Test alert from " + (Build.MODEL != null ? Build.MODEL : "Android Phone");
            mWebBridge.sendAlert(1001, BleConstants.ALERT_LEVEL_WARN, Build.MODEL, testMsg, "Floor 1", (success, message) -> {
                if (binding != null) {
                    binding.btnTestWebBridge.setEnabled(true);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Background Service Toggle
        binding.switchBgService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                toggleBackgroundService(isChecked);
            }
        });

        // Send Sample Mesh Alert Button
        binding.btnSendSampleAlert.setOnClickListener(v -> {
            int level = BleConstants.ALERT_LEVEL_EMERGENCY;
            if (binding.rbAlertWarning.isChecked()) {
                level = BleConstants.ALERT_LEVEL_WARN;
            } else if (binding.rbAlertInfo.isChecked()) {
                level = BleConstants.ALERT_LEVEL_INFO;
            }

            String msg = (binding.etAlertMsg.getText() != null) ? binding.etAlertMsg.getText().toString().trim() : "";
            if (msg.isEmpty()) {
                msg = "Alert: Distress signal from " + (Build.MODEL != null ? Build.MODEL : "Phone");
            }

            // 1. Broadcast into the air via BLE Mesh
            boolean sent = mMeshManager.sendMeshAlert(level, msg);
            if (sent) {
                Toast.makeText(requireContext(), "Broadcasted over BLE Mesh & Web App!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Broadcasting alert...", Toast.LENGTH_SHORT).show();
            }

            // 2. Also forward directly to Web Application if auto-forward is enabled
            if (mWebBridge.isAutoForwardEnabled()) {
                mWebBridge.sendAlert(0, level, Build.MODEL, msg, "Floor 1", null);
            }
        });

        // Log Console Buttons
        binding.btnClearLogs.setOnClickListener(v -> mLogAdapter.clear());

        binding.btnCopyLogs.setOnClickListener(v -> {
            String logs = mLogAdapter.getAllLogsAsText();
            if (logs.isEmpty()) {
                Toast.makeText(requireContext(), "No logs to copy", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText("BLE Mesh Helper Logs", logs);
                cm.setPrimaryClip(clip);
                Toast.makeText(requireContext(), "Logs copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindBgService() {
        Intent intent = new Intent(requireContext(), BleMeshBackgroundService.class);
        requireContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void toggleBackgroundService(boolean enable) {
        Context context = requireContext();
        Intent intent = new Intent(context, BleMeshBackgroundService.class);
        if (enable) {
            intent.setAction(BleMeshBackgroundService.ACTION_START_SERVICE);
            intent.putExtra(BleMeshBackgroundService.EXTRA_ENABLE_SERVER, true);
            intent.putExtra(BleMeshBackgroundService.EXTRA_ENABLE_WAKELOCK, false);
            ContextCompat.startForegroundService(context, intent);
        } else {
            intent.setAction(BleMeshBackgroundService.ACTION_STOP_SERVICE);
            context.startService(intent);
        }
        updateServiceUi();
    }

    private void updateServiceUi() {
        if (binding == null) return;
        boolean isRunning = mBgService != null && mBgService.isRunning();
        binding.switchBgService.setChecked(isRunning);
        binding.tvServiceStatus.setText(isRunning ? R.string.service_status_active : R.string.service_status_stopped);
        binding.tvServiceStatus.setTextColor(ContextCompat.getColor(requireContext(), isRunning ? R.color.status_connected : R.color.status_disconnected));
    }

    private void updateWebBridgeUi(boolean isConnected, String message) {
        if (binding == null || getContext() == null) return;
        binding.tvBridgeStatusBadge.setText(isConnected ? R.string.bridge_status_connected : R.string.bridge_status_disconnected);
        binding.tvBridgeStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), isConnected ? R.color.status_connected : R.color.status_disconnected));
        binding.btnConnectWebBridge.setText(isConnected ? "Disconnect" : "Connect to Web App");
    }

    // ================= WebBridgeManager Listener Callbacks =================

    @Override
    public void onBridgeStatusChanged(boolean connected, String message) {
        updateWebBridgeUi(connected, message);
    }

    @Override
    public void onBridgeLog(String tag, String message, int level) {
        if (binding == null) return;
        mLogAdapter.addLog(tag, message, level);
        binding.rvLogs.scrollToPosition(mLogAdapter.getItemCount() - 1);
    }

    // ================= BleMeshManager Listener Callbacks =================

    @Override
    public void onScanResult(DiscoveredBleDevice device) {}

    @Override
    public void onScanStateChanged(boolean isScanning) {}

    @Override
    public void onConnectionStateChanged(int state, String message, DiscoveredBleDevice device) {}

    @Override
    public void onRssiUpdated(int rssi, String quality) {}

    @Override
    public void onMtuUpdated(int mtu) {}

    @Override
    public void onPhyUpdated(int txPhy, int rxPhy) {}

    @Override
    public void onPacketTransmitted(MeshPacket packet) {}

    @Override
    public void onPacketReceived(MeshPacket packet) {}

    @Override
    public void onAlertReceived(int alertId, int level, String sender, String message) {
        // Android notifications & alert dialogs are disabled by design.
        // Instead, the alert is automatically forwarded to the Web Application!
        if (mWebBridge != null && mWebBridge.isAutoForwardEnabled()) {
            mWebBridge.sendAlert(alertId, level, sender, message, sender, null);
        }
        onLog("MeshAlert", "🚨 Received BLE Alert from " + sender + " -> Forwarded to Web Application", BleMeshManager.LOG_SUCCESS);
    }

    @Override
    public void onLog(String tag, String message, int level) {
        if (binding == null) return;
        mLogAdapter.addLog(tag, message, level);
        binding.rvLogs.scrollToPosition(mLogAdapter.getItemCount() - 1);
    }

    @Override
    public void onStatisticsUpdated(int txCount, int rxCount, long totalBytes, int errorCount) {}

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mIsBound) {
            try {
                requireContext().unbindService(mServiceConnection);
            } catch (Exception ignored) {
            }
            mIsBound = false;
        }
        binding = null;
    }
}
