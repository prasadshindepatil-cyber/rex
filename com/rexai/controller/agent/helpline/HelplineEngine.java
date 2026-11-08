package com.rexai.controller.agent.helpline;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HelplineEngine {
    private static final String TAG = "HelplineEngine";
    private Context context;
    private HelplineHistoryManager historyManager;
    private HelplineDiagnosisAgent diagnosisAgent;

    public HelplineEngine(Context context) {
        this.context = context;
        this.historyManager = new HelplineHistoryManager(context);
        this.diagnosisAgent = new HelplineDiagnosisAgent(context);
    }

    public static class ErrorReport {
        public String category;
        public String errorMessage;
        public String probableCause;
        public String confidence;
        public String recommendedSolution;
        public List<String> affectedFiles;
        public String riskLevel;
        public boolean requiresRebuild;

        public ErrorReport(String category, String errorMessage, String probableCause) {
            this.category = category;
            this.errorMessage = errorMessage;
            this.probableCause = probableCause;
            this.confidence = "High";
            this.affectedFiles = new ArrayList<>();
            this.riskLevel = "Low";
            this.requiresRebuild = false;
        }
    }

    public ErrorReport analyzeError(String rawError) {
        Log.d(TAG, "Analyzing error: " + rawError);
        ErrorReport report;

        if (rawError != null && (rawError.contains("Read timed out") || rawError.contains("timeout"))) {
            report = new ErrorReport("API_TIMEOUT", rawError, "Network connection or Gemini API read timeout (exceeded limit).");
            report.recommendedSolution = "Increase API read timeout or retry the request with exponential backoff.";
            report.riskLevel = "Low";
        } else if (rawError != null && (rawError.contains("API_KEY") || rawError.contains("401") || rawError.contains("403"))) {
            report = new ErrorReport("AUTH_ERROR", rawError, "Invalid API key or unauthorized access.");
            report.recommendedSolution = "Check and update the API key in settings.";
            report.riskLevel = "Medium";
        } else {
            report = new ErrorReport("GENERAL_ERROR", rawError, "Unknown exception or runtime error.");
            report.recommendedSolution = "Check logs and apply recommended patch if available.";
            report.riskLevel = "Medium";
        }

        historyManager.saveError(report);
        return report;
    }
}
