import os
import subprocess

jni_dir = "/home/admin/pinyin_unzip/PinyinIme-master/jni"

android_mk = """LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := jni_pinyinime
LOCAL_SRC_FILES := \\
	android/com_gaoxin_guangsuime_PinyinDecoderService.cpp \\
	share/dictbuilder.cpp \\
	share/dictlist.cpp \\
	share/dicttrie.cpp \\
	share/lpicache.cpp \\
	share/matrixsearch.cpp \\
	share/mystdlib.cpp \\
	share/ngram.cpp \\
	share/pinyinime.cpp \\
	share/searchutility.cpp \\
	share/spellingtable.cpp \\
	share/spellingtrie.cpp \\
	share/splparser.cpp \\
	share/userdict.cpp \\
	share/utf16char.cpp \\
	share/utf16reader.cpp \\
	share/sync.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_LDLIBS := -llog
LOCAL_CFLAGS := -O3 -fPIC -Wno-narrowing -Wno-error

include $(BUILD_SHARED_LIBRARY)
"""

application_mk = """APP_ABI := armeabi-v7a arm64-v8a
APP_PLATFORM := android-21
APP_STL := c++_static
"""

with open(os.path.join(jni_dir, "Android.mk"), "w", encoding="utf-8") as f:
    f.write(android_mk)

with open(os.path.join(jni_dir, "Application.mk"), "w", encoding="utf-8") as f:
    f.write(application_mk)

# Fix GetStringChars(..., false) -> NULL
cpp_path = os.path.join(jni_dir, "android", "com_gaoxin_guangsuime_PinyinDecoderService.cpp")
with open(cpp_path, "r", encoding="utf-8", errors="ignore") as f:
    cpp_src = f.read()
cpp_src = cpp_src.replace("(*env).GetStringChars(fixed_str, false)", "(*env).GetStringChars(fixed_str, NULL)")
cpp_src = cpp_src.replace("GetStringChars(fixed_str, false)", "GetStringChars(fixed_str, NULL)")
with open(cpp_path, "w", encoding="utf-8") as f:
    f.write(cpp_src)

print("Android.mk, Application.mk and C++ source patched successfully.")

ndk_build = "/home/admin/ndk/android-ndk-r26c/ndk-build"
cmd = [ndk_build, f"NDK_PROJECT_PATH={os.path.dirname(jni_dir)}", f"APP_BUILD_SCRIPT={os.path.join(jni_dir, 'Android.mk')}", f"NDK_APPLICATION_MK={os.path.join(jni_dir, 'Application.mk')}"]

print("Running:", " ".join(cmd))
res = subprocess.run(cmd, capture_output=True, text=True)
print("STDOUT:\n", res.stdout)
print("STDERR:\n", res.stderr)
if res.returncode == 0:
    print("NDK BUILD SUCCESS!")
else:
    print(f"NDK BUILD FAILED (code {res.returncode})")
