package com.keanbin.pinyinime;

import android.util.Log;
import java.io.FileDescriptor;

/**
 * AOSP 谷歌拼音 JNI 解码核心接口
 * 绑定 libjni_pinyinime.so 中的原生方法
 */
public class PinyinDecoderService {
    private static final String TAG = "PinyinDecoderService";
    private static boolean isNativeLoaded = false;
    private static String loadError = null;

    static {
        try {
            System.loadLibrary("jni_pinyinime");
            isNativeLoaded = true;
            Log.i(TAG, "libjni_pinyinime.so loaded successfully");
        } catch (Throwable t) {
            loadError = t.getClass().getSimpleName() + ": " + t.getMessage();
            Log.e(TAG, "Failed to load libjni_pinyinime.so: " + loadError, t);
        }
    }

    public static boolean isLoaded() {
        return isNativeLoaded;
    }

    public static String getLoadError() {
        return loadError;
    }

    // Native JNI functions registered in libjni_pinyinime.so
    public static native boolean nativeImOpenDecoder(byte[] fn_sys_dict, byte[] fn_usr_dict);
    public static native boolean nativeImOpenDecoderFd(FileDescriptor fd, long startOffset, long length, byte[] fn_usr_dict);
    public static native void nativeImSetMaxLens(int maxSpsLen, int maxHzsLen);
    public static native boolean nativeImCloseDecoder();
    public static native int nativeImSearch(byte[] pyBuf, int pyLen);
    public static native int nativeImDelSearch(int pos, boolean is_pos_in_splid, boolean clear_fixed_this_step);
    public static native void nativeImResetSearch();
    public static native int nativeImAddLetter(byte ch);
    public static native String nativeImGetPyStr(boolean decoded);
    public static native int nativeImGetPyStrLen(boolean decoded);
    public static native int[] nativeImGetSplStart();
    public static native String nativeImGetChoice(int choiceId);
    public static native int nativeImChoose(int choiceId);
    public static native int nativeImCancelLastChoice();
    public static native int nativeImGetFixedLen();
    public static native boolean nativeImCancelInput();
    public static native boolean nativeImFlushCache();
    public static native int nativeImGetPredictsNum(String fixedStr);
    public static native String nativeImGetPredictItem(int predictNo);

    // Sync functions registered in libjni_pinyinime.so
    public static native boolean nativeSyncBegin(byte[] dictFile);
    public static native boolean nativeSyncFinish();
    public static native int nativeSyncPutLemmas(String tomerge);
    public static native String nativeSyncGetLemmas();
    public static native int nativeSyncGetLastCount();
    public static native int nativeSyncGetTotalCount();
    public static native boolean nativeSyncClearLastGot();
    public static native int nativeSyncGetCapacity();
}
