# -*- coding: utf-8 -*-
import os
import gzip
import jieba
from pypinyin import pinyin, Style
from collections import defaultdict

def build_dict():
    extra_words = [
        '帮我', '好的', '请问', '给我', '我想', '我们可以', '你可以', '这个', '那个', '什么', '怎么',
        '为什么', '如何', '我们', '你们', '他们', '自己', '写个', '做个', '看看', '查查', '一下',
        '一点', '一些', '还有', '没有', '不是', '就是', '如果是', '怎么样', '怎么样呢', '怎么办',
        '蓝牙', '键盘', '阅读器', '讯飞', '起点', '墨水屏', '会话', '历史', '设置', '人工智能',
        '你好', '写代码', '清空', '测试', '写一个', '写一段', '解释一下', '总结一下', '翻译成',
        '什么是', '怎么用', '如何做', '帮我写', '帮我查', '帮我做', '能帮我', '帮我看下',
        '大模型', '提示词', '回答', '问题', '联网', '服务器', '配置', '刷新', '保存', '退出',
        '好的谢谢', '非常感谢', '不客气', '辛苦了', '拜拜', '再见'
    ]

    dict_path = os.path.join(os.path.dirname(jieba.__file__), 'dict.txt')
    words = []
    with open(dict_path, 'r', encoding='utf-8') as f:
        for line in f:
            p = line.strip().split()
            if len(p) >= 2 and all('\u4e00' <= ch <= '\u9fff' for ch in p[0]):
                words.append((p[0], int(p[1])))

    for ew in extra_words:
        if all('\u4e00' <= ch <= '\u9fff' for ch in ew):
            words.append((ew, 10000000))

    words.sort(key=lambda x: x[1], reverse=True)

    py_map = defaultdict(list)

    # 1. multi words (freq >= 15)
    for w, f in words:
        if 2 <= len(w) <= 6 and f >= 15:
            py = ''.join([p[0] for p in pinyin(w, style=Style.NORMAL)])
            if w not in py_map[py]:
                py_map[py].append(w)

    # 2. single chars
    for w, f in words:
        if len(w) == 1:
            py = ''.join([p[0] for p in pinyin(w, style=Style.NORMAL)])
            if w not in py_map[py]:
                py_map[py].append(w)

    # 3. ensure all unicode CJK unified ideographs
    for code in range(0x4E00, 0x9FA5 + 1):
        ch = chr(code)
        py = ''.join([p[0] for p in pinyin(ch, style=Style.NORMAL)])
        if py and ch not in py_map[py]:
            py_map[py].append(ch)

    lines = []
    for py in sorted(py_map.keys()):
        cands = py_map[py][:20]
        line = py + '=' + ','.join(cands)
        lines.append(line)

    raw_text = '\n'.join(lines)
    raw_bytes = raw_text.encode('utf-8')
    gz_bytes = gzip.compress(raw_bytes, compresslevel=9)

    out_dir = os.path.join(os.path.dirname(__file__), '..', 'assets')
    os.makedirs(out_dir, exist_ok=True)
    out_file = os.path.join(out_dir, 'pinyin_dict.dat')
    with open(out_file, 'wb') as f:
        f.write(gz_bytes)

    print(f"Generated {len(py_map)} keys.")
    print(f"Raw UTF-8 size: {len(raw_bytes)} bytes.")
    print(f"Gzip size: {len(gz_bytes)} bytes written to {out_file}")

    # Verify a few lookups
    for test in ['nihao', 'ceshi', 'lanya', 'jianpan', 'bangwo', 'haode']:
        print(f"Test '{test}': {py_map.get(test, [])[:5]}")

if __name__ == '__main__':
    build_dict()
