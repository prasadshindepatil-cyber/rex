package com.rexai.controller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class RexOverlayService extends Service {

    private static final String CHANNEL_ID = "rex_service_channel";
    private static final int NOTIF_ID = 104;

    private WindowManager windowManager;
    private Button floatingButton;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean isListening = false;
    private Handler handler;

    private final HashMap<String, String> appMap = new HashMap<>();

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        buildAppMap();
        startForegroundNotification();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createFloatingButton();
        initSpeech();
    }

    private void buildAppMap() {
        appMap.put("whatsapp", "com.whatsapp");
        appMap.put("instagram", "com.instagram.android");
        appMap.put("facebook", "com.facebook.katana");
        appMap.put("youtube", "com.google.android.youtube");
        appMap.put("chrome", "com.android.chrome");
        appMap.put("gmail", "com.google.android.gm");
        appMap.put("maps", "com.google.android.apps.maps");
        appMap.put("spotify", "com.spotify.music");
        appMap.put("telegram", "org.telegram.messenger");
        appMap.put("twitter", "com.twitter.android");
        appMap.put("snapchat", "com.snapchat.android");
        appMap.put("netflix", "com.netflix.mediaclient");
        appMap.put("paytm", "net.one97.paytm");
        appMap.put("phonepe", "com.phonepe.app");
        appMap.put("truecaller", "com.truecaller");
        appMap.put("zoom", "us.zoom.videomeetings");
        appMap.put("drive", "com.google.android.apps.docs");
        appMap.put("calendar", "com.google.android.calendar");
        appMap.put("clock", "com.google.android.deskclock");
        appMap.put("calculator", "com.google.android.calculator");
        appMap.put("contacts", "com.google.android.contacts");
        appMap.put("messages", "com.google.android.apps.messaging");
        appMap.put("chatgpt", "com.openai.chatgpt");

        appMap.put("व्हाट्सएप", "com.whatsapp");
        appMap.put("इंस्टाग्राम", "com.instagram.android");
        appMap.put("फेसबुक", "com.facebook.katana");
        appMap.put("यूट्यूब", "com.google.android.youtube");
        appMap.put("युट्यूब", "com.google.android.youtube");
        appMap.put("क्रोम", "com.android.chrome");
        appMap.put("जीमेल", "com.google.android.gm");
        appMap.put("नकाशे", "com.google.android.apps.maps");
        appMap.put("पेटीएम", "net.one97.paytm");
        appMap.put("टेलीग्राम", "org.telegram.messenger");
    }

    private void createFloatingButton() {
        floatingButton = new Button(this);
        floatingButton.setText("REX");
        floatingButton.setTextColor(Color.WHITE);
        floatingButton.setBackgroundColor(Color.parseColor("#CC0000"));
        floatingButton.setPadding(30, 15, 30, 15);
        floatingButton.setTextSize(14);

        int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.x = 30;
        params.y = 200;

        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        try { windowManager.updateViewLayout(floatingButton, params); } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                        int diffX = (int) (event.getRawX() - initialTouchX);
                        int diffY = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                            triggerVoiceSearch();
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(floatingButton, params);
        } catch (Exception e) {
            Toast.makeText(this, "Overlay permission missing", Toast.LENGTH_LONG).show();
        }
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "REX AI Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("REX AI Ready")
                .setContentText("Tap REX button to give command")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void initSpeech() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
                updateButton("LISTENING...", "#00AA00");
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { updateButton("PROCESSING...", "#FFAA00"); }
            @Override public void onError(int error) { resetState(); }
            @Override
            public void onResults(Bundle results) {
                resetState();
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processSmartCommand(matches.get(0));
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void triggerVoiceSearch() {
        if (!isListening) {
            try { speechRecognizer.startListening(speechIntent); } catch (Exception e) { resetState(); }
        } else {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
            resetState();
        }
    }

    private void resetState() {
        isListening = false;
        updateButton("REX", "#CC0000");
    }

    private void updateButton(final String text, final String color) {
        handler.post(() -> {
            if (floatingButton != null) {
                floatingButton.setText(text);
                floatingButton.setBackgroundColor(Color.parseColor(color));
            }
        });
    }

    private void processSmartCommand(String text) {
        String cmd = text.toLowerCase(Locale.ROOT).trim();

        if (containsAny(cmd, "light on", "torch on", "flashlight on",
                "लाईट चालू", "लाइट चालू", "टॉर्च ऑन", "बत्ती चालू", "उजेड चालू")) {
            toggleFlashlight(true);
            showToast("REX: Torch ON");
            return;
        }

        if (containsAny(cmd, "light off", "torch off", "flashlight off",
                "लाईट बंद", "लाइट बंद", "टॉर्च ऑफ", "बत्ती बंद", "उजेड बंद")) {
            toggleFlashlight(false);
            showToast("REX: Torch OFF");
            return;
        }

        for (String key : appMap.keySet()) {
            if (cmd.contains(key)) {
                String pkg = appMap.get(key);
                if (openApp(pkg, key)) {
                    showToast("REX: " + key + " opened");
                    return;
                }
            }
        }

        if (containsAny(cmd, "camera", "कैमरा", "कॅमेरा")) {
            openSystemApp("camera");
            showToast("REX: Camera opened");
            return;
        }
        if (containsAny(cmd, "settings", "सेटिंग", "सेटिंग्स")) {
            openSystemApp("settings");
            showToast("REX: Settings opened");
            return;
        }
        if (containsAny(cmd, "gallery", "गैलरी", "फोटो", "फोटोज")) {
            openSystemApp("gallery");
            showToast("REX: Gallery opened");
            return;
        }
        if (containsAny(cmd, "dialer", "phone", "कॉल", "फोन", "डायल")) {
            Intent i = new Intent(Intent.ACTION_DIAL);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            showToast("REX: Dialer opened");
            return;
        }

        String query = cmd.replace("rex", "").replace("youtube", "")
                .replace("you tube", "").replace("search", "").replace("play", "")
                .replace("khol", "").replace("karo", "").replace("कर", "")
                .replace("खोल", "").replace("वर", "").replace("पर", "").trim();

        if (query.isEmpty()) query = text;

        String url = "https://www.youtube.com/results?search_query=" + Uri.encode(query);
        Intent yt = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        yt.setPackage("com.google.android.youtube");
        yt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(yt); }
        catch (Exception e) {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(web);
        }
        showToast("REX: YouTube -> " + query);
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) if (text.contains(k)) return true;
        return false;
    }

    private boolean openApp(String packageName, String name) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent == null) { showToast(name + " installed nahi hai"); return false; }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Exception e) { return false; }
    }

    private void openSystemApp(String type) {
        try {
            Intent i = null;
            if (type.equals("camera")) i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            else if (type.equals("settings")) i = new Intent(Settings.ACTION_SETTINGS);
            else if (type.equals("gallery")) { i = new Intent(Intent.ACTION_VIEW); i.setType("image/*"); }
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
        } catch (Exception ignored) {}
    }

    private void toggleFlashlight(boolean status) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                if (cm == null) return;
                for (String id : cm.getCameraIdList()) {
                    Boolean hasFlash = cm.getCameraCharacteristics(id)
                            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (hasFlash != null && hasFlash) { cm.setTorchMode(id, status); return; }
                }
            } catch (Exception ignored) {}
        }
    }

    private void showToast(final String msg) {
        handler.post(() -> Toast.makeText(RexOverlayService.this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { if (floatingButton != null && windowManager != null) windowManager.removeView(floatingButton); } catch (Exception ignored) {}
        if (speechRecognizer != null) { try { speechRecognizer.destroy(); } catch (Exception ignored) {} }
    }
}