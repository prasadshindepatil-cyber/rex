package com.rexai.controller.agent.helpline;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;

public class HelplineRepairHandler {
    private static final String TAG = "HelplineRepairHandler";
    private Context context;

    public HelplineRepairHandler(Context context) {
        this.context = context;
    }

    public boolean createBackup(File sourceFile) {
        if (!sourceFile.exists()) return false;
        File backupFile = new File(sourceFile.getAbsolutePath() + ".bak");
        try (FileChannel src = new FileInputStream(sourceFile).getChannel();
             FileChannel dst = new FileOutputStream(backupFile).getChannel()) {
            dst.transferFrom(src, 0, src.size());
            Log.d(TAG, "Backup created successfully: " + backupFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to create backup", e);
            return false;
        }
    }

    public boolean rollback(File targetFile) {
        File backupFile = new File(targetFile.getAbsolutePath() + ".bak");
        if (!backupFile.exists()) {
            Log.e(TAG, "No backup found for rollback");
            return false;
        }
        try (FileChannel src = new FileInputStream(backupFile).getChannel();
             FileChannel dst = new FileOutputStream(targetFile).getChannel()) {
            dst.transferFrom(src, 0, src.size());
            Log.d(TAG, "Rollback successful for: " + targetFile.getName());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Rollback failed", e);
            return false;
        }
    }

    public String getFileChecksum(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] byteArray = new byte[1024];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error computing SHA-256", e);
            return null;
        }
    }
}
