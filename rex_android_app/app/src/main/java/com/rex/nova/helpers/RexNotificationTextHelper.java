package com.rex.nova.helpers;

public class RexNotificationTextHelper {
    public static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\r\\n]+", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
}
