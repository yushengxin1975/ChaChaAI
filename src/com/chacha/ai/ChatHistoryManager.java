package com.chacha.ai;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ChatHistoryManager {
    private static final String FILE_NAME = "chat_history.json";

    public static synchronized void saveHistory(Context context, List<ChatMessage> messages) {
        if (context == null || messages == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (ChatMessage msg : messages) {
                // 不存储临时系统提示，或者存储关键对话记录
                JSONObject obj = new JSONObject();
                obj.put("role", msg.role);
                obj.put("content", msg.content);
                obj.put("time", msg.time);
                arr.put(obj);
            }
            File file = new File(context.getFilesDir(), FILE_NAME);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(arr.toString().getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized List<ChatMessage> loadHistory(Context context) {
        List<ChatMessage> list = new ArrayList<ChatMessage>();
        if (context == null) return list;
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.exists()) return list;

            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            fis.close();

            JSONArray arr = new JSONArray(sb.toString().trim());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                int role = obj.optInt("role", ChatMessage.ROLE_USER);
                String content = obj.optString("content", "");
                String time = obj.optString("time", "");
                list.add(new ChatMessage(role, content, time));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static synchronized void clearHistory(Context context) {
        if (context == null) return;
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
