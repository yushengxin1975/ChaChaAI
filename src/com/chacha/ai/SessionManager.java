package com.chacha.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理器（墨水屏多端高可靠性存储引擎）
 * 核心特性：
 * 1. 多层冗余持久化：同时写入内部存储、/sdcard/ChaChaAI/sessions 公共持久化目录以及外部应用私有目录；
 * 2. 覆盖安装与卸载保护：即使应用被系统清理、卸载重装或覆盖安装，启动时自动从 /sdcard/ChaChaAI 无缝复原历史会话；
 * 3. 索引自愈修复（Self-Healing）：当 sessions_index.json 损坏或丢失时，自动反向扫描磁盘真实存在的 session_*.json 并重建索引。
 */
public class SessionManager {
    private static final String PREF_NAME = "ChaChaSessionPrefs";
    private static final String KEY_CURRENT_SESSION_ID = "current_session_id";
    private static final String SESSIONS_DIR = "sessions";
    private static final String INDEX_FILE = "sessions_index.json";

    private static boolean isMigrating = false;

    /**
     * 获取所有可用的存储会话目录列表（内部私有目录 + SD卡公共持久化目录 + 外部私有目录）
     */
    public static List<File> getAllSessionsDirs(Context context) {
        List<File> dirs = new ArrayList<File>();
        if (context == null) return dirs;

        // 1. 内部私有存储目录
        try {
            File intDir = new File(context.getFilesDir(), SESSIONS_DIR);
            if (!intDir.exists()) intDir.mkdirs();
            dirs.add(intDir);
        } catch (Exception ignored) {}

        // 2. 外部公共持久化存储目录（/sdcard/ChaChaAI/sessions，即便卸载重装或覆盖安装，文件也绝不丢失）
        try {
            File extRoot = Environment.getExternalStorageDirectory();
            if (extRoot != null) {
                File pubDir = new File(extRoot, "ChaChaAI/" + SESSIONS_DIR);
                if (!pubDir.exists()) pubDir.mkdirs();
                if (pubDir.exists() && pubDir.canWrite()) {
                    dirs.add(pubDir);
                }
            }
        } catch (Exception ignored) {}

        // 3. 外部应用私有存储目录（免动态权限）
        try {
            File extAppDir = context.getExternalFilesDir(null);
            if (extAppDir != null) {
                File appSessions = new File(extAppDir, SESSIONS_DIR);
                if (!appSessions.exists()) appSessions.mkdirs();
                if (appSessions.exists() && appSessions.canWrite()) {
                    dirs.add(appSessions);
                }
            }
        } catch (Exception ignored) {}

        return dirs;
    }

    /**
     * 应用启动时自动触发多层同步与数据恢复
     */
    public static synchronized void ensureInitialized(Context context) {
        if (context == null) return;
        syncAndRecoverSessions(context);
    }

    /**
     * 获取完整的会话列表（自动跨目录同步与自愈检查）
     */
    public static synchronized List<ChatSession> getSessionList(Context context) {
        if (context == null) return new ArrayList<ChatSession>();
        return syncAndRecoverSessions(context);
    }

