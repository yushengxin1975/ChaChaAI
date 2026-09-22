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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 用户自造词组与自定义词库管理器
 * - 支持用户自主录入专业词组、缩写、专有名词及其全拼/简拼声母
 * - 持久化存储至内部 user_phrases.json
 * - 结合全拼匹配与首字母简拼（如 dhjk -> 东航金控、sftcj -> 商发陶瓷基）
 * - 智能按使用频率和最近选词时间排序置顶
 */
public class UserPhraseManager {
    private static final String FILE_NAME = "user_phrases.json";
    private static UserPhraseManager instance;

    public static class Phrase {
        public String word;
        public String pinyin;
        public String initials;
        public int count;
        public int missCount;
        public long timestamp;

        public Phrase(String word, String pinyin, String initials, int count, int missCount, long timestamp) {
            this.word = word;
            this.pinyin = pinyin;
            this.initials = initials;
            this.count = count;
            this.missCount = missCount;
            this.timestamp = timestamp;
        }

        public Phrase(String word, String pinyin, String initials, int count, long timestamp) {
            this(word, pinyin, initials, count, 0, timestamp);
        }

        public int getEffectiveScore() {
            // 有效分值：选词次数 - (连续未选忽略次数 * 2)
            return count - (missCount * 2);
        }

        public boolean isDegraded() {
            // 连续 2 次及以上在对应拼音下被用户忽略，判定为动态降级
            return missCount >= 2;
        }
    }

    private final List<Phrase> phraseList = new ArrayList<Phrase>();
    private boolean loaded = false;

    private UserPhraseManager() {}

    public static synchronized UserPhraseManager getInstance() {
        if (instance == null) {
            instance = new UserPhraseManager();
        }
        return instance;
    }

    public static List<File> getAllPhraseFiles(Context context) {
        List<File> files = new ArrayList<File>();
        if (context == null) return files;

        // 1. 内部私有存储
        try {
            File intFile = new File(context.getFilesDir(), FILE_NAME);
            files.add(intFile);
        } catch (Exception ignored) {}

        // 2. SD卡公共持久化存储（即便卸载重装、覆盖安装也不会丢失）
        try {
            File extRoot = android.os.Environment.getExternalStorageDirectory();
            if (extRoot != null) {
                File dir = new File(extRoot, "ChaChaAI");
                if (!dir.exists()) dir.mkdirs();
                if (dir.exists()) {
                    files.add(new File(dir, FILE_NAME));
                }
            }
        } catch (Exception ignored) {}

        // 3. 外部应用私有存储
        try {
            File extApp = context.getExternalFilesDir(null);
            if (extApp != null) {
                files.add(new File(extApp, FILE_NAME));
            }
        } catch (Exception ignored) {}

        return files;
    }

    public synchronized void syncStorage(Context context) {
        if (context == null) return;
        loaded = false;
        load(context);
    }

    public synchronized void load(Context context) {
        if (loaded || context == null) return;
        phraseList.clear();

        List<File> files = getAllPhraseFiles(context);
        boolean anyLoaded = false;

        for (File file : files) {
            if (!file.exists() || file.length() < 2) continue;
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

                JSONArray arr = new JSONArray(sb.toString().trim());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String word = obj.optString("word", "");
                    String pinyin = obj.optString("pinyin", "");
                    String initials = obj.optString("initials", "");
                    int count = obj.optInt("count", 1);
                    int missCount = obj.optInt("missCount", 0);
                    long ts = obj.optLong("timestamp", 0);
                    if (word.length() > 0) {
                        boolean exists = false;
                        for (Phrase p : phraseList) {
                            if (p.word.equals(word)) {
                                exists = true;
                                if (count > p.count) p.count = count;
                                p.missCount = missCount;
                                if (ts > p.timestamp) p.timestamp = ts;
                                break;
                            }
                        }
                        if (!exists) {
                            phraseList.add(new Phrase(word, pinyin, initials, count, missCount, ts));
                        }
                    }
                }
                anyLoaded = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        sortPhrases();
        loaded = true;

        // 如果从外部存储恢复了词组，同步回写到所有存储路径
        if (anyLoaded) {
            save(context);
        }
    }

