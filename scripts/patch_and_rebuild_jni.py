import os, subprocess

cpp_path = "/home/admin/pinyin_unzip/PinyinIme-master/jni/android/com_gaoxin_guangsuime_PinyinDecoderService.cpp"

with open(cpp_path, "r", encoding="utf-8", errors="ignore") as f:
    content = f.read()

# Replace registerNativeMethods, registerNatives, and JNI_OnLoad
target_block = """/*
 * Register several native methods for one class.
 */
static int registerNativeMethods(JNIEnv* env, const char* className,
		JNINativeMethod* gMethods, int numMethods) {"""

idx = content.find(target_block)
if idx != -1:
    content = content[:idx]

new_block = """
/*
 * Register several native methods for one class.
 */
static int registerNativeMethods(JNIEnv* env, const char* className,
		JNINativeMethod* gMethods, int numMethods) {
	jclass clazz = (*env).FindClass(className);
	if (clazz == NULL) {
		if (env->ExceptionCheck()) env->ExceptionClear();
		return JNI_FALSE;
	}
	if ((*env).RegisterNatives(clazz, gMethods, numMethods) < 0) {
		if (env->ExceptionCheck()) env->ExceptionClear();
		return JNI_FALSE;
	}

	clazz = env->FindClass("java/io/FileDescriptor");
	if (clazz != NULL) {
		gFileDescriptorOffsets.mClass = (jclass) env->NewGlobalRef(clazz);
		gFileDescriptorOffsets.mDescriptor = env->GetFieldID(clazz, "descriptor", "I");
	}
	if (env->ExceptionCheck()) {
		env->ExceptionClear();
	}

	return JNI_TRUE;
}

/*
 * Register native methods for all classes we know about.
 */
static int registerNatives(JNIEnv* env) {
	registerNativeMethods(env,
			"com/keanbin/pinyinime/PinyinDecoderService", gMethods,
			sizeof(gMethods) / sizeof(gMethods[0]));
	if (env->ExceptionCheck()) {
		env->ExceptionClear();
	}
	return JNI_TRUE;
}

/*
 * Standard exported JNI functions (fallback if RegisterNatives is bypassed)
 */
JNIEXPORT jboolean JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImOpenDecoder(
		JNIEnv* env, jclass jclazz, jbyteArray fn_sys_dict, jbyteArray fn_usr_dict) {
	return nativeImOpenDecoder(env, jclazz, fn_sys_dict, fn_usr_dict);
}

JNIEXPORT jboolean JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImOpenDecoderFd(
		JNIEnv* env, jclass jclazz, jobject fd_sys_dict, jlong startoffset, jlong length, jbyteArray fn_usr_dict) {
	return nativeImOpenDecoderFd(env, jclazz, fd_sys_dict, startoffset, length, fn_usr_dict);
}

JNIEXPORT void JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImSetMaxLens(
		JNIEnv* env, jclass jclazz, jint max_sps_len, jint max_hzs_len) {
	nativeImSetMaxLens(env, jclazz, max_sps_len, max_hzs_len);
}

JNIEXPORT jboolean JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImCloseDecoder(
		JNIEnv* env, jclass jclazz) {
	return nativeImCloseDecoder(env, jclazz);
}

JNIEXPORT jint JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImSearch(
		JNIEnv* env, jclass jclazz, jbyteArray pybuf, jint pylen) {
	return nativeImSearch(env, jclazz, pybuf, pylen);
}

JNIEXPORT jint JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImDelSearch(
		JNIEnv* env, jclass jclazz, jint pos, jboolean is_pos_in_splid, jboolean clear_fixed_this_step) {
	return nativeImDelSearch(env, jclazz, pos, is_pos_in_splid, clear_fixed_this_step);
}

JNIEXPORT void JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImResetSearch(
		JNIEnv* env, jclass jclazz) {
	nativeImResetSearch(env, jclazz);
}

JNIEXPORT jint JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImAddLetter(
		JNIEnv* env, jclass jclazz, jbyte ch) {
	return nativeImAddLetter(env, jclazz, ch);
}

JNIEXPORT jstring JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImGetPyStr(
		JNIEnv* env, jclass jclazz, jboolean decoded) {
	return nativeImGetPyStr(env, jclazz, decoded);
}

JNIEXPORT jint JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImGetPyStrLen(
		JNIEnv* env, jclass jclazz, jboolean decoded) {
	return nativeImGetPyStrLen(env, jclazz, decoded);
}

JNIEXPORT jintArray JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImGetSplStart(
		JNIEnv* env, jclass jclazz) {
	return nativeImGetSplStart(env, jclazz);
}

JNIEXPORT jstring JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImGetChoice(
		JNIEnv *env, jclass clazz, jint candidateId) {
	return nativeImGetChoice(env, clazz, candidateId);
}

JNIEXPORT jint JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImChoose(
		JNIEnv *env, jclass clazz, jint choice_id) {
	return nativeImChoose(env, clazz, choice_id);
}

JNIEXPORT jint JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImCancelLastChoice(
		JNIEnv *env, jclass clazz) {
	return nativeImCancelLastChoice(env, clazz);
}

JNIEXPORT jint JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImGetFixedLen(
		JNIEnv *env, jclass clazz) {
	return nativeImGetFixedLen(env, clazz);
}

JNIEXPORT jboolean JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImCancelInput(
		JNIEnv *env, jclass clazz) {
	return nativeImCancelInput(env, clazz);
}

JNIEXPORT jboolean JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImFlushCache(
		JNIEnv *env, jclass clazz) {
	return nativeImFlushCache(env, clazz);
}

JNIEXPORT jint JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImGetPredictsNum(
		JNIEnv *env, jclass clazz, jstring fixed_str) {
	return nativeImGetPredictsNum(env, clazz, fixed_str);
}

JNIEXPORT jstring JNICALL Java_com_keanbin_pinyinime_PinyinDecoderService_nativeImGetPredictItem(
		JNIEnv *env, jclass clazz, jint predict_no) {
	return nativeImGetPredictItem(env, clazz, predict_no);
}

/*
 * Returns the JNI version on success, never return failure (-1) to avoid breaking app startup.
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
	JNIEnv* env = NULL;
	if ((*vm).GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
		return JNI_VERSION_1_4;
	}

	registerNatives(env);
	if (env->ExceptionCheck()) {
		env->ExceptionClear();
	}

	return JNI_VERSION_1_4;
}

#ifdef __cplusplus
}
#endif
"""

with open(cpp_path, "w", encoding="utf-8") as f:
    f.write(content + new_block)

print("Source patched successfully. Now building with NDK...")
subprocess.run(["python3", "/home/admin/build_native_ndk.py"])
