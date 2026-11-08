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
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public interface Callback { void onSuccess(String text); void onError(String error); }

    public void ask(String apiKey, String prompt, String system, Callback cb) { ask(apiKey,prompt,system,null,null,cb,1); }
    
    public void askWithImage(String apiKey, String prompt, String system, String base64Image, String mimeType, Callback cb) {
        ask(apiKey, prompt, system, base64Image, mimeType, cb, 1);
    }

    private void ask(String apiKey, String prompt, String system, String base64Image, String mimeType, Callback cb, final int attempt) {
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
                user.put("role", "user");
                JSONArray parts = new JSONArray();

                if (base64Image != null && !base64Image.isEmpty()) {
                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mimeType", mimeType != null ? mimeType : "image/jpeg");
                    inlineData.put("data", base64Image);
                    JSONObject partObj = new JSONObject();
                    partObj.put("inlineData", inlineData);
                    parts.put(partObj);
                }

                if (prompt != null && !prompt.isEmpty()) {
                    parts.put(new JSONObject().put("text", prompt));
                }

                user.put("parts", parts);
                contents.put(user);
                req.put("contents", contents);

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
                if((code==503||code==429||code==500||code==404)&&attempt<5){ try{Thread.sleep(2000L*attempt);}catch(Exception ig){} ask(apiKey,prompt,system,base64Image,mimeType,cb,attempt+1); return; }
                if(code<200||code>=300){ cbError(cb,"Gemini HTTP "+code+": "+raw); return; }
                String answer=extract(new JSONObject(raw));
                if(answer.isEmpty()){ cbError(cb,"Gemini ने रिकामा response दिला."); return; }
                main.post(() -> cb.onSuccess(answer));
            } catch(Exception e) {
                cbError(cb,"Gemini error: "+e.getMessage());
            } finally { if(c!=null)c.disconnect(); }
        });
    }

    private String modelFor(int a){ return a<=2?DEFAULT_MODEL:(a==3?"gemini-2.5-pro":"gemini-2.0-flash"); }

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
