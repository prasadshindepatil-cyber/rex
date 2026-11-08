package com.rexai.controller.agent.vision;

import android.graphics.Rect;
import android.util.Log;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

public class RexOCRReader {
    private static final String TAG = "RexOCRReader";
    private final TextRecognizer recognizer;

    public static class OCRResultItem {
        public String text;
        public Rect boundingBox;

        public OCRResultItem(String text, Rect boundingBox) {
            this.text = text;
            this.boundingBox = boundingBox;
        }
    }

    public interface OCRCallback {
        void onSuccess(List<OCRResultItem> items);
        void onError(Exception e);
    }

    public RexOCRReader() {
        // Supporting Devanagari (Marathi, Hindi) and Latin (English)
        recognizer = TextRecognition.getClient(
            new DevanagariTextRecognizerOptions.Builder().build()
        );
    }

    public void recognizeImage(android.graphics.Bitmap bitmap, OCRCallback callback) {
        if (bitmap == null) {
            if (callback != null) callback.onError(new IllegalArgumentException("Bitmap is null"));
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(image)
            .addOnSuccessListener(visionText -> {
                List<OCRResultItem> results = new ArrayList<>();
                for (Text.TextBlock block : visionText.getTextBlocks()) {
                    for (Text.Line line : block.getLines()) {
                        results.add(new OCRResultItem(line.getText(), line.getBoundingBox()));
                    }
                }
                if (callback != null) callback.onSuccess(results);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "OCR Recognition failed", e);
                if (callback != null) callback.onError(e);
            });
    }

    public void close() {
        try {
            recognizer.close();
        } catch (Exception ignored) {}
    }
}
