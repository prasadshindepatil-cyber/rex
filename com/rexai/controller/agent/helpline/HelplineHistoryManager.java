package com.rexai.controller.agent.helpline;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class HelplineHistoryManager {
    private static final String PREF_NAME = "rex_helpline_history";
    private static final String KEY_HISTORY = "history_list";
    private SharedPreferences prefs;

    public HelplineHistoryManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveError(HelplineEngine.ErrorReport report) {
        try {
            String existing = prefs.getString(KEY_HISTORY, "[]");
            JSONArray array = new JSONArray(existing);

            JSONObject obj = new JSONObject();
            obj.put("category", report.category);
            obj.put("message", report.errorMessage);
            obj.put("cause", report.probableCause);
            obj.put("solution", report.recommendedSolution);
            obj.put("timestamp", System.currentTimeMillis());

            array.put(obj);
            prefs.edit().putString(KEY_HISTORY, array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> getHistorySummary() {
        List<String> list = new ArrayList<>();
        try {
            String existing = prefs.getString(KEY_HISTORY, "[]");
            JSONArray array = new JSONArray(existing);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add("[" + obj.getString("category") + "] " + obj.getString("message"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
