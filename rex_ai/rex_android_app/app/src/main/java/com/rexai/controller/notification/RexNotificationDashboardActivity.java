package com.rexai.controller.notification;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.rexai.controller.R;
import java.util.ArrayList;
import java.util.List;

public class RexNotificationDashboardActivity extends Activity {
    private ListView listView;
    private TextView tvStatus;
    private Button btnRefresh, btnSummarize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Dynamic lightweight layout creation
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(0xFF0F172A); // Dark theme

        TextView title = new TextView(this);
        title.setText("Rex Notification Intelligence");
        title.setTextSize(22);
        title.setTextColor(0xFF38BDF8);
        title.setPadding(0, 0, 0, 24);
        root.addView(title);

        tvStatus = new TextView(this);
        tvStatus.setText("Dashboard active. Recent secured notifications:");
        tvStatus.setTextColor(0xFF94A3B8);
        tvStatus.setPadding(0, 0, 0, 16);
        root.addView(tvStatus);

        listView = new ListView(this);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listView.setLayoutParams(lp);
        root.addView(listView);

        android.widget.LinearLayout btnLayout = new android.widget.LinearLayout(this);
        btnLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        btnLayout.setPadding(0, 24, 0, 0);

        btnRefresh = new Button(this);
        btnRefresh.setText("Refresh");
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadNotifications();
                Toast.loadTextOrCustom(RexNotificationDashboardActivity.this, "Refreshed", Toast.LENGTH_SHORT).show(); // handled safely
            }
        });
        btnLayout.addView(btnRefresh);

        root.addView(btnLayout);
        setContentView(root);

        loadNotifications();
    }

    private void loadNotifications() {
        List<RexNotificationManager.NotificationItem> list = RexNotificationManager.getRecentNotifications();
        List<String> displayList = new ArrayList<>();
        for (RexNotificationManager.NotificationItem item : list) {
            displayList.add("[" + item.category + "] " + item.title + "\n" + item.text);
        }
        if (displayList.isEmpty()) {
            displayList.add("No recent notifications captured or service not enabled.");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        listView.setAdapter(adapter);
    }
}
