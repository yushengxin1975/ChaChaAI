import subprocess
import os

jni_dir = "/home/admin/pinyin_unzip/PinyinIme-master/jni"

test_cpp = """#include <stdio.h>
#include "include/pinyinime.h"

using namespace ime_pinyin;

int main() {
    printf("Testing im_open_decoder...\\n");
    bool ret = im_open_decoder("/tmp/pinyin_bin/dict_pinyin.dat", "/tmp/usr_dict.dat");
    printf("im_open_decoder result: %d\\n", ret ? 1 : 0);
    if (ret) {
        size_t count = im_search("nihao", 5);
        printf("Search nihao count: %zu\\n", count);
        char16 cand[256];
        for (size_t i = 0; i < count && i < 5; i++) {
            size_t len = im_get_candidate(i, cand, 255);
            printf("Cand %zu len: %zu\\n", i, len);
        }
    }
    return 0;
}
"""

with open(os.path.join(jni_dir, "test_main.cpp"), "w", encoding="utf-8") as f:
    f.write(test_cpp)

# Compile with g++ on 64-bit Linux!
sources = [
    "test_main.cpp",
    "share/dictbuilder.cpp",
    "share/dictlist.cpp",
    "share/dicttrie.cpp",
    "share/lpicache.cpp",
    "share/matrixsearch.cpp",
    "share/mystdlib.cpp",
    "share/ngram.cpp",
    "share/pinyinime.cpp",
    "share/searchutility.cpp",
    "share/spellingtable.cpp",
    "share/spellingtrie.cpp",
    "share/splparser.cpp",
    "share/userdict.cpp",
    "share/utf16char.cpp",
    "share/utf16reader.cpp",
    "share/sync.cpp",
]

cmd = ["g++", "-O2", "-Iinclude", "-o", "test_pinyin"] + sources
print("Compiling 64-bit test binary...")
res = subprocess.run(cmd, cwd=jni_dir, capture_output=True, text=True)
if res.returncode != 0:
    print("Compilation failed:", res.stderr)
else:
    print("Compilation successful! Running ./test_pinyin:")
    run_res = subprocess.run(["./test_pinyin"], cwd=jni_dir, capture_output=True, text=True)
    print("STDOUT:\n", run_res.stdout)
    print("STDERR:\n", run_res.stderr)
