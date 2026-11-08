package com.rexai.controller.agent.vision;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.util.Log;
import java.util.List;

public class RexAutonomousVisionEngine {
    private static final String TAG = "RexAutonomousVisionEngine";
    private final RexOCRReader ocrReader;
    private final RexScreenActionEngine actionEngine;
    private final RexActionVerifier actionVerifier;

    public RexAutonomousVisionEngine(AccessibilityService service) {
        this.ocrReader = new RexOCRReader();
        this.actionEngine = new RexScreenActionEngine(service);
        this.actionVerifier = new RexActionVerifier();
    }

    public void readScreenText(Bitmap screenBitmap, RexOCRReader.OCRCallback callback) {
        ocrReader.recognizeImage(screenBitmap, callback);
    }

    public void executeCommand(String command, Bitmap currentBitmap, RexActionVerifier.VerificationCallback callback) {
        String lower = command.toLowerCase();
        Log.i(TAG, "Executing autonomous command: " + command);

        if (lower.contains("back")) {
            boolean success = actionEngine.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            actionVerifier.verifyAction("Global Back", 500, callback);
            return;
        }

        if (lower.contains("home")) {
            boolean success = actionEngine.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
            actionVerifier.verifyAction("Global Home", 500, callback);
            return;
        }

        // Search text / click target
        if (lower.contains("click") || lower.contains("shodh") || lower.contains("open")) {
            // Extract potential target from command or use OCR
            ocrReader.recognizeImage(currentBitmap, new RexOCRReader.OCRCallback() {
                @Override
                public void onSuccess(List<RexOCRReader.OCRResultItem> items) {
                    boolean found = false;
                    for (RexOCRReader.OCRResultItem item : items) {
                        for (String word : command.split("\\s+")) {
                            if (word.length() > 3 && item.text.toLowerCase().contains(word.toLowerCase())) {
                                if (item.boundingBox != null) {
                                    int centerX = item.boundingBox.centerX();
                                    int centerY = item.boundingBox.centerY();
                                    actionEngine.clickByCoordinates(centerX, centerY);
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (found) break;
                    }
                    if (!found) {
                        // Fallback to Accessibility Node click
                        for (String word : command.split("\\s+")) {
                            if (word.length() > 3) {
                                if (actionEngine.clickByText(word)) {
                                    found = true;
                                    break;
                                }
                            }
                        }
                    }
                    actionVerifier.verifyAction(command, 800, callback);
                }

                @Override
                public void onError(Exception e) {
                    if (callback != null) callback.onVerified(false, "OCR Error: " + e.getMessage());
                }
            });
        } else {
            if (callback != null) callback.onVerified(false, "Command not recognized by Vision Engine");
        }
    }

    public void shutdown() {
        ocrReader.close();
    }
}
