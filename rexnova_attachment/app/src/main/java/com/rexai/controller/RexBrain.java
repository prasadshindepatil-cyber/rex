package com.rexai.controller;

import android.content.*;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.*;
import android.provider.AlarmClock;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class RexBrain {
    public interface ProgressListener {
        void onProgress(String text);
    }

    private static volatile ProgressListener progressListener;

    public static void setProgressListener(ProgressListener listener) {
        progressListener = listener;
    }

    private static void progress(String text) {
        ProgressListener listener = progressListener;
        if (listener != null && text != null) {
            listener.onProgress(text);
        }
    }
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String[] MODELS = {"gemini-3.8-flash", "gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.5-flash-lite"};
    private final Context ctx;
    private final RexAgent agent;
    private final RexMemory mem;
    private final SkillEngine skillEngine;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final JSONArray tools = new JSONArray();
    private final JSONArray hist = new JSONArray();

    // Task State Manager: preserve the active task across clarification turns.
    private static final class TaskState {
        String currentTask = "";
        String lastQuestion = "";
        String status = "IDLE";
        long updatedAt = 0L;

        synchronized void begin(String task) {
            currentTask = task == null ? "" : task.trim();
            lastQuestion = "";
            status = currentTask.isEmpty() ? "IDLE" : "ACTIVE";
            updatedAt = System.currentTimeMillis();
        }

        synchronized void waitingFor(String question) {
            lastQuestion = question == null ? "" : question.trim();
            status = lastQuestion.isEmpty() ? "ACTIVE" : "WAITING_USER";
            updatedAt = System.currentTimeMillis();
        }

        synchronized String snapshot() {
            return "status=" + status + "; task=" + currentTask
                    + "; lastQuestion=" + lastQuestion;
        }

        synchronized String prepare(String text) {
            String clean = text == null ? "" : text.trim();
            if ("WAITING_USER".equals(status) && !currentTask.isEmpty()) {
                String combined = "Continue the previous task. Previous task: "
                        + currentTask + ". Last clarification question: "
                        + lastQuestion + ". User's answer: " + clean
                        + ". Use this context, do not repeat the same question, "
                        + "and continue the task if the needed details are now available.";
                currentTask = currentTask + " | User clarification: " + clean;
                lastQuestion = "";
                status = "ACTIVE";
                updatedAt = System.currentTimeMillis();
                return combined;
            }
            begin(clean);
            return clean;
        }

        synchronized void finish(String reply) {
            String r = reply == null ? "" : reply.trim();
            String low = r.toLowerCase(java.util.Locale.ROOT);
            boolean question = r.contains("?") && (
                    low.contains("which") || low.contains("what should")
                    || low.contains("who") || low.contains("number")
                    || low.contains("sms") || low.contains("whatsapp")
                    || r.contains("काय लिहू") || r.contains("कोणता")
                    || r.contains("कोणाला") || r.contains("कुठला")
                    || r.contains("कोणते"));
            if (question) waitingFor(r);
            else if ("ACTIVE".equals(status)) {
                status = "COMPLETE";
                updatedAt = System.currentTimeMillis();
            }
        }
    }

    private final TaskState taskState = new TaskState();
    private volatile String key = "";

    public RexBrain(Context c, RexAgent a) {
        ctx = c.getApplicationContext();
        agent = a;
        mem = new RexMemory(ctx);
        skillEngine = new SkillEngine(ctx);
        try {
            JSONArray d = new JSONArray();
            d.put(fn("open_app", "Open an installed app by name", "name:STRING:app name e.g. YouTube"));
            d.put(fn("search_contact", "Search phone contacts by a person name; return matching names and phone numbers, never guess", "name:STRING:person name"));
            d.put(fn("web_search", "Google search", "query:STRING:search words"));
            d.put(fn("youtube_search", "Search YouTube", "query:STRING:search words"));
            d.put(fn("open_url", "Open a link", "url:STRING:full URL"));
            d.put(fn("navigate", "Open Google Maps for a place", "place:STRING:place name"));
            d.put(fn("set_alarm", "Set an alarm", "hour:INTEGER:0-23", "minute:INTEGER:0-59", "label:STRING:label"));
            d.put(fn("set_timer", "Set a countdown timer", "seconds:INTEGER:seconds", "label:STRING:label"));
            d.put(fn("call_number", "Open dialer with number (user presses call)", "number:STRING:phone number"));
            d.put(fn("send_sms", "Open SMS app prefilled (user presses send)", "number:STRING:phone number", "message:STRING:text"));
            d.put(fn("whatsapp_message", "Open WhatsApp chat prefilled (user presses send)", "number:STRING:number with country code", "message:STRING:text"));
            d.put(fn("whatsapp_open", "Open a WhatsApp chat by phone number without message text; never send a message", "number:STRING:phone number including country code"));
            d.put(fn("battery", "Get battery level"));
            d.put(fn("weather", "Weather of a city", "city:STRING:city"));
            d.put(fn("torch", "Flashlight on or off", "on:BOOLEAN:true to turn on"));
            d.put(fn("remember", "Save a note to memory", "text:STRING:what to remember"));
            d.put(fn("screen_task", "Do a multi-step task by tapping/typing inside apps using screen control", "task:STRING:clear task description"));
            tools.put(new JSONObject().put("functionDeclarations", d));
        } catch (Exception ignored) {}
    }

    private JSONObject fn(String name, String desc, String... params) throws JSONException {
        JSONObject f = new JSONObject().put("name", name).put("description", desc);
        if (params.length > 0) {
            JSONObject props = new JSONObject(); JSONArray req = new JSONArray();
            for (String p : params) {
                String[] x = p.split(":", 3);
                props.put(x[0], new JSONObject().put("type", x[1]).put("description", x[2]));
                req.put(x[0]);
            }
            f.put("parameters", new JSONObject().put("type", "OBJECT").put("properties", props).put("required", req));
        }
        return f;
    }

    private JSONObject turn(String role, JSONArray parts) throws JSONException {
        return new JSONObject().put("role", role).put("parts", parts);
    }

    private String localBasicTool(String text) {
        if (text == null) return null;

        String t = text.toLowerCase(java.util.Locale.ROOT).trim();

        String[] rememberPrefixes = {"rex, lakshat thev", "lakshat thev", "हे लक्षात ठेव", "he lakshat thev", "remember that", "remember"};
        String[] forgetPrefixes = {"rex, visrun ja", "visrun ja", "विसरून जा", "हे विसर", "forget that", "forget"};
        for (String prefix : rememberPrefixes) {
            if (t.startsWith(prefix)) {
                String fact = text.trim().substring(prefix.length()).replaceFirst("^[\\s,:-]+", "").trim();
                if (fact.isEmpty()) return "Kay lakshat thevayche te pan sang.";
                mem.add(fact);
                return "🧠 Memory madhye save kele: " + fact;
            }
        }
        for (String prefix : forgetPrefixes) {
            if (t.startsWith(prefix)) {
                String fact = text.trim().substring(prefix.length()).replaceFirst("^[\\s,:-]+", "").trim();
                if (fact.isEmpty()) return "Konti memory visaraychi te spashta sang.";
                return mem.remove(fact) ? "Memory madhun delete kele: " + fact : "Hi exact memory sapadli nahi.";
            }
        }

        try {
            org.json.JSONObject a = new org.json.JSONObject();

            // Local-first common app and search commands; no Gemini call needed.
            if (t.matches(".*(open|launch|start|उघड|चालू कर).*whatsapp.*") ||
                t.matches(".*(whatsapp|व्हॉट्सअॅप|व्हाट्सअॅप).*(open|launch|start|उघड|चालू कर).*")) {
                Intent wi = ctx.getPackageManager().getLaunchIntentForPackage("com.whatsapp");
                if (wi == null) wi = RexApps.find(ctx, "WhatsApp");
                if (wi != null) return go(wi).equals("opened ok")
                        ? "Local Tool → WhatsApp Open" : "WhatsApp उघडता आले नाही.";
            }

            String query = null;
            String[] googlePrefixes = {"search google for ", "google search ", "search for ",
                    "शोध ", "गुगलवर शोध ", "गूगलवर शोध "};
            for (String prefix : googlePrefixes) {
                int at = t.indexOf(prefix);
                if (at >= 0) {
                    query = text.substring(at + prefix.length()).trim();
                    break;
                }
            }
            if (query != null && !query.isEmpty()) {
                return go(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=" + enc(query)))).equals("opened ok")
                        ? "Local Tool → Google Search" : "Search उघडता आले नाही.";
            }

            String[] ytPrefixes = {"search youtube for ", "youtube search ",
                    "यूट्यूबवर शोध ", "युट्यूबवर शोध "};
            for (String prefix : ytPrefixes) {
                int at = t.indexOf(prefix);
                if (at >= 0) {
                    query = text.substring(at + prefix.length()).trim();
                    break;
                }
            }
            if (query != null && !query.isEmpty()) {
                return go(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=" + enc(query)))).equals("opened ok")
                        ? "Local Tool → YouTube Search" : "YouTube Search उघडता आले नाही.";
            }

            if (t.matches(".*\\b(back|go back|मागे|मागे जा|परत)\\b.*")) {
                a.put("action", "back");
                if (RexAccessibilityService.performAgentAction(a))
                    return "Local Tool → Back";
            }

            if (t.matches(".*\\b(home|home screen|होम|होम स्क्रीन)\\b.*")) {
                a.put("action", "home");
                if (RexAccessibilityService.performAgentAction(a))
                    return "Local Tool → Home";
            }

            String[] clickWords = {
                    "click ", "tap ", "press ",
                    "क्लिक ", "टॅप ", "दाब "
            };

            for (String w : clickWords) {
                int pos = t.indexOf(w);
                if (pos >= 0) {
                    String target = text.substring(pos + w.length()).trim();
                    if (!target.isEmpty()) {
                        a.put("action", "click_text");
                        a.put("text", target);

                        if (RexAccessibilityService.performAgentAction(a))
                            return "Local Tool → Click " + target;
                    }
                }
            }

            String[] typeWords = {
                    "type ", "write ", "enter ",
                    "टाइप ", "टाईप ", "लिही ", "लिहा ",
                    "मजकूर लिही "
            };

            for (String w : typeWords) {
                int pos = t.indexOf(w);
                if (pos >= 0) {
                    String value = text.substring(pos + w.length()).trim();
                    if (!value.isEmpty()) {
                        a.put("action", "type");
                        a.put("text", value);

                        if (RexAccessibilityService.performAgentAction(a))
                            return "Local Tool → Type";
                    }
                }
            }

            String[] swipeLeft = {"swipe left", "डावीकडे स्वाइप", "डावीकडे swipe"};
            String[] swipeRight = {"swipe right", "उजवीकडे स्वाइप", "उजवीकडे swipe"};

            for (String w : swipeLeft) {
                if (t.contains(w)) {
                    a.put("action", "swipe");
                    a.put("x1", 850);
                    a.put("y1", 900);
                    a.put("x2", 150);
                    a.put("y2", 900);
                    a.put("duration", 400);
                    if (RexAccessibilityService.performAgentAction(a))
                        return "Local Tool → Swipe Left";
                }
            }

            for (String w : swipeRight) {
                if (t.contains(w)) {
                    a.put("action", "swipe");
                    a.put("x1", 150);
                    a.put("y1", 900);
                    a.put("x2", 850);
                    a.put("y2", 900);
                    a.put("duration", 400);
                    if (RexAccessibilityService.performAgentAction(a))
                        return "Local Tool → Swipe Right";
                }
            }

            if (t.contains("scroll") || t.contains("स्क्रोल") ||
                t.contains("खाली") || t.contains("वर")) {

                boolean backward =
                        t.contains("up") ||
                        t.contains("वर") ||
                        t.contains("backward");

                a.put("action", backward ? "scroll_backward" : "scroll_forward");

                if (RexAccessibilityService.performAgentAction(a))
                    return backward
                            ? "Local Tool → Scroll Up"
                            : "Local Tool → Scroll Down";
            }

        } catch (Exception ignored) {}

        return null;
    }

    public void ask(String apiKey, String text, RexGemini.Callback cb) {
        key = apiKey == null ? "" : apiKey.trim();
        final String effectiveText = taskState.prepare(text);

        final String taskProvider = ctx.getSharedPreferences("rex_settings", Context.MODE_PRIVATE)
                .getString("selected_api", "gemini");
        ex.execute(() -> {
            try {
                String provider = taskProvider;
                if (key.isEmpty()) throw new Exception(provider.toUpperCase() + " API key सेट केलेली नाही.");

                if (!"gemini".equals(provider)) {
                    String selectedModel;
                    switch (provider) {
                        case "groq": selectedModel = "openai/gpt-oss-120b"; break;
                        case "openai": selectedModel = "gpt-4o-mini"; break;
                        case "cerebras": selectedModel = "gpt-oss-120b"; break;
                        case "openrouter": selectedModel = "openai/gpt-oss-120b"; break;
                        default: selectedModel = "unknown";
                    }
                    progress("🌐 Selected API: " + provider.toUpperCase() + " | Model: " + selectedModel);
                    String reply = callCompatible(provider, effectiveText);
                    taskState.finish(reply);
                    main.post(() -> cb.onSuccess(reply));
                    return;
                }

                JSONArray userParts = new JSONArray().put(new JSONObject().put("text", effectiveText));
                JSONArray msgs = new JSONArray();
                for (int i = 0; i < hist.length(); i++) msgs.put(hist.get(i));
                msgs.put(turn("user", userParts));
                String out = "";
                for (int step = 0; step < 8; step++) {
                    JSONObject r = call(msgs, effectiveText);
                    JSONArray cands = r.optJSONArray("candidates");
                    JSONObject cont = (cands != null && cands.length() > 0) ? cands.getJSONObject(0).optJSONObject("content") : null;
                    JSONArray parts = cont != null ? cont.optJSONArray("parts") : null;
                    if (parts == null) parts = new JSONArray();
                    JSONArray resp = new JSONArray();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject p = parts.getJSONObject(i);
                        if (p.has("functionCall")) {
                            JSONObject fc = p.getJSONObject("functionCall");
                            JSONObject args = fc.optJSONObject("args");
                            String res = exec(fc.getString("name"), args == null ? new JSONObject() : args);
                            resp.put(new JSONObject().put("functionResponse", new JSONObject()
                                    .put("name", fc.getString("name")).put("response", new JSONObject().put("result", res))));
                        } else if (!p.optBoolean("thought", false)) sb.append(p.optString("text", ""));
                    }
                    if (resp.length() == 0) { out = sb.toString().trim(); if (out.isEmpty()) out = "काम पूर्ण झाले."; break; }
                    msgs.put(turn("model", parts));
                    msgs.put(turn("user", resp));
                }
                if (out.isEmpty()) out = "खूप steps झाले, थांबलो.";
                hist.put(turn("user", userParts));
                hist.put(turn("model", new JSONArray().put(new JSONObject().put("text", out))));
                while (hist.length() > 16) hist.remove(0);
                final String f = out;
                taskState.finish(f);
                main.post(() -> cb.onSuccess(f));
            } catch (Exception e) {
                final String m = e.getMessage() == null ? "Brain error" : e.getMessage();
                main.post(() -> cb.onError(m));
            }
        });
    }

    private String system(String task) {
        String savedSkill = skillEngine.findSkill(task);

        String skillContext = savedSkill == null
                ? "\nNo saved skill matches this task."
                : "\nSaved skill that may help with this task:\n" + savedSkill;

        return "You are Rex, King Raj's personal AI assistant living on his Android phone. Reply in the user's language "
            + "(Marathi/Hindi/English). Be friendly, warm, natural and helpful, like King Raj's personal AI companion. "
            + "Understand the user's intent and conversation context before answering. Answer the actual question first, in a complete and useful way; normally use 2-5 sentences, and explain more when asked. "
            + "Avoid unnecessary clarification questions. If the request is clear, act directly; ask only when essential details are missing, the contact is ambiguous, or a wrong action could cause harm. "
            + "For follow-up questions such as 'what is the question?' or 'what do you mean?', interpret the previous conversation instead of treating the words in isolation. "
            + "You can act on the phone with tools. Use a tool whenever the user wants an action, otherwise answer conversationally. "
            + "ROUTING RULES: Never use Google web_search unless the user explicitly asks for Google or a general web search. "
            + "If the user explicitly asks to search YouTube, use youtube_search, not web_search. "
            + "For WhatsApp contact tasks, first call search_contact with the person name. If multiple contacts match, ask the user to choose; never guess. After a unique contact is identified, call whatsapp_open with the actual contact number. If message text is missing, ask what to write after opening the chat. If text is supplied, prepare it for review and never send without explicit confirmation. Verify actions and report failures honestly. "
            + "If screen_task cannot complete the WhatsApp flow and the correct phone number is already known, use whatsapp_message as a fallback. Never invent or guess a person's phone number. "
            + "For WhatsApp or SMS, prepare the message and show it for user review; do not press the final Send button without the user's explicit confirmation. "
            + "If the user asks to message a person but does not specify WhatsApp or SMS, ask which app they want only if the context does not already make it clear. "
            + "If SMS or text message is specified, use send_sms. "
            + "If the user asks to find something inside the currently open app, use screen_task to search within that app when possible; do not switch to Google by default. "
            + "If the target app or action is unclear, ask a short clarification instead of choosing an unrelated tool. "
            + "For anything that needs tapping, typing or reading inside another app, use screen_task with a clear, step-by-step task and verify the visible result. "
            + "Never claim something is done unless the tool result says so. "
            + "Use remember only when the user asks you to remember something. "
            + "If a saved skill is provided, use it as learned experience, but verify each action and adapt if the screen differs. "
            + "Saved memory:\n" + mem.asText()
            + skillContext
            + "\nNow: " + new java.util.Date();
    }

    private String callCompatible(String provider, String task) throws Exception {
        String endpoint;
        String model;
        String display;

        switch (provider) {
            case "groq":
                endpoint = "https://api.groq.com/openai/v1/chat/completions";
                model = "openai/gpt-oss-120b";
                display = "Groq";
                break;
            case "openai":
                endpoint = "https://api.openai.com/v1/chat/completions";
                model = "gpt-4o-mini";
                display = "OpenAI";
                break;
            case "cerebras":
                endpoint = "https://api.cerebras.ai/v1/chat/completions";
                model = "gpt-oss-120b";
                display = "Cerebras";
                break;
            case "openrouter":
                endpoint = "https://openrouter.ai/api/v1/chat/completions";
                model = "openai/gpt-oss-120b";
                display = "OpenRouter";
                break;
            default:
                throw new Exception("Unknown selected API: " + provider);
        }

        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", system(task)))
                .put(new JSONObject().put("role", "user").put("content", task));

        JSONArray compatibleTools = new JSONArray();
        JSONArray declarations = tools.length() > 0
                ? tools.optJSONObject(0).optJSONArray("functionDeclarations") : null;

        if (declarations != null) {
            for (int i = 0; i < declarations.length(); i++) {
                JSONObject f = declarations.getJSONObject(i);
                JSONObject parameters = f.optJSONObject("parameters");
                if (parameters != null) {
                    parameters = new JSONObject(parameters.toString());
                    lowercaseSchemaTypes(parameters);
                } else {
                    parameters = new JSONObject()
                            .put("type", "object")
                            .put("properties", new JSONObject());
                }

                compatibleTools.put(new JSONObject()
                        .put("type", "function")
                        .put("function", new JSONObject()
                                .put("name", f.getString("name"))
                                .put("description", f.optString("description", "Rex tool"))
                                .put("parameters", parameters)));
            }
        }

        for (int step = 0; step < 7; step++) {
            JSONObject body = new JSONObject()
                    .put("model", model)
                    .put("messages", messages)
                    .put("temperature", 0.4)
                    .put("max_tokens", 2048);

            if (compatibleTools.length() > 0) {
                body.put("tools", compatibleTools);
                body.put("tool_choice", "auto");
            }

            HttpURLConnection c = null;
            String raw;
            try {
                c = (HttpURLConnection) new URL(endpoint).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(10000);
                c.setReadTimeout(45000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("Authorization", "Bearer " + key);

                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = c.getResponseCode();
                raw = read(code < 300 ? c.getInputStream() : c.getErrorStream());
                if (code >= 300)
                    throw new Exception(display + " HTTP " + code + ": " + raw);
            } finally {
                if (c != null) c.disconnect();
            }

            JSONObject response = new JSONObject(raw);
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0)
                throw new Exception(display + ": response madhye answer nahi.");

            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null)
                throw new Exception(display + ": message missing.");

            JSONArray calls = message.optJSONArray("tool_calls");
            if (calls == null || calls.length() == 0) {
                String answer = message.optString("content", "").trim();
                if (answer.isEmpty()) answer = "काम पूर्ण झाले.";
                return answer;
            }

            messages.put(message);

            for (int i = 0; i < calls.length(); i++) {
                JSONObject tc = calls.getJSONObject(i);
                String callId = tc.optString("id", "");
                JSONObject fn = tc.optJSONObject("function");
                if (fn == null) continue;

                String name = fn.optString("name", "");
                JSONObject args;
                try {
                    args = new JSONObject(fn.optString("arguments", "{}"));
                } catch (Exception badArgs) {
                    args = new JSONObject();
                }

                progress("🔧 " + display + " tool → " + name);
                String result = exec(name, args);

                messages.put(new JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", callId)
                        .put("content", result == null ? "" : result));
            }

            progress("🧠 " + display + " tool result verify karat aahe…");
        }

        throw new Exception(display + ": tool steps chi limit pohochली; task purna zala yachi khatri nahi.");
    }

    private void lowercaseSchemaTypes(JSONObject node) throws JSONException {
        if (node.has("type") && node.opt("type") instanceof String) {
            node.put("type", node.getString("type").toLowerCase(java.util.Locale.ROOT));
        }

        java.util.Iterator<String> keys = node.keys();
        while (keys.hasNext()) {
            Object value = node.opt(keys.next());
            if (value instanceof JSONObject) {
                lowercaseSchemaTypes((JSONObject) value);
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                for (int i = 0; i < array.length(); i++) {
                    Object item = array.opt(i);
                    if (item instanceof JSONObject) {
                        lowercaseSchemaTypes((JSONObject) item);
                    }
                }
            }
        }
    }

    private JSONObject call(JSONArray msgs, String task) throws Exception {
        JSONObject body = new JSONObject().put("contents", msgs).put("tools", tools)
                .put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", system(task)))))
                .put("generationConfig", new JSONObject().put("temperature", 0.4).put("maxOutputTokens", 4096));
        String err = "";
        for (int a = 0; a < 5; a++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(BASE + MODELS[Math.min(a, MODELS.length - 1)] + ":generateContent").openConnection();
                c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(60000); c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("x-goog-api-key", key);
                try (OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                int code = c.getResponseCode();
                String raw = read(code < 300 ? c.getInputStream() : c.getErrorStream());
                if (code < 300) return new JSONObject(raw);
                err = "Gemini HTTP " + code + ": " + raw;
                if (!(code == 503 || code == 429 || code == 500 || code == 404)) throw new Exception(err);
            } finally { if (c != null) c.disconnect(); }
            Thread.sleep(2000L * (a + 1));
        }
        throw new Exception(err);
    }

    private String read(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) b.append(line);
        r.close();
        return b.toString();
    }

    private String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }

    private String go(Intent i) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); return "opened ok"; }
        catch (Exception e) { return "failed: " + e.getMessage(); }
    }

    private String exec(String n, JSONObject a) {
        try {
            switch (n) {
                case "open_app": { Intent i = RexApps.find(ctx, a.optString("name")); return i == null ? "App सापडला नाही." : go(i); }
                case "search_contact": return searchContact(a.optString("name"));
                case "web_search": return go(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + enc(a.optString("query")))));
                case "youtube_search": return go(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + enc(a.optString("query")))));
                case "open_url": return go(new Intent(Intent.ACTION_VIEW, Uri.parse(a.optString("url"))));
                case "navigate": return go(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + enc(a.optString("place")))));
                case "set_alarm": return go(new Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, a.optInt("hour")).putExtra(AlarmClock.EXTRA_MINUTES, a.optInt("minute"))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "Rex")).putExtra(AlarmClock.EXTRA_SKIP_UI, true));
                case "set_timer": return go(new Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, a.optInt("seconds")).putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "Rex"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true));
                case "call_number": return go(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(a.optString("number")))));
                case "send_sms": return go(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(a.optString("number"))))
                        .putExtra("sms_body", a.optString("message")));
                case "whatsapp_open": { String number = a.optString("number").replaceAll("[^0-9]", ""); if (number.isEmpty()) return "Phone number missing."; return go(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + number))); }
                case "whatsapp_message": return go(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/"
                        + a.optString("number").replaceAll("[^0-9]", "") + "?text=" + enc(a.optString("message")))));
                case "battery": {
                    Intent b = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    int l = b.getIntExtra("level", -1), sc = b.getIntExtra("scale", 100), st = b.getIntExtra("status", 0);
                    return (l * 100 / sc) + "%, charging=" + (st == 2 || st == 5);
                }
                case "weather": {
                    HttpURLConnection c = (HttpURLConnection) new URL("https://wttr.in/" + enc(a.optString("city")) + "?format=3").openConnection();
                    c.setConnectTimeout(10000); c.setReadTimeout(10000);
                    try { return read(c.getInputStream()).trim(); } finally { c.disconnect(); }
                }
                case "torch": {
                    CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
                    cm.setTorchMode(cm.getCameraIdList()[0], a.optBoolean("on"));
                    return "torch done";
                }
                case "remember": mem.add(a.optString("text")); return "Saved.";
                case "screen_task": return screen(a.optString("task"));
            }
            return "Unknown tool " + n;
        } catch (Exception e) { return "Tool error: " + e.getMessage(); }
    }

    private String searchContact(String name) {
        if (name == null || name.trim().isEmpty()) return "Contact चे नाव द्या.";
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
                ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return "CONTACT_PERMISSION_REQUIRED: Rex ला Contacts permission द्या आणि पुन्हा प्रयत्न करा.";
        }
        android.database.Cursor cur = null;
        try {
            String wanted = name.trim().toLowerCase(java.util.Locale.ROOT);
            cur = ctx.getContentResolver().query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[] {
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    }, null, null,
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
            if (cur == null) return "Contacts वाचता आले नाहीत.";
            org.json.JSONArray matches = new org.json.JSONArray();
            while (cur.moveToNext() && matches.length() < 10) {
                String contactName = cur.getString(0);
                String number = cur.getString(1);
                if (contactName != null && number != null &&
                        contactName.toLowerCase(java.util.Locale.ROOT).contains(wanted)) {
                    org.json.JSONObject item = new org.json.JSONObject();
                    item.put("name", contactName);
                    item.put("number", number);
                    matches.put(item);
                }
            }
            if (matches.length() == 0) return "या नावाचा contact सापडला नाही: " + name;
            return "CONTACT_MATCHES: " + matches.toString()
                    + ". Multiple matches असल्यास user कडून योग्य contact निवडून घ्या.";
        } catch (Exception e) {
            return "Contact search error: " + e.getMessage();
        } finally {
            if (cur != null) cur.close();
        }
    }

    private String screen(String task) throws Exception {
        if (!RexAccessibilityService.isOnline()) return "Screen Agent OFF. Accessibility ON करायला सांगा.";
        final CountDownLatch L = new CountDownLatch(1);
        final String[] res = {"timeout"};
        agent.run(key, task, new RexAgent.Callback() {
            public void onUpdate(String t) {
                progress("🧠 " + t);
            }
            public void onDone(String t) { res[0] = t; L.countDown(); }
            public void onError(String e) { res[0] = "error: " + e; L.countDown(); }
        });
        boolean completed = L.await(150, TimeUnit.SECONDS);
        if (!completed) {
            agent.cancelCurrentTask();
            return "TIMEOUT: Agent ला थांबण्याची विनंती केली. Task थांबेपर्यंत नवीन Agent task सुरू होणार नाही.";
        }
        return res[0];
    }

    public void shutdown() { ex.shutdownNow(); }
}
