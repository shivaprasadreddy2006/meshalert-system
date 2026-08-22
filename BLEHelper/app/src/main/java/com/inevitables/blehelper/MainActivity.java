package com.inevitables.blehelper;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.inevitables.blehelper.databinding.ActivityMainBinding;
import com.inevitables.blehelper.mesh.BleDiagnosticsHelper;
import com.inevitables.blehelper.mesh.BleMeshManager;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    private final ActivityResultLauncher<String[]> mPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionsResult);

    private final ActivityResultLauncher<Intent> mEnableBtLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // Fragment will refresh hardware state
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // Hide or configure FAB for quick actions
        binding.fab.hide();

        // Initial permission check
        checkAndPromptPermissions();
    }

    public void requestBlePermissions() {
        String[] required = BleDiagnosticsHelper.getRequiredPermissionsArray();
        mPermissionLauncher.launch(required);
    }

    public void requestEnableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        try {
            mEnableBtLauncher.launch(enableBtIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Bluetooth settings", Toast.LENGTH_SHORT).show();
        }
    }

    public void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            }
        }
    }

    private void checkAndPromptPermissions() {
        BleDiagnosticsHelper.PermissionInfo permInfo = BleDiagnosticsHelper.checkPermissions(this);
        if (!permInfo.missingPermissions.isEmpty()) {
            requestBlePermissions();
        }
    }

    private void onPermissionsResult(Map<String, Boolean> results) {
        boolean allGranted = true;
        for (Boolean granted : results.values()) {
            if (granted == null || !granted) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            Toast.makeText(this, "Some BLE permissions were denied. Scanning and background operation may be limited.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("About BLE Mesh Helper")
                .setMessage("A high-performance Android tool for connecting to Bluetooth SIG Mesh Proxy networks, maintaining background connectivity, and diagnosing real-time BLE link metrics.")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}