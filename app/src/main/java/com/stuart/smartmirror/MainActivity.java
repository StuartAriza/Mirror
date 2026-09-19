package com.stuart.smartmirror;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        View controls = findViewById(R.id.controls_button);
        controls.setOnClickListener(view -> Toast.makeText(
                this,
                R.string.hold_for_controls,
                Toast.LENGTH_SHORT).show());
        controls.setOnLongClickListener(view -> {
            showRecoveryControls();
            return true;
        });
    }

    @Override
    public void onBackPressed() {
        showRecoveryControls();
    }

    private void showRecoveryControls() {
        final String[] actions = {
                getString(R.string.open_android_settings),
                getString(R.string.enable_navigation_strip),
                getString(R.string.exit_to_home),
                getString(R.string.stay_in_mirror)
        };

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(R.string.recovery_controls)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            launchSettings(Settings.ACTION_SETTINGS);
                            break;
                        case 1:
                            launchSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                            break;
                        case 2:
                            exitToHome();
                            break;
                        default:
                            dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void launchSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (ActivityNotFoundException exception) {
            if (!Settings.ACTION_SETTINGS.equals(action)) {
                launchSettings(Settings.ACTION_SETTINGS);
            } else {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void exitToHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }
}

