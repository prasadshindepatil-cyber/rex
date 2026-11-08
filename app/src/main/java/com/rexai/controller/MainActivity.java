package com.rexai.controller;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 100;
    private static final int REQ_PERMS = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 120, 60, 60);

        TextView title = new TextView(this);
        title.setText("REX AI");
        title.setTextSize(28);
        layout.addView(title);

        Button permBtn = new Button(this);
        permBtn.setText("1. GRANT PERMISSIONS");
        permBtn.setOnClickListener(v -> requestAllPermissions());
        layout.addView(permBtn);

        Button overlayBtn = new Button(this);
        overlayBtn.setText("2. ALLOW OVERLAY");
        overlayBtn.setOnClickListener(v -> requestOverlayPermission());
        layout.addView(overlayBtn);

        Button startBtn = new Button(this);
        startBtn.setText("3. START REX");
        startBtn.setOnClickListener(v -> startRexService());
        layout.addView(startBtn);

        setContentView(layout);
    }

    private void requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            ArrayList<String> perms = new ArrayList<>();
            perms.add(Manifest.permission.RECORD_AUDIO);
            if (Build.VERSION.SDK_INT >= 33) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            requestPermissions(perms.toArray(new String[0]), REQ_PERMS);
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_OVERLAY);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            Toast.makeText(this, Settings.canDrawOverlays(this) ? "Overlay granted" : "Overlay denied", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Toast.makeText(this, "Permissions updated", Toast.LENGTH_SHORT).show();
    }

    private void startRexService() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Grant RECORD_AUDIO first!", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant OVERLAY first!", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, RexOverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
        Toast.makeText(this, "REX started!", Toast.LENGTH_LONG).show();
    }
}