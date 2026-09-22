package com.chacha.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.keanbin.pinyinime.PinyinDecoderService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * AOSP 谷歌拼音原生引擎高级管理单例
 * - 自动释放二进制词库 dict_pinyin.dat 到内部存储
 * - 调用底层的动态规划分词与 Bigram 语言模型
 * - 支持全拼、简拼（声母缩写如 nh -> 你好）、长句分词与候选词生成
 * - 双层词频与自造词学习：
 *   1) AOSP 原生 usr_dict.dat (nativeImChoose / nativeImFlushCache)
 *   2) 即时动态高频词置顶加权缓存（按使用频次与最近选择时间实时浮动）
 */
public class AospPinyinEngine {
    private static final String TAG = "AospPinyinEngine";
    private static AospPinyinEngine instance;

    private boolean isInitialized = false;
    private boolean isInitializing = false;
    private String initError = null;
    private SharedPreferences freqPrefs;
    private Context appContext;

    // 连续输入自造词组智能学习状态
    private long lastChooseTime = 0;
    private final StringBuilder autoWordBuffer = new StringBuilder();
    private final StringBuilder autoPinyinBuffer = new StringBuilder();

    private AospPinyinEngine() {}

    public static synchronized AospPinyinEngine getInstance() {
        if (instance == null) {
            instance = new AospPinyinEngine();
        }
        return instance;
    }

    public synchronized boolean isInitialized() {
        return isInitialized;
    }

    public synchronized String getInitError() {
        return initError;
    }

    /**
     * 初始化拼音引擎与系统/用户词库
     */
    public synchronized void init(Context context) {
        if (isInitialized || isInitializing) return;
        isInitializing = true;
        initError = null;

        long start = System.currentTimeMillis();
        try {
            if (context != null) {
                appContext = context.getApplicationContext();
                freqPrefs = appContext.getSharedPreferences("pinyin_user_freq", Context.MODE_PRIVATE);
            }

            if (!PinyinDecoderService.isLoaded()) {
                initError = "Native library jni_pinyinime not loaded: " + PinyinDecoderService.getLoadError();
                Log.e(TAG, initError);
                return;
            }

            File filesDir = context.getFilesDir();
            if (!filesDir.exists()) {
                filesDir.mkdirs();
            }

            File dictFile = new File(filesDir, "dict_pinyin.dat");
            File usrDictFile = new File(filesDir, "usr_dict.dat");

            // 检查或复制 assets 中的 dict_pinyin.dat 到内部存储
            if (!dictFile.exists() || dictFile.length() < 500000) {
                Log.i(TAG, "Extracting dict_pinyin.dat from assets to " + dictFile.getAbsolutePath());
                InputStream is = context.getAssets().open("dict_pinyin.dat");
                FileOutputStream fos = new FileOutputStream(dictFile);
                byte[] buffer = new byte[32768];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
                fos.close();
                is.close();
                Log.i(TAG, "dict_pinyin.dat extracted, size: " + dictFile.length() + " bytes");
            }

            // 确保用户词典文件可用
            if (!usrDictFile.exists()) {
                try {
                    usrDictFile.createNewFile();
                } catch (Exception ignored) {}
            }

            byte[] sysDictBytes = toCString(dictFile.getAbsolutePath());
            byte[] usrDictBytes = toCString(usrDictFile.getAbsolutePath());

            boolean openSuccess = PinyinDecoderService.nativeImOpenDecoder(sysDictBytes, usrDictBytes);
            if (openSuccess) {
                PinyinDecoderService.nativeImSetMaxLens(32, 16);
                isInitialized = true;
                Log.i(TAG, "AOSP PinyinEngine initialized successfully in " + (System.currentTimeMillis() - start) + "ms");
            } else {
                initError = "nativeImOpenDecoder failed to open dictionary: " + dictFile.getAbsolutePath();
                Log.e(TAG, initError);
            }
        } catch (Exception e) {
            initError = "Exception in AospPinyinEngine.init: " + e.getMessage();
            Log.e(TAG, initError, e);
        } finally {
            isInitializing = false;
        }
    }

