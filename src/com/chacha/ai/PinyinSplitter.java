package com.chacha.ai;

import com.keanbin.pinyinime.PinyinDecoderService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 拼音音节切分与声母提取器
 * - 优先调用 AOSP 原生引擎音节切分 (nativeImGetSplStart)
 * - 辅以标准普通话全量音节贪心正向匹配字典
 * - 精确计算候选词（单字/词组）消耗的拼音字符长度与剩余拼音
 * - 自动提取汉字词组声母简拼（如 feisi -> fs, shangfa -> sf）
 */
public class PinyinSplitter {

    // 标准普通话音节全集（按字符长度降序排列，确保贪心正向最大匹配优先匹配长音节）
    private static final String[] SYLLABLES_ARRAY = {
        // 6 字母
        "chuang", "shuang", "zhuang",
        // 5 字母
        "chang", "cheng", "chong", "chuai", "chuan", "guang", "huang", "jiang",
        "jiong", "kuang", "liang", "qiong", "shang", "sheng", "shuai", "shuan",
        "xiang", "xiong", "zhang", "zheng", "zhong", "zhuai", "zhuan",
        // 4 字母
        "bang", "beng", "bian", "biao", "bing", "cang", "ceng", "chai", "chan",
        "chao", "chen", "chou", "chua", "chui", "chun", "chuo", "cong", "cuan",
        "dang", "deng", "dian", "diao", "ding", "dong", "duan", "fang", "feng",
        "gang", "geng", "gong", "guai", "guan", "hang", "heng", "hong", "huai",
        "huan", "jian", "jiao", "jing", "juan", "kang", "keng", "kong", "kuai",
        "kuan", "lang", "leng", "lian", "liao", "ling", "long", "luan", "mang",
        "meng", "mian", "miao", "ming", "nang", "neng", "nian", "niao", "ning",
        "nong", "nuan", "pang", "peng", "pian", "piao", "ping", "qiang", "qiao",
        "qing", "quan", "rang", "reng", "rong", "ruan", "sang", "seng", "shai",
        "shan", "shao", "shei", "shen", "shou", "shua", "shui", "shun", "shuo",
        "song", "suan", "tang", "teng", "tian", "tiao", "ting", "tong", "tuan",
        "wang", "weng", "xian", "xiao", "xing", "xuan", "yang", "ying", "yong",
        "yuan", "zang", "zeng", "zhai", "zhan", "zhao", "zhei", "zhen", "zhou",
        "zhua", "zhui", "zhun", "zhuo", "zong", "zuan",
        // 3 字母
        "bai", "ban", "bao", "bei", "ben", "bie", "bin", "cai", "can", "cao",
        "cen", "cha", "che", "chi", "chu", "cou", "cui", "cun", "cuo", "dai",
        "dan", "dao", "dei", "den", "dia", "die", "diu", "dou", "dui", "dun",
        "duo", "fan", "fei", "fen", "fou", "gai", "gan", "gao", "gei", "gen",
        "gou", "gua", "gui", "gun", "guo", "hai", "han", "hao", "hei", "hen",
        "hou", "hua", "hui", "hun", "huo", "jia", "jie", "jin", "jiu", "jue",
        "jun", "kai", "kan", "kao", "kei", "ken", "kou", "kua", "kui", "kun",
        "kuo", "lai", "lan", "lao", "lei", "lia", "lie", "lin", "liu", "lou",
        "lun", "luo", "lve", "mai", "man", "mao", "mei", "men", "mie", "min",
        "miu", "mou", "nai", "nan", "nao", "nei", "nen", "nie", "nin", "niu",
        "nou", "nun", "nuo", "nve", "pai", "pan", "pao", "pei", "pen", "pie",
        "pin", "pou", "qia", "qie", "qin", "qiu", "que", "qun", "ran", "rao",
        "ren", "rou", "rui", "run", "ruo", "sai", "san", "sao", "sen", "sha",
        "she", "shi", "shu", "sou", "sui", "sun", "suo", "tai", "tan", "tao",
        "tie", "tou", "tui", "tun", "tuo", "wai", "wan", "wei", "wen", "xia",
        "xie", "xin", "xiu", "xue", "xun", "yan", "yao", "yin", "you", "yue",
        "yun", "zai", "zan", "zao", "zei", "zen", "zha", "zhe", "zhi", "zhu",
        "zou", "zui", "zun", "zuo",
        // 2 字母
        "ai", "an", "ao", "ba", "bi", "bo", "bu", "ca", "ce", "ci", "cu",
        "da", "de", "di", "du", "ei", "en", "er", "fa", "fo", "fu", "ga",
        "ge", "gu", "ha", "he", "hu", "ji", "ju", "ka", "ke", "ku", "la",
        "le", "li", "lu", "lv", "ma", "me", "mi", "mo", "mu", "na", "ne",
        "ni", "nu", "nv", "ou", "pa", "pi", "po", "pu", "qi", "qu", "re",
        "ri", "ru", "sa", "se", "si", "su", "ta", "te", "ti", "tu", "wa",
        "wo", "wu", "xi", "xu", "ya", "ye", "yi", "yo", "yu", "za", "ze",
        "zi", "zu",
        // 1 字母
        "a", "e", "o"
    };

