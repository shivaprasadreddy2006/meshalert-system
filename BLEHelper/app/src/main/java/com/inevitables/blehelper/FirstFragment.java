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
        if (binding.etServerIp != null) {
            binding.etServerIp.setText(mWebBridge.getServerUrl());
        }
        binding.switchAutoForward.setChecked(mWebBridge.isAutoForwardEnabled());
        updateWebBridgeUi(mWebBridge.isConnected(), mWebBridge.isConnected() ? "Cloud Connected 🟢" : "Ready to Connect");

        // Automatically start listening for Mesh Alerts as soon as dashboard opens
        mMeshManager.ensureListening();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMeshManager.addListener(this);
        mWebBridge.addListener(this);
        updateServiceUi();
        updateWebBridgeUi(mWebBridge.isConnected(), mWebBridge.isConnected() ? "Cloud Connected 🟢" : "Ready to Connect");
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
        // Server URL text watcher
        if (binding.etServerIp != null) {
            binding.etServerIp.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (s != null && s.length() > 0) {
                        mWebBridge.setServerUrl(s.toString().trim());
                    }
                }
            });
        }

        // Auto-forward toggle
        binding.switchAutoForward.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mWebBridge.setAutoForwardEnabled(isChecked);
        });

        // Connect / Test Web Bridge Button
        binding.btnConnectWebBridge.setOnClickListener(v -> {
            binding.btnConnectWebBridge.setEnabled(false);
            mWebBridge.connect((success, message) -> {
                if (binding != null) {
                    binding.btnConnectWebBridge.setEnabled(true);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Test Web Bridge Button (Sends sample emergency alert to Cloud)
        binding.btnTestWebBridge.setOnClickListener(v -> {
            binding.btnTestWebBridge.setEnabled(false);
            String testMsg = "Fire alert broadcast from " + (Build.MODEL != null ? Build.MODEL : "Android Phone");
            mWebBridge.sendAlert(1001, BleConstants.ALERT_LEVEL_EMERGENCY, Build.MODEL, testMsg, "Floor 1", (success, message) -> {
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

        // Send Sample Mesh Alert Button (Local BLE broadcast + Cloud forward)
        binding.btnSendAlert.setOnClickListener(v -> {
            binding.btnSendAlert.setEnabled(false);
            mMeshManager.broadcastAlert(
                    BleConstants.ALERT_LEVEL_EMERGENCY,
                    "Fire detected! Evacuate immediately via Exit A.",
                    (success, message) -> {
                        if (binding != null) {
                            binding.btnSendAlert.setEnabled(true);
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        }
                    }
            );

            // Auto-forward sample alert to Web App
            if (mWebBridge.isAutoForwardEnabled()) {
                mWebBridge.sendAlert(
                        (int) (System.currentTimeMillis() % 100000),
                        BleConstants.ALERT_LEVEL_EMERGENCY,
                        Build.MODEL != null ? Build.MODEL : "Local Device",
                        "Fire detected! Evacuate immediately via Exit A.",
                        "Floor 1",
                        null
                );
            }
        });
    }

    private void updateWebBridgeUi(boolean connected, String message) {
        if (binding == null) return;
        binding.tvBridgeStatusBadge.setText(connected ? "Online 🟢" : "Ready ⚪");
        binding.tvBridgeStatusBadge.setBackgroundResource(
                connected ? R.drawable.bg_badge_mesh : R.drawable.bg_badge_generic
        );
        binding.btnConnectWebBridge.setText(connected ? "Test Cloud Ping" : "Connect Web Bridge");
    }

    private void bindBgService() {
        Intent intent = new Intent(requireContext(), BleMeshBackgroundService.class);
        requireContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void toggleBackgroundService(boolean enable) {
        if (enable) {
            Intent intent = new Intent(requireContext(), BleMeshBackgroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(intent);
            } else {
                requireContext().startService(intent);
            }
            bindBgService();
        } else {
            if (mIsBound) {
                requireContext().unbindService(mServiceConnection);
                mIsBound = false;
            }
            Intent intent = new Intent(requireContext(), BleMeshBackgroundService.class);
            requireContext().stopService(intent);
            mBgService = null;
            updateServiceUi();
        }
    }

    private void updateServiceUi() {
        if (binding == null) return;
        boolean isRunning = BleMeshBackgroundService.isRunning();
        binding.switchBgService.setChecked(isRunning);
        binding.tvServiceStatus.setText(isRunning ? "Running in background" : "Service stopped");
        binding.tvServiceStatus.setTextColor(
                ContextCompat.getColor(requireContext(), isRunning ? R.color.brand_emerald : R.color.text_tertiary)
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mIsBound) {
            try {
                requireContext().unbindService(mServiceConnection);
            } catch (Exception ignored) {}
            mIsBound = false;
        }
        binding = null;
    }

    // ------------------------------------------------------------------
    //  BleMeshListener Callbacks
    // ------------------------------------------------------------------

    @Override
    public void onScanResult(DiscoveredBleDevice device) {}

    @Override
    public void onScanStateChanged(boolean scanning) {}

    @Override
    public void onConnectionStateChanged(boolean connected, String status) {}

    @Override
    public void onRssiUpdated(int rssi) {}

    @Override
    public void onMtuUpdated(int mtu) {}

    @Override
    public void onPhyUpdated(int txPhy, int rxPhy) {}

    @Override
    public void onPacketTransmitted(MeshPacket packet) {
        mLogAdapter.addLog("TX Packet", "Type: " + packet.getTypeName() + " | Seq: " + packet.getSequenceNumber(), BleMeshManager.LOG_SUCCESS);
    }

    @Override
    public void onPacketReceived(MeshPacket packet) {
        mLogAdapter.addLog("RX Packet", "From: " + packet.getOriginatorAddressHex() + " | Hops: " + packet.getHopCount(), BleMeshManager.LOG_INFO);
    }

    @Override
    public void onAlertReceived(int alertId, int level, String sender, String message, String area) {
        mLogAdapter.addLog("MESH ALERT", "[" + level + "] " + message + " (Area: " + area + ")", BleMeshManager.LOG_ERROR);

        // Forward received mesh alert directly to Railway Cloud Web App
        if (mWebBridge != null && mWebBridge.isAutoForwardEnabled()) {
            mWebBridge.sendAlert(alertId, level, sender, message, area, null);
        }
    }

    @Override
    public void onLog(String tag, String message, int level) {
        if (mLogAdapter != null) {
            mLogAdapter.addLog(tag, message, level);
        }
    }

    @Override
    public void onStatisticsUpdated(int txCount, int rxCount, int relayCount) {
        if (binding == null) return;
        binding.tvStatTx.setText(String.valueOf(txCount));
        binding.tvStatRx.setText(String.valueOf(rxCount));
        binding.tvStatRelays.setText(String.valueOf(relayCount));
    }

    // ------------------------------------------------------------------
    //  WebBridgeListener Callbacks
    // ------------------------------------------------------------------

    @Override
    public void onBridgeStatusChanged(boolean connected, String message) {
        updateWebBridgeUi(connected, message);
    }

    @Override
    public void onBridgeLog(String tag, String message, int level) {
        if (mLogAdapter != null) {
            mLogAdapter.addLog(tag, message, level);
        }
    }
}
