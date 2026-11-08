package com.rexai.controller;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RexGemini {
    private static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public interface Callback { void onSuccess(String text); void onError(String error); }

    public static class AttachmentItem {
        public String mimeType;
        public String base64Data;
        public String displayName;
        public AttachmentItem(String mimeType, String base64Data, String displayName) {
            this.mimeType = mimeType;
            this.base64Data = base64Data;
            this.displayName = displayName;
        }
    }

    public void ask(String apiKey, String prompt, String system, Callback cb) { ask(apiKey,prompt,system,cb,1); }
    private void ask(String apiKey, String prompt, String system, Callback cb, final int attempt) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            cbError(cb, "Gemini API key सेट केलेली नाही."); return;
        }
        executor.execute(() -> {
            HttpURLConnection c = null;
            try {
                URL u = new URL(API_URL + modelFor(attempt) + ":generateContent");
                c = (HttpURLConnection)u.openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json");
                c.setRequestProperty("x-goog-api-key",apiKey.trim());

                JSONObject req = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject user = new JSONObject();
                user.put("role","user");
                JSONArray parts = new JSONArray();
                parts.put(new JSONObject().put("text", prompt));
                user.put("parts",parts);
                contents.put(user);
                req.put("contents",contents);

                if (system != null && !system.trim().isEmpty()) {
                    JSONObject si = new JSONObject();
                    si.put("parts",new JSONArray().put(new JSONObject().put("text",system)));
                    req.put("systemInstruction",si);
                }

                JSONObject cfg = new JSONObject();
                cfg.put("temperature",0.3);
                cfg.put("maxOutputTokens",4096);
                req.put("generationConfig",cfg);

                byte[] data=req.toString().getBytes(StandardCharsets.UTF_8);
                try(OutputStream os=c.getOutputStream()){ os.write(data); }

                int code=c.getResponseCode();
                InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
                String raw=read(in);
                if((code==503||code==429||code==500||code==404)&&attempt<5){ try{Thread.sleep(2000L*attempt);}catch(Exception ig){} ask(apiKey,prompt,system,cb,attempt+1); return; }
                if(code<200||code>=300){ cbError(cb,"Gemini HTTP "+code+": "+raw); return; }
                String answer=extract(new JSONObject(raw));
                if(answer.isEmpty()){ cbError(cb,"Gemini ने रिकामा response दिला."); return; }
                main.post(() -> cb.onSuccess(answer));
            } catch(Exception e) {
                cbError(cb,"Gemini error: "+e.getMessage());
            } finally { if(c!=null)c.disconnect(); }
        });
    }

    public void askMultimodal(String apiKey, String prompt, String system, java.util.List<AttachmentItem> attachments, Callback cb) {
        askMultimodalInternal(apiKey, prompt, system, attachments, cb, 1);
    }

    private void askMultimodalInternal(String apiKey, String prompt, String system, java.util.List<AttachmentItem> attachments, Callback cb, final int attempt) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            cbError(cb, "Gemini API key सेट केलेली नाही."); return;
        }
        executor.execute(() -> {
            HttpURLConnection c = null;
            try {
                URL u = new URL(API_URL + modelFor(attempt) + ":generateContent");
                c = (HttpURLConnection)u.openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json");
                c.setRequestProperty("x-goog-api-key",apiKey.trim());

                JSONObject req = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject user = new JSONObject();
                user.put("role","user");
                JSONArray parts = new JSONArray();

                if (attachments != null) {
                    for (AttachmentItem item : attachments) {
                        if (item.mimeType != null && item.mimeType.startsWith("text/")) {
                            String decodedText = new String(android.util.Base64.decode(item.base64Data, android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
                            parts.put(new JSONObject().put("text", "--- Document (" + item.displayName + ") ---\n" + decodedText + "\n--------------------------------"));
                        } else if (item.base64Data != null && !item.base64Data.isEmpty()) {
                            JSONObject inlineData = new JSONObject();
                            inlineData.put("mime_type", item.mimeType != null ? item.mimeType : "image/jpeg");
                            inlineData.put("data", item.base64Data);
                            JSONObject blobPart = new JSONObject();
                            blobPart.put("inline_data", inlineData);
                            parts.put(blobPart);
                        }
                    }
                }

                if (prompt != null && !prompt.trim().isEmpty()) {
                    parts.put(new JSONObject().put("text", prompt));
                }

                user.put("parts",parts);
                contents.put(user);
                req.put("contents",contents);

                if (system != null && !system.trim().isEmpty()) {
                    JSONObject si = new JSONObject();
                    si.put("parts",new JSONArray().put(new JSONObject().put("text",system)));
                    req.put("systemInstruction",si);
                }

                JSONObject cfg = new JSONObject();
                cfg.put("temperature",0.3);
                cfg.put("maxOutputTokens",4096);
                req.put("generationConfig",cfg);

                byte[] data=req.toString().getBytes(StandardCharsets.UTF_8);
                try(OutputStream os=c.getOutputStream()){ os.write(data); }

                int code=c.getResponseCode();
                InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
                String raw=read(in);
                if((code==503||code==429||code==500||code==404)&&attempt<5){ try{Thread.sleep(2000L*attempt);}catch(Exception ig){} askMultimodalInternal(apiKey,prompt,system,attachments,cb,attempt+1); return; }
                if(code<200||code>=300){ cbError(cb,"Gemini HTTP "+code+": "+raw); return; }
                String answer=extract(new JSONObject(raw));
                if(answer.isEmpty()){ cbError(cb,"Gemini ने रिकामा response दिला."); return; }
                main.post(() -> cb.onSuccess(answer));
            } catch(Exception e) {
                cbError(cb,"Gemini error: "+e.getMessage());
            } finally { if(c!=null)c.disconnect(); }
        });
    }

    private String modelFor(int a){ return a<=2?DEFAULT_MODEL:(a==3?"gemini-3.7-flash":"gemini-3.5-flash-lite"); }

    private String extract(JSONObject j) {
        StringBuilder out=new StringBuilder();
        JSONArray cs=j.optJSONArray("candidates");
        if(cs==null||cs.length()==0)return "";
        JSONObject content=cs.optJSONObject(0);
        if(content==null)return "";
        content=content.optJSONObject("content");
        if(content==null)return "";
        JSONArray ps=content.optJSONArray("parts");
        if(ps==null)return "";
        for(int i=0;i<ps.length();i++){
            JSONObject p=ps.optJSONObject(i);
            if(p!=null){
                String t=p.optString("text","");
                if(!t.isEmpty()){if(out.length()>0)out.append("\n");out.append(t);}
            }
        }
        return out.toString().trim();
    }

    private String read(InputStream in)throws Exception{
        if(in==null)return "";
        BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder(); String line;
        while((line=r.readLine())!=null)b.append(line);
        r.close(); return b.toString();
    }

    private void cbError(Callback cb,String e){ main.post(() -> cb.onError(e)); }
    public void shutdown(){executor.shutdownNow();}
}
