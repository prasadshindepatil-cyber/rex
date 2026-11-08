package com.rexai.controller;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageAnalysisEngine {
    private static final String TAG = "ImageAnalysisEngine";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AnalysisCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public void analyzeImage(Context context, Bitmap bitmap, String prompt, AnalysisCallback callback) {
        if (bitmap == null) {
            if (callback != null) callback.onError("Image bitmap is null");
            return;
        }

        String apiKey = RexKeys.getGeminiKey(context);
        if (apiKey == null || apiKey.isEmpty()) {
            if (callback != null) callback.onError("Gemini API Key missing. Please configure in settings.");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                byte[] imageBytes = baos.toByteArray();
                String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);

                JSONObject req = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");

                JSONArray parts = new JSONArray();
                JSONObject textPart = new JSONObject();
                textPart.put("text", prompt != null && !prompt.isEmpty() ? prompt : "Describe this image in detail and read any visible text in Marathi.");
                parts.put(textPart);

                JSONObject imagePart = new JSONObject();
                JSONObject inlineData = new JSONObject();
                inlineData.put("mimeType", "image/jpeg");
                inlineData.put("data", base64Image);
                imagePart.put("inlineData", inlineData);
                parts.put(imagePart);

                userMsg.put("parts", parts);
                contents.put(userMsg);
                req.put("contents", contents);

                byte[] bodyBytes = req.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                }

                int respCode = conn.getResponseCode();
                InputStream in = (respCode >= 200 && respCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                String responseStr = readStream(in);

                if (respCode < 200 || respCode >= 300) {
                    notifyError(callback, "Gemini Vision API error (" + respCode + "): " + responseStr);
                    return;
                }

                String analysisResult = extractText(new JSONObject(responseStr));
                if (analysisResult.isEmpty()) {
                    notifyError(callback, "Gemini returned empty analysis.");
                } else {
                    mainHandler.post(() -> callback.onSuccess(analysisResult));
                }

            } catch (Exception e) {
                Log.e(TAG, "Image analysis error", e);
                notifyError(callback, "Error analyzing image: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void notifyError(AnalysisCallback cb, String error) {
        if (cb != null) {
            mainHandler.post(() -> cb.onError(error));
        }
    }

    private String readStream(InputStream in) throws Exception {
        if (in == null) return "";
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) b.append(line);
        r.close();
        return b.toString();
    }

    private String extractText(JSONObject j) {
        StringBuilder out = new StringBuilder();
        JSONArray cs = j.optJSONArray("candidates");
        if (cs == null || cs.length() == 0) return "";
        JSONObject content = cs.optJSONObject(0);
        if (content == null) return "";
        content = content.optJSONObject("content");
        if (content == null) return "";
        JSONArray ps = content.optJSONArray("parts");
        if (ps == null) return "";
        for (int i = 0; i < ps.length(); i++) {
            JSONObject p = ps.optJSONObject(i);
            if (p != null) {
                String t = p.optString("text", "");
                if (!t.isEmpty()) {
                    if (out.length() > 0) out.append("\n");
                    out.append(t);
                }
            }
        }
        return out.toString().trim();
    }
}