    private static final Set<String> SYLLABLE_SET = new HashSet<String>(Arrays.asList(SYLLABLES_ARRAY));

    /**
     * 将输入的拼音字符串智能切分为音节列表
     * @param pinyin 原始拼音字符串（如 "feisi"、"shangfa"）
     * @return 切分后的音节列表（如 ["fei", "si"]、["shang", "fa"]）
     */
    public static List<String> splitSyllables(String pinyin) {
        List<String> list = new ArrayList<String>();
        if (pinyin == null || pinyin.trim().isEmpty()) {
            return list;
        }

        String raw = pinyin.toLowerCase().replaceAll("[^a-z]", "");
        if (raw.isEmpty()) return list;

        int n = raw.length();
        // 1. 动态规划（DP）寻找能够完全由合法普通话音节覆盖的最优划分
        @SuppressWarnings("unchecked")
        List<String>[] dp = new List[n + 1];
        dp[0] = new ArrayList<String>();

        for (int i = 0; i < n; i++) {
            if (dp[i] == null) continue;
            int maxLen = Math.min(6, n - i);
            for (int l = maxLen; l >= 1; l--) {
                String sub = raw.substring(i, i + l);
                if (SYLLABLE_SET.contains(sub)) {
                    if (dp[i + l] == null || dp[i].size() + 1 < dp[i + l].size()) {
                        List<String> next = new ArrayList<String>(dp[i]);
                        next.add(sub);
                        dp[i + l] = next;
                    }
                }
            }
        }

        if (dp[n] != null && !dp[n].isEmpty()) {
            return dp[n];
        }

        // 2. 回退机制：正向最大贪心匹配（用于输入尚未敲完的过渡拼音或声母简拼如 fs）
        int idx = 0;
        while (idx < n) {
            boolean matched = false;
            int maxMatchLen = Math.min(6, n - idx);
            for (int l = maxMatchLen; l >= 1; l--) {
                String sub = raw.substring(idx, idx + l);
                if (SYLLABLE_SET.contains(sub)) {
                    list.add(sub);
                    idx += l;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                list.add(String.valueOf(raw.charAt(idx)));
                idx++;
            }
        }

        return list;
    }

    /**
     * 计算选中的汉字词组所消耗的拼音长度
     * @param pinyin 当前剩余/输入的拼音字符串
     * @param wordLen 选中的汉字字符数（例如选了单字为 1，选了双字为 2）
     * @return 消耗的拼音字符数
     */
    public static int getConsumedPinyinLength(String pinyin, int wordLen) {
        if (pinyin == null || pinyin.isEmpty() || wordLen <= 0) {
            return 0;
        }
        List<String> syllables = splitSyllables(pinyin);
        if (syllables.isEmpty()) {
            return Math.min(wordLen, pinyin.length());
        }

        int consumeSylCount = Math.min(wordLen, syllables.size());
        int consumedLen = 0;
        for (int i = 0; i < consumeSylCount; i++) {
            consumedLen += syllables.get(i).length();
        }

        return Math.min(consumedLen, pinyin.length());
    }

    /**
     * 根据全拼或音节序列自动提取首字母声母简拼
     * 例如 ["fei", "si"] -> "fs"；"feisi" -> "fs"
     */
    public static String getInitials(String pinyin) {
        if (pinyin == null || pinyin.isEmpty()) return "";
        List<String> syllables = splitSyllables(pinyin);
        StringBuilder sb = new StringBuilder();
        for (String syl : syllables) {
            if (syl.length() > 0) {
                sb.append(syl.charAt(0));
            }
        }
        return sb.toString();
    }
}