    private void save(Context context) {
        if (context == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (Phrase p : phraseList) {
                JSONObject obj = new JSONObject();
                obj.put("word", p.word);
                obj.put("pinyin", p.pinyin);
                obj.put("initials", p.initials);
                obj.put("count", p.count);
                obj.put("missCount", p.missCount);
                obj.put("timestamp", p.timestamp);
                arr.put(obj);
            }
            byte[] bytes = arr.toString().getBytes("UTF-8");
            List<File> files = getAllPhraseFiles(context);
            for (File file : files) {
                try {
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(bytes);
                    fos.flush();
                    fos.close();
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void addPhrase(Context context, String word, String inputPinyin) {
        if (word == null || word.trim().isEmpty()) return;
        word = word.trim();
        if (context == null) return;
        load(context);

        String clean = (inputPinyin != null) ? inputPinyin.toLowerCase().trim() : "";
        String pinyin = clean;
        String initials = clean;

        // 如果包含逗号或空格分隔（如 "donghangjinkong, dhjk"）
        if (clean.contains(",") || clean.contains(" ") || clean.contains(";")) {
            String[] parts = clean.split("[, ;]+");
            if (parts.length >= 2) {
                if (parts[0].length() > parts[1].length()) {
                    pinyin = parts[0].replaceAll("[^a-z]", "");
                    initials = parts[1].replaceAll("[^a-z]", "");
                } else {
                    pinyin = parts[1].replaceAll("[^a-z]", "");
                    initials = parts[0].replaceAll("[^a-z]", "");
                }
            } else if (parts.length == 1) {
                pinyin = parts[0].replaceAll("[^a-z]", "");
                initials = pinyin;
            }
        } else {
            pinyin = clean.replaceAll("[^a-z]", "");
            initials = PinyinSplitter.getInitials(pinyin);
            if (initials.isEmpty()) {
                initials = pinyin;
            }
        }

        // 检查是否已存在同名词组，存在则更新拼音和频次
        for (Phrase p : phraseList) {
            if (p.word.equals(word)) {
                if (!pinyin.isEmpty()) p.pinyin = pinyin;
                if (!initials.isEmpty()) p.initials = initials;
                p.count++;
                p.missCount = 0; // 重新使用，消除忽略惩罚
                p.timestamp = System.currentTimeMillis();
                sortPhrases();
                save(context);
                return;
            }
        }

        // 新造词赋予初始权重 3 与 0 忽略数，确保初始排在第 1 位
        phraseList.add(new Phrase(word, pinyin, initials, 3, 0, System.currentTimeMillis()));
        sortPhrases();
        save(context);
    }

    public synchronized void deletePhrase(Context context, String word) {
        if (context == null || word == null) return;
        load(context);
        for (int i = 0; i < phraseList.size(); i++) {
            if (phraseList.get(i).word.equals(word)) {
                phraseList.remove(i);
                save(context);
                return;
            }
        }
    }

    public synchronized List<Phrase> getAllPhrases(Context context) {
        if (context == null) return Collections.emptyList();
        load(context);
        return new ArrayList<Phrase>(phraseList);
    }

    public synchronized List<Phrase> getMatchingPhrases(Context context, String queryPinyin) {
        if (context == null || queryPinyin == null || queryPinyin.trim().isEmpty()) {
            return Collections.emptyList();
        }
        load(context);
        String q = queryPinyin.toLowerCase().replaceAll("[^a-z]", "");
        if (q.isEmpty()) return Collections.emptyList();

        List<Phrase> exactMatches = new ArrayList<Phrase>();
        List<Phrase> prefixMatches = new ArrayList<Phrase>();

        for (Phrase p : phraseList) {
            if ((p.pinyin != null && p.pinyin.equals(q)) ||
                (p.initials != null && p.initials.equals(q))) {
                if (!exactMatches.contains(p)) {
                    exactMatches.add(p);
                }
            } else if ((p.pinyin != null && p.pinyin.startsWith(q)) ||
                       (p.initials != null && p.initials.startsWith(q))) {
                if (!exactMatches.contains(p) && !prefixMatches.contains(p)) {
                    prefixMatches.add(p);
                }
            }
        }

        List<Phrase> results = new ArrayList<Phrase>(exactMatches);
        results.addAll(prefixMatches);
        return results;
    }

    public synchronized List<String> matchPhrases(Context context, String queryPinyin) {
        List<Phrase> list = getMatchingPhrases(context, queryPinyin);
        List<String> words = new ArrayList<String>();
        for (Phrase p : list) {
            if (!words.contains(p.word)) {
                words.add(p.word);
            }
        }
        return words;
    }

    public synchronized void recordUsage(Context context, String word) {
        if (context == null || word == null) return;
        load(context);
        for (Phrase p : phraseList) {
            if (p.word.equals(word)) {
                p.count++;
                p.missCount = 0; // 用户选中该自造词，重置未选计数，恢复顶级权重！
                p.timestamp = System.currentTimeMillis();
                sortPhrases();
                save(context);
                return;
            }
        }
    }

    /**
     * 动态降级：当用户输入了该拼音却连续选了其他词时，记录未选忽略次数
     */
    public synchronized void recordMiss(Context context, String pinyin, String chosenWord) {
        if (context == null || pinyin == null || pinyin.trim().isEmpty()) return;
        load(context);
        String q = pinyin.toLowerCase().replaceAll("[^a-z]", "");
        if (q.isEmpty()) return;

        boolean changed = false;
        for (Phrase p : phraseList) {
            boolean matches = (p.pinyin != null && p.pinyin.equals(q)) ||
                              (p.initials != null && p.initials.equals(q));
            if (matches && (chosenWord == null || !p.word.equals(chosenWord))) {
                p.missCount++;
                changed = true;
            }
        }
        if (changed) {
            sortPhrases();
            save(context);
        }
    }

    private void sortPhrases() {
        Collections.sort(phraseList, new Comparator<Phrase>() {
            @Override
            public int compare(Phrase o1, Phrase o2) {
                int score1 = o1.getEffectiveScore();
                int score2 = o2.getEffectiveScore();
                if (score1 != score2) {
                    return score2 - score1;
                }
                return Long.compare(o2.timestamp, o1.timestamp);
            }
        });
    }
}
