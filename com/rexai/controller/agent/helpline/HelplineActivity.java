package com.rexai.controller.agent.helpline;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import java.util.List;
// Note: Actual R.id resources will be bound or dynamically created if layout is missing.

public class HelplineActivity extends Activity {
    private HelplineEngine helplineEngine;
    private HelplineHistoryManager historyManager;
    private TextView statusTextView;
    private ListView historyListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Creating UI programmatically for safety and zero XML dependency issues
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("🛠️ REX HELPLINE AGENT");
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        statusTextView = new TextView(this);
        statusTextView.setText("System Health: Operational\nReady to diagnose errors.");
        statusTextView.setPadding(0, 24, 0, 24);
        layout.addView(statusTextView);

        Button diagnoseButton = new Button(this);
        diagnoseButton.setText("Run Self-Diagnosis");
        diagnoseButton.setOnClickListener(v -> {
            HelplineEngine.ErrorReport report = helplineEngine.analyzeError("HTTPSConnectionPool: Read timed out.");
            statusTextView.setText("Diagnosis: " + report.category + "\nCause: " + report.probableCause + "\nSolution: " + report.recommendedSolution);
            loadHistory();
        });
        layout.addView(diagnoseButton);

        historyListView = new ListView(this);
        layout.addView(historyListView);

        setContentView(layout);

        helplineEngine = new HelplineEngine(this);
        historyManager = new HelplineHistoryManager(this);
        loadHistory();
    }

    private void loadHistory() {
        List<String> history = historyManager.getHistorySummary();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, history);
        historyListView.setAdapter(adapter);
    }
}
