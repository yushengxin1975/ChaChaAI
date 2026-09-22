package com.chacha.ai;

import android.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 端到端 AES-256-CBC 密码学加解密工具类
 * 基于客户端与服务端共享的 Token 派生 256 位强加密密钥，配合动态随机 IV，防止局域网抓包监听。
 */
public class CryptoUtils {
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";

    private static byte[] deriveKey(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(secret.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 Key derivation failed", e);
        }
    }

    /**
     * 将明文字符串加密为 Base64 格式密文 (IV + 密文)
     */
    public static String encrypt(String plaintext, String secret) {
        if (plaintext == null || secret == null || secret.length() == 0) {
            return plaintext;
        }
        try {
            byte[] keyBytes = deriveKey(secret);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));

            byte[] combined = new byte[16 + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, 16);
            System.arraycopy(encrypted, 0, combined, 16, encrypted.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将 Base64 密文解密为明文字符串
     */
    public static String decrypt(String base64Ciphertext, String secret) {
        if (base64Ciphertext == null || secret == null || secret.length() == 0) {
            return base64Ciphertext;
        }
        try {
            byte[] combined = Base64.decode(base64Ciphertext, Base64.DEFAULT);
            if (combined == null || combined.length < 17) {
                return null;
            }

            byte[] iv = new byte[16];
            System.arraycopy(combined, 0, iv, 0, 16);
            int cipherLen = combined.length - 16;
            byte[] encrypted = new byte[cipherLen];
            System.arraycopy(combined, 16, encrypted, 0, cipherLen);

            byte[] keyBytes = deriveKey(secret);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