    /**
     * 核心同步与自愈引擎：
     * 1. 扫描所有候选目录中的 session_*.json；
     * 2. 补齐各目录间缺失的会话文件（双向同步）；
     * 3. 重建或补充索引文件 sessions_index.json 并同步回写至所有目录。
     */
    public static synchronized List<ChatSession> syncAndRecoverSessions(Context context) {
        List<File> dirs = getAllSessionsDirs(context);
        Map<String, ChatSession> sessionMap = new HashMap<String, ChatSession>();

        // 0. 如果旧版 chat_history.json 存在且尚未迁移，先迁移
        if (!isMigrating) {
            isMigrating = true;
            try {
                migrateOldHistory(context);
            } catch (Exception ignored) {}
            isMigrating = false;
        }

        // 1. 先尝试从各目录的 sessions_index.json 中读取已有索引
        for (File dir : dirs) {
            File indexFile = new File(dir, INDEX_FILE);
            if (indexFile.exists() && indexFile.length() > 2) {
                try {
                    String content = readFileContent(indexFile);
                    if (content != null && content.length() > 2) {
                        JSONArray arr = new JSONArray(content.trim());
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            String id = obj.optString("id", "");
                            if (id.length() > 0 && !sessionMap.containsKey(id)) {
                                ChatSession s = new ChatSession();
                                s.id = id;
                                s.title = obj.optString("title", "未命名会话");
                                s.time = obj.optString("time", "");
                                sessionMap.put(id, s);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. 深度扫描各目录下的所有 session_*.json，防止因索引文件丢失或覆盖安装造成会话遗漏
        Map<String, File> freshestFiles = new HashMap<String, File>();
        for (File dir : dirs) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                String fname = f.getName();
                if (fname.startsWith("session_") && fname.endsWith(".json") && f.length() > 10) {
                    String sessionId = fname.substring("session_".length(), fname.length() - ".json".length());
                    if (!freshestFiles.containsKey(sessionId) || f.lastModified() > freshestFiles.get(sessionId).lastModified()) {
                        freshestFiles.put(sessionId, f);
                    }

                    // 如果当前 session 尚未记录在索引中，从文件头部直接恢复 title 和 time
                    if (!sessionMap.containsKey(sessionId)) {
                        ChatSession s = readSessionHeader(f, sessionId);
                        if (s != null) {
                            sessionMap.put(sessionId, s);
                        }
                    }
                }
            }
        }

        // 3. 跨存储目录文件双向同步（确保内部存储与 SD 卡持久化目录文件一致）
        for (Map.Entry<String, File> entry : freshestFiles.entrySet()) {
            String sid = entry.getKey();
            File srcFile = entry.getValue();
            String targetFileName = "session_" + sid + ".json";

            for (File dir : dirs) {
                File destFile = new File(dir, targetFileName);
                if (!destFile.exists() || destFile.length() < 10) {
                    copyFile(srcFile, destFile);
                }
            }
        }

        // 4. 构建统一列表并按时间倒序排序
        List<ChatSession> resultList = new ArrayList<ChatSession>(sessionMap.values());
        Collections.sort(resultList, new Comparator<ChatSession>() {
            @Override
            public int compare(ChatSession o1, ChatSession o2) {
                return o2.id.compareTo(o1.id);
            }
        });

        // 5. 将完整的 sessions_index.json 同步保存至所有目录
        saveSessionIndexToAllDirs(dirs, resultList);

        return resultList;
    }

    private static ChatSession readSessionHeader(File file, String sessionId) {
        try {
            String content = readFileContent(file);
            if (content == null || content.length() < 10) return null;
            JSONObject root = new JSONObject(content.trim());
            ChatSession s = new ChatSession();
            s.id = root.optString("id", sessionId);
            s.title = root.optString("title", "未命名会话");
            s.time = root.optString("time", "");
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    public static synchronized void saveSessionIndex(Context context, List<ChatSession> list) {
        saveSessionIndexToAllDirs(getAllSessionsDirs(context), list);
    }

    private static void saveSessionIndexToAllDirs(List<File> dirs, List<ChatSession> list) {
        if (dirs == null || dirs.isEmpty() || list == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (ChatSession s : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.id);
                obj.put("title", s.title);
                obj.put("time", s.time);
                arr.put(obj);
            }
            byte[] bytes = arr.toString().getBytes("UTF-8");
            for (File dir : dirs) {
                try {
                    File indexFile = new File(dir, INDEX_FILE);
                    FileOutputStream fos = new FileOutputStream(indexFile);
                    fos.write(bytes);
                    fos.flush();
                    fos.close();
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 加载特定会话的所有历史对话消息
     */
    public static synchronized ChatSession loadSession(Context context, String sessionId) {
        ChatSession session = new ChatSession();
        session.id = sessionId;
        if (sessionId == null) return session;

        List<File> dirs = getAllSessionsDirs(context);
        File targetFile = null;
        for (File dir : dirs) {
            File f = new File(dir, "session_" + sessionId + ".json");
            if (f.exists() && f.length() > 10) {
                targetFile = f;
                break;
            }
        }

        if (targetFile == null) {
            return session;
        }

        try {
            String content = readFileContent(targetFile);
            if (content != null && content.length() > 0) {
                JSONObject root = new JSONObject(content.trim());
                session.title = root.optString("title", "对话");
                session.time = root.optString("time", "");

                JSONArray arr = root.optJSONArray("messages");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        int role = obj.optInt("role", ChatMessage.ROLE_USER);
                        String c = obj.optString("content", "");
                        String t = obj.optString("time", "");
                        session.messages.add(new ChatMessage(role, c, t));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return session;
    }

    /**
     * 保存单条会话至所有冗余存储目录
     */
    public static synchronized void saveSession(Context context, ChatSession session) {
        if (session == null || session.id == null) return;
        if (session.messages == null || session.messages.isEmpty()) return;

        try {
            JSONObject root = new JSONObject();
            root.put("id", session.id);
            root.put("title", session.title);
            root.put("time", session.time);

            JSONArray arr = new JSONArray();
            for (ChatMessage msg : session.messages) {
                JSONObject obj = new JSONObject();
                obj.put("role", msg.role);
                obj.put("content", msg.content);
                obj.put("time", msg.time);
                arr.put(obj);
            }
            root.put("messages", arr);

            byte[] jsonBytes = root.toString().getBytes("UTF-8");
            List<File> dirs = getAllSessionsDirs(context);

            for (File dir : dirs) {
                try {
                    File sessionFile = new File(dir, "session_" + session.id + ".json");
                    FileOutputStream fos = new FileOutputStream(sessionFile);
                    fos.write(jsonBytes);
                    fos.flush();
                    fos.close();
                } catch (Exception ignored) {}
            }

            // 更新索引记录
            List<ChatSession> list = syncAndRecoverSessions(context);
            boolean found = false;
            for (ChatSession s : list) {
                if (s.id.equals(session.id)) {
                    s.title = session.title;
                    s.time = session.time;
                    found = true;
                    break;
                }
            }
            if (!found) {
                ChatSession newEntry = new ChatSession();
                newEntry.id = session.id;
                newEntry.title = session.title;
                newEntry.time = session.time;
                list.add(0, newEntry);
            }
            saveSessionIndexToAllDirs(dirs, list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从所有存储目录彻底删除指定会话
     */
    public static synchronized void deleteSession(Context context, String sessionId) {
        if (sessionId == null) return;
        try {
            List<File> dirs = getAllSessionsDirs(context);
            for (File dir : dirs) {
                try {
                    File sessionFile = new File(dir, "session_" + sessionId + ".json");
                    if (sessionFile.exists()) {
                        sessionFile.delete();
                    }
                } catch (Exception ignored) {}
            }

            List<ChatSession> list = getSessionList(context);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id.equals(sessionId)) {
                    list.remove(i);
                    break;
                }
            }
            saveSessionIndexToAllDirs(dirs, list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ChatSession createNewSession(Context context) {
        String id = String.valueOf(System.currentTimeMillis());
        String time = new SimpleDateFormat("MM-dd HH:mm").format(new Date());
        ChatSession session = new ChatSession(id, "新会话", time);
        setCurrentSessionId(context, id);
        return session;
    }

    public static String getCurrentSessionId(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_CURRENT_SESSION_ID, null);
    }

    public static void setCurrentSessionId(Context context, String sessionId) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_CURRENT_SESSION_ID, sessionId).commit();
    }

    private static void migrateOldHistory(Context context) {
        try {
            File oldFile = new File(context.getFilesDir(), "chat_history.json");
            if (oldFile.exists() && oldFile.length() > 10) {
                List<ChatMessage> oldMsgs = ChatHistoryManager.loadHistory(context);
                if (oldMsgs != null && !oldMsgs.isEmpty()) {
                    String id = String.valueOf(System.currentTimeMillis());
                    String time = new SimpleDateFormat("MM-dd HH:mm").format(new Date());
                    String title = "历史会话";
                    for (ChatMessage m : oldMsgs) {
                        if (m.role == ChatMessage.ROLE_USER && m.content != null && m.content.length() > 0) {
                            title = m.content.trim();
                            if (title.length() > 16) title = title.substring(0, 16) + "...";
                            break;
                        }
                    }
                    ChatSession s = new ChatSession(id, title, time);
                    s.messages = oldMsgs;
                    saveSession(context, s);
                    setCurrentSessionId(context, id);
                }
            }
        } catch (Exception ignored) {}
    }

    private static String readFileContent(File file) {
        if (file == null || !file.exists()) return null;
        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            fis.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void copyFile(File src, File dest) {
        if (src == null || !src.exists() || dest == null) return;
        try {
            FileInputStream fis = new FileInputStream(src);
            FileOutputStream fos = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int read;
            while ((read = fis.read(buf)) > 0) {
                fos.write(buf, 0, read);
            }
            fos.flush();
            fos.close();
            fis.close();
        } catch (Exception ignored) {}
    }
}
