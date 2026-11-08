package com.rexai.controller;

import android.app.*;
import android.os.*;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.speech.*;
import android.widget.*;
import android.view.*;
import android.net.*;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class MainActivity extends Activity {
    private SpeechRecognizer speech;
    private boolean live=false;
    private volatile boolean requestRunning=false;
    private final ArrayDeque<String> taskQueue = new ArrayDeque<>();
    private Button btnLive,btnStart,btnAttach;
    private EditText input;
    private TextView reply,status,txtAttachmentName;
    private ImageView imgAttachmentPreview;
    private LinearLayout layoutAttachmentPreview;
    private RexGemini gemini;
    private RexBrain brain;
    private LocalBrain localBrain;
    private RexAgent agent;
    private RexMemory memory;
    private android.content.SharedPreferences prefs;

    // Attachment State
    private Uri attachedFileUri = null;
    private String attachedMimeType = null;
    private String attachedFileName = null;
    private byte[] attachedFileBytes = null;

    private static final int PICK_FILE_REQUEST = 1001;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        RexTermuxBtn.attach(this);
        RexTermux.autoStart(this);
        findViewById(R.id.btnSend).setOnLongClickListener(v->{RexTermux.setup(this);return true;});
        RexDoctor.install(this);
        RexDoctor.attach(this);
        ((ImageView)findViewById(R.id.imgLogo)).setImageResource(getApplicationInfo().icon!=0?getApplicationInfo().icon:android.R.drawable.sym_def_app_icon);
        
        chatList = findViewById(R.id.chatList);
        int[] cid={R.id.chipYt,R.id.chipWa,R.id.chipCh,R.id.chipSe,R.id.chipCa,R.id.chipMa,R.id.chipIn,R.id.chipGm,R.id.chipPh};
        final String[] cn={"youtube","whatsapp","chrome","settings","camera","maps","instagram","gmail","phone"};
        for(int ci=0;ci<cid.length;ci++){
            final String nm=cn[ci];
            findViewById(cid[ci]).setOnClickListener(v->{
                String r=RexApps.tryOpen(this,"open "+nm);
                addChat("🔴 "+(r!=null?r:nm+" सापडला नाही."));
            });
        }
        btnLive=findViewById(R.id.btnLive);
        btnStart=findViewById(R.id.btnStart);
        btnAttach=findViewById(R.id.btnAttach);
        input=findViewById(R.id.edtMessage);
        reply=findViewById(R.id.txtReply);
        rexCore=findViewById(R.id.rexCore);
        status=findViewById(R.id.txtBrainStatus);
        
        layoutAttachmentPreview = findViewById(R.id.layoutAttachmentPreview);
        imgAttachmentPreview = findViewById(R.id.imgAttachmentPreview);
        txtAttachmentName = findViewById(R.id.txtAttachmentName);

        gemini=new RexGemini(); agent=new RexAgent(this); memory=new RexMemory(this); brain=new RexBrain(this,agent);
        localBrain=new LocalBrain(this);
        prefs=getSharedPreferences("rex_settings",MODE_PRIVATE);

        findViewById(R.id.btnSend).setOnClickListener(v->send());
        btnStart.setOnClickListener(v->configureApi());
        btnAttach.setOnClickListener(v->showAttachmentDialog());
        findViewById(R.id.btnRemoveAttachment).setOnClickListener(v->clearAttachment());

        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            LinearLayout panel = findViewById(R.id.toolsPanel);
            boolean opening = panel.getVisibility() != View.VISIBLE;
            panel.setVisibility(opening ? View.VISIBLE : View.GONE);
            findViewById(R.id.btnSettings).setContentDescription(opening ? "Hide Rex tools" : "Show Rex tools");
            panel.setAlpha(opening ? 0f : 1f);
            if (opening) panel.animate().alpha(1f).setDuration(220).start();
        });

        btnLive.setOnClickListener(v->toggleLive());
        findViewById(R.id.btnAgent).setOnClickListener(v->runAgentFromInput());
        findViewById(R.id.btnDeveloper).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, RexDeveloperActivity.class));
        });

        findViewById(R.id.btnBrain).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, RexBrainActivity.class));
        });

        findViewById(R.id.btnChat).setOnClickListener(v->input.requestFocus());
        findViewById(R.id.btnPermission).setOnClickListener(v->{
            startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
        });

        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) {
            java.util.ArrayList<String> missing = new java.util.ArrayList<>();
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.RECORD_AUDIO);
            if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.READ_CONTACTS);
            requestPermissions(missing.toArray(new String[0]), 123);
        }
    }

    private void showAttachmentDialog(){
        String[] options = {"📷 Take Photo (Camera)", "🖼 Choose Image/Document from Gallery"};
        new AlertDialog.Builder(this)
            .setTitle("Select Attachment")
            .setItems(options, (dialog, which) -> {
                if(which == 0){
                    Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    if(cameraIntent.resolveActivity(getPackageManager()) != null){
                        startActivityForResult(cameraIntent, 2002);
                    } else {
                        Toast.makeText(this, "Camera app not found", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    String[] mimes = {"image/*", "application/pdf", "text/plain", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"};
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(Intent.createChooser(intent, "Select File"), PICK_FILE_REQUEST);
                }
            })
            .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK){
            if(requestCode == PICK_FILE_REQUEST && data != null){
                attachedFileUri = data.getData();
                if(attachedFileUri != null){
                    processSelectedFile(attachedFileUri);
                }
            } else if(requestCode == 2002 && data != null && data.getExtras() != null){
                Bitmap photo = (Bitmap) data.getExtras().get("data");
                if(photo != null){
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    photo.compress(Bitmap.CompressFormat.JPEG, 90, stream);
                    attachedFileBytes = stream.toByteArray();
                    attachedMimeType = "image/jpeg";
                    attachedFileName = "CameraPhoto.jpg";
                    
                    imgAttachmentPreview.setImageBitmap(photo);
                    txtAttachmentName.setText(attachedFileName);
                    layoutAttachmentPreview.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Photo attached successfully", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void processSelectedFile(Uri uri){
        try {
            attachedMimeType = getContentResolver().getType(uri);
            if(attachedMimeType == null) attachedMimeType = "application/octet-stream";
            
            attachedFileName = "Attached File";
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if(cursor != null && cursor.moveToFirst()){
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(nameIndex != -1){
                    attachedFileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }

            InputStream iStream = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            int len;
            while ((len = iStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            attachedFileBytes = byteBuffer.toByteArray();
            iStream.close();

            if(attachedMimeType.startsWith("image/")){
                Bitmap bmp = BitmapFactory.decodeByteArray(attachedFileBytes, 0, attachedFileBytes.length);
                if(bmp != null){
                    imgAttachmentPreview.setImageBitmap(bmp);
                } else {
                    imgAttachmentPreview.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                imgAttachmentPreview.setImageResource(android.R.drawable.ic_menu_agenda);
            }

            txtAttachmentName.setText(attachedFileName);
            layoutAttachmentPreview.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Attached: " + attachedFileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e){
            Toast.makeText(this, "Failed to attach file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            clearAttachment();
        }
    }

    private void clearAttachment(){
        attachedFileUri = null;
        attachedFileBytes = null;
        attachedMimeType = null;
        attachedFileName = null;
        layoutAttachmentPreview.setVisibility(View.GONE);
    }

    private void configureApi(){
        final EditText ed=new EditText(this);
        ed.setHint("Gemini API Key टाка");
        ed.setText(prefs.getString("api_key",""));
        new AlertDialog.Builder(this)
            .setTitle("Rex API Setup")
            .setView(ed)
            .setPositiveButton("Save", (d,w)->{
                String k=ed.getText().toString().trim();
                prefs.edit().putString("api_key",k).apply();
                Toast.makeText(this,"API Key Saved!",Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel",null)
            .show();
    }

    private void runAgentFromInput(){
        String q=input.getText().toString().trim();
        if(q.isEmpty()){Toast.makeText(this,"कृपया command लिहा!",Toast.LENGTH_SHORT).show();return;}
        input.setText("");
        addChat("👤 "+q);
        new Thread(()->{
            String res=agent.execute(q);
            runOnUiThread(()->addChat("🤖 "+res));
        }).start();
    }

    private void send(){
        String q=input.getText().toString().trim();
        if(q.isEmpty() && attachedFileBytes == null) return;
        if(q.isEmpty()) q = "Describe this attachment.";

        input.setText("");
        String displayMsg = q;
        if(attachedFileName != null) displayMsg += " [Attached: " + attachedFileName + "]";
        addChat("👤 "+displayMsg);

        // Capture local reference of attachment for background thread
        final byte[] fileBytes = attachedFileBytes;
        final String mimeType = attachedMimeType;
        clearAttachment(); // reset UI preview

        new Thread(()->{
            String apiKey=prefs.getString("api_key","");
            String ans="";
            if(fileBytes != null && fileBytes.length > 0){
                // Multimodal request
                ans = gemini.askMultimodal(apiKey, q, fileBytes, mimeType);
            } else if(!apiKey.isEmpty()){
                ans=gemini.ask(apiKey, q);
            } else {
                ans=localBrain.think(q);
            }
            final String finalAns=ans;
            runOnUiThread(()->{
                reply.setText(finalAns);
                addChat("🤖 "+finalAns);
            });
        }).start();
    }

    private LinearLayout chatList;
    private View rexCore;

    private void addChat(String m){
        TextView t=new TextView(this);
        t.setText(m);
        t.setTextColor(m.startsWith("👤")?0xFFFFFFFF:0xFF91A9CC);
        t.setTextSize(14sp);
        t.setPadding(0,6,0,6);
        chatList.addView(t);
        ((ScrollView)chatList.getParent()).fullScroll(View.FOCUS_DOWN);
    }

    private void toggleLive(){
        if(live){stopLive();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},99);
            return;
        }
        live=true;
        btnLive.setText("⏹ Stop");
        btnLive.setBackgroundResource(R.drawable.rx_btn2);
        speech=SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle p){}
            public void onBeginningOfSpeech(){}
            public void onRmsChanged(float r){}
            public void onBufferReceived(byte[] b){}
            public void onEndOfSpeech(){}
            public void onError(int e){}
            public void onResults(Bundle r){
                ArrayList<String> matches=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(matches!=null && !matches.isEmpty()){
                    String spoken=matches.get(0);
                    input.setText(spoken);
                    send();
                }
                if(live) speech.startListening(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH));
            }
            public void onPartialResults(Bundle p){}
            public void onEvent(int i, Bundle b){}
        });
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"mr-IN");
        speech.startListening(intent);
        Toast.makeText(this,"Live Listening Started...",Toast.LENGTH_SHORT).show();
    }

    private void stopLive(){
        live=false;
        btnLive.setText("🎙 Live");
        btnLive.setBackgroundResource(R.drawable.rx_btn);
        if(speech!=null){speech.destroy();speech=null;}
        Toast.makeText(this,"Live Stopped",Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy(){
        super.onDestroy();
        if(speech!=null) speech.destroy();
    }
}
