package com.inevitables.blehelper.mesh;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class BleDiagnosticsHelper {

    public static class HardwareInfo {
        public boolean isBluetoothSupported;
        public boolean isBluetoothEnabled;
        public boolean isBleSupported;
        public boolean isLe2MPhySupported;
        public boolean isLeCodedPhySupported;
        public boolean isExtendedAdvSupported;
        public boolean isPeriodicAdvSupported;
        public boolean isMultipleAdvSupported;
        public boolean isOffloadedFilteringSupported;
        public boolean isOffloadedBatchingSupported;
        public int maxAdvDataLength;
    }

    public static class PermissionInfo {
        public boolean isScanGranted;
        public boolean isConnectGranted;
        public boolean isAdvertiseGranted;
        public boolean isFineLocationGranted;
        public boolean isPostNotificationsGranted;
        public boolean isLocationServiceEnabled;
        public boolean isIgnoringBatteryOptimizations;
        public List<String> missingPermissions = new ArrayList<>();
    }

    public static HardwareInfo getHardwareInfo(Context context) {
        HardwareInfo info = new HardwareInfo();
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;

        info.isBluetoothSupported = adapter != null;
        info.isBluetoothEnabled = adapter != null && adapter.isEnabled();
        info.isBleSupported = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);

        if (adapter != null) {
            info.isMultipleAdvSupported = adapter.isMultipleAdvertisementSupported();
            info.isOffloadedFilteringSupported = adapter.isOffloadedFilteringSupported();
            info.isOffloadedBatchingSupported = adapter.isOffloadedScanBatchingSupported();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                info.isLe2MPhySupported = adapter.isLe2MPhySupported();
                info.isLeCodedPhySupported = adapter.isLeCodedPhySupported();
                info.isExtendedAdvSupported = adapter.isLeExtendedAdvertisingSupported();
                info.isPeriodicAdvSupported = adapter.isLePeriodicAdvertisingSupported();
                info.maxAdvDataLength = adapter.getLeMaximumAdvertisingDataLength();
            } else {
                info.isLe2MPhySupported = false;
                info.isLeCodedPhySupported = false;
                info.isExtendedAdvSupported = false;
                info.isPeriodicAdvSupported = false;
                info.maxAdvDataLength = 31;
            }
        }

        return info;
    }

    public static PermissionInfo checkPermissions(Context context) {
        PermissionInfo info = new PermissionInfo();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.isScanGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            info.isConnectGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            info.isAdvertiseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;

            if (!info.isScanGranted) info.missingPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
            if (!info.isConnectGranted) info.missingPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (!info.isAdvertiseGranted) info.missingPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        } else {
            info.isScanGranted = true;
            info.isConnectGranted = true;
            info.isAdvertiseGranted = true;
        }

        info.isFineLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!info.isFineLocationGranted) {
            info.missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            info.isPostNotificationsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            if (!info.isPostNotificationsGranted) {
                info.missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            info.isPostNotificationsGranted = true;
        }

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            info.isLocationServiceEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            info.isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }

        return info;
    }

    public static String[] getRequiredPermissionsArray() {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return list.toArray(new String[0]);
    }
}