    /**
     * 查询拼音候选词列表
     * @param pinyin 用户输入的拼音或声母简拼
     * @param maxCandidates 最大候选词数量
     * @return 候选词列表
     */
    public synchronized List<String> search(String pinyin, int maxCandidates) {
        if (pinyin == null || pinyin.length() == 0 || !isInitialized) {
            return Collections.emptyList();
        }

        try {
            PinyinDecoderService.nativeImResetSearch();
            byte[] pyBytes = pinyin.toLowerCase().getBytes("ISO-8859-1");
            int totalChoices = PinyinDecoderService.nativeImSearch(pyBytes, pyBytes.length);

            List<String> rawResults = new ArrayList<String>();
            if (totalChoices > 0) {
                int fetchLimit = Math.min(totalChoices, Math.max(maxCandidates, 50));
                for (int i = 0; i < fetchLimit; i++) {
                    String choice = PinyinDecoderService.nativeImGetChoice(i);
                    if (choice != null && choice.length() > 0 && !rawResults.contains(choice)) {
                        rawResults.add(choice);
                    }
                }
            }

            // 如果输入包含多个音节（例如 feisi），额外获取首音节（如 fei）的纯单字候选，确保用户在第1页始终能选单字自造词组
            List<String> syls = PinyinSplitter.splitSyllables(pinyin);
            List<String> firstSylSingleChars = new ArrayList<String>();
            if (syls.size() > 1) {
                String firstSyl = syls.get(0);
                try {
                    PinyinDecoderService.nativeImResetSearch();
                    byte[] firstBytes = firstSyl.toLowerCase().getBytes("ISO-8859-1");
                    int firstChoices = PinyinDecoderService.nativeImSearch(firstBytes, firstBytes.length);
                    for (int i = 0; i < Math.min(firstChoices, 30); i++) {
                        String choice = PinyinDecoderService.nativeImGetChoice(i);
                        // 严格过滤单字（长度为1），确保单字造词状态机精准运行
                        if (choice != null && choice.length() == 1 && !firstSylSingleChars.contains(choice)) {
                            firstSylSingleChars.add(choice);
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 0. 最高优先级：匹配用户自造词库（带 ★ 标识）
            List<String> customWords = (appContext != null) ?
                    UserPhraseManager.getInstance().matchPhrases(appContext, pinyin) :
                    Collections.<String>emptyList();

            List<String> finalResults = new ArrayList<String>();
            // 1. 自造词置顶（如果用户此前已自造该词组，强制绝对置顶）
            for (String cw : customWords) {
                if (!finalResults.contains("★" + cw)) {
                    finalResults.add("★" + cw);
                }
            }

            // 2. 动态词频调整：读取该拼音下用户以往的选词频率
            if (freqPrefs != null) {
                String pyKey = pinyin.toLowerCase();
                String record = freqPrefs.getString("py_" + pyKey, "");
                List<FreqEntry> userEntries = parseFreqEntries(record);
                for (FreqEntry entry : userEntries) {
                    if (!finalResults.contains(entry.word)) {
                        finalResults.add(entry.word);
                    }
                }
            }

            // 3. 原生词库候选词与首音节单字精细编排
            if (syls.size() > 1) {
                // 多音节输入时（如 feisi）：
                // 先放入最多 2 个原生整词候选（若词库有），随后立即放置首音节单字（如 飞、非、肥、废...）
                // 确保第 1 页（9个候选词）中必然包含单字，用户无需翻页即可直接选字造词！
                int phraseCount = 0;
                for (String w : rawResults) {
                    if (phraseCount < 2 && !finalResults.contains(w)) {
                        finalResults.add(w);
                        phraseCount++;
                    }
                }
                for (String c : firstSylSingleChars) {
                    if (!finalResults.contains(c)) {
                        finalResults.add(c);
                    }
                }
                for (String w : rawResults) {
                    if (!finalResults.contains(w)) {
                        finalResults.add(w);
                    }
                }
            } else {
                for (String w : rawResults) {
                    if (!finalResults.contains(w)) {
                        finalResults.add(w);
                    }
                }
            }

            if (finalResults.size() > maxCandidates) {
                return new ArrayList<String>(finalResults.subList(0, maxCandidates));
            }
            return finalResults;
        } catch (Exception e) {
            Log.e(TAG, "Error in search: " + pinyin, e);
            return Collections.emptyList();
        }
    }

    /**
     * 选词确认并触发词频自动调整与自造词沉淀
     * @param pinyin 正在输入的拼音
     * @param choiceIndex 原生候选索引
     * @param word 用户选定的汉字词组
     */
    public synchronized void chooseCandidate(String pinyin, int choiceIndex, String word) {
        if (!isInitialized || pinyin == null || pinyin.length() == 0 || word == null || word.length() == 0) {
            return;
        }

        // 如果包含自造词标识 ★，予以去除
        String pureWord = word.startsWith("★") ? word.substring(1) : word;

        // 1. 如果是自造词，更新自造词库的使用频次
        if (word.startsWith("★") && appContext != null) {
            UserPhraseManager.getInstance().recordUsage(appContext, pureWord);
        }

        // 2. 连续选字智能自造词学习（4秒内连续选字自动组成新词组）
        learnAutoPhrase(pinyin, pureWord);

        // 3. 通知 AOSP 原生引擎更新内部 Bigram 词典模型与用户词库 usr_dict.dat
        try {
            if (choiceIndex >= 0) {
                PinyinDecoderService.nativeImChoose(choiceIndex);
                PinyinDecoderService.nativeImFlushCache();
            }
        } catch (Exception e) {
            Log.w(TAG, "native choose failed: " + e.getMessage());
        }

        // 4. 更新 Java 层即时动态词频加权缓存（按使用频次与时间权重置顶）
        try {
            if (freqPrefs != null) {
                String pyKey = pinyin.toLowerCase();
                String record = freqPrefs.getString("py_" + pyKey, "");
                List<FreqEntry> entries = parseFreqEntries(record);
                boolean found = false;
                for (FreqEntry entry : entries) {
                    if (entry.word.equals(pureWord)) {
                        entry.count++;
                        entry.timestamp = System.currentTimeMillis();
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    entries.add(new FreqEntry(pureWord, 1, System.currentTimeMillis()));
                }
                // 排序：高频排前面；频次相同按最新使用时间
                Collections.sort(entries, new Comparator<FreqEntry>() {
                    @Override
                    public int compare(FreqEntry o1, FreqEntry o2) {
                        if (o1.count != o2.count) {
                            return o2.count - o1.count;
                        }
                        return (o2.timestamp > o1.timestamp) ? 1 : ((o2.timestamp < o1.timestamp) ? -1 : 0);
                    }
                });
                // 保持最多 12 条高频记忆
                if (entries.size() > 12) {
                    entries = new ArrayList<FreqEntry>(entries.subList(0, 12));
                }
                freqPrefs.edit().putString("py_" + pyKey, serializeFreqEntries(entries)).apply();
            }
        } catch (Exception e) {
            Log.w(TAG, "user freq update failed: " + e.getMessage());
        }
    }

    private void learnAutoPhrase(String pinyin, String pureWord) {
        long now = System.currentTimeMillis();
        String cleanPy = pinyin.toLowerCase().replaceAll("[^a-z]", "");
        if (now - lastChooseTime < 4000 && autoWordBuffer.length() > 0 && autoWordBuffer.length() < 10) {
            autoWordBuffer.append(pureWord);
            autoPinyinBuffer.append(cleanPy);
            if (appContext != null && autoWordBuffer.length() >= 2) {
                UserPhraseManager.getInstance().addPhrase(appContext, autoWordBuffer.toString(), autoPinyinBuffer.toString());
            }
        } else {
            autoWordBuffer.setLength(0);
            autoPinyinBuffer.setLength(0);
            autoWordBuffer.append(pureWord);
            autoPinyinBuffer.append(cleanPy);
        }
        lastChooseTime = now;
    }

    /**
     * 获取切分格式化后的拼音字符串（如 ni'hao、jin'tian）
     */
    public synchronized String getFormattedPinyin(String rawPinyin) {
        if (rawPinyin == null || rawPinyin.length() == 0) return "";
        if (!isInitialized) return rawPinyin;

        try {
            int[] splStart = PinyinDecoderService.nativeImGetSplStart();
            if (splStart != null && splStart.length > 2) {
                StringBuilder sb = new StringBuilder();
                int numSpl = splStart[0]; // 第一个元素通常是音节段数
                if (numSpl > 1 && numSpl + 1 <= splStart.length) {
                    for (int i = 1; i <= numSpl; i++) {
                        int s = splStart[i];
                        int e = (i < numSpl) ? splStart[i + 1] : rawPinyin.length();
                        if (s >= 0 && e <= rawPinyin.length() && s < e) {
                            if (sb.length() > 0) sb.append("'");
                            sb.append(rawPinyin.substring(s, e));
                        }
                    }
                    if (sb.length() > 0) {
                        return sb.toString();
                    }
                }
            }
        } catch (Exception ignored) {}

        return rawPinyin;
    }

    /**
     * 重置拼音检索缓存
     */
    public synchronized void reset() {
        if (isInitialized) {
            try {
                PinyinDecoderService.nativeImResetSearch();
            } catch (Exception ignored) {}
        }
    }

    /**
     * 释放解码器
     */
    public synchronized void destroy() {
        if (isInitialized) {
            try {
                PinyinDecoderService.nativeImCloseDecoder();
            } catch (Exception ignored) {}
            isInitialized = false;
        }
    }

    private static byte[] toCString(String str) {
        if (str == null) return new byte[]{0};
        byte[] b = str.getBytes();
        byte[] ret = new byte[b.length + 1];
        System.arraycopy(b, 0, ret, 0, b.length);
        ret[b.length] = 0;
        return ret;
    }

    // 辅助数据结构与序列化
    private static class FreqEntry {
        String word;
        int count;
        long timestamp;

        FreqEntry(String word, int count, long timestamp) {
            this.word = word;
            this.count = count;
            this.timestamp = timestamp;
        }
    }

    private static List<FreqEntry> parseFreqEntries(String data) {
        List<FreqEntry> list = new ArrayList<FreqEntry>();
        if (data == null || data.length() == 0) return list;
        String[] parts = data.split("\\|");
        for (String part : parts) {
            if (part == null || part.length() == 0) continue;
            String[] fields = part.split(":");
            if (fields.length >= 2) {
                String w = fields[0];
                int c = 1;
                long t = 0;
                try {
                    c = Integer.parseInt(fields[1]);
                    if (fields.length >= 3) {
                        t = Long.parseLong(fields[2]);
                    }
                } catch (Exception ignored) {}
                list.add(new FreqEntry(w, c, t));
            }
        }
        return list;
    }

    private static String serializeFreqEntries(List<FreqEntry> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            FreqEntry e = list.get(i);
            if (i > 0) sb.append("|");
            sb.append(e.word).append(":").append(e.count).append(":").append(e.timestamp);
        }
        return sb.toString();
    }
}
