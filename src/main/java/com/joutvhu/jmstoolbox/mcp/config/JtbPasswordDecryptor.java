package com.joutvhu.jmstoolbox.mcp.config;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;

/**
 * Replicates decryption of JMSToolBox passwords.
 * Exactly matches org.titou10.jtb.util.EncryptUtils from JMSToolBox.
 * Format: {@code {##}<base64_encoded_ciphertext>}
 */
public class JtbPasswordDecryptor {
    private static final String DEFAULT_KEY = "JMSToolBox!?&$*-";
    private static final String ENC_PREFIX = "{##}";
    private static final String ALGORITHM = "AES";

    public static String decrypt(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.trim().isEmpty()) {
            return null;
        }

        if (!encryptedPassword.startsWith(ENC_PREFIX)) {
            return encryptedPassword; // Plaintext
        }

        String d = encryptedPassword.substring(ENC_PREFIX.length());

        try {
            String keyStr = System.getenv("JTB_KEY");
            if (keyStr == null || keyStr.trim().isEmpty()) {
                keyStr = DEFAULT_KEY;
            }

            byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
            Key key = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.DECRYPT_MODE, key);

            byte[] decodedValue = Base64.getDecoder().decode(d);
            byte[] decrypted = c.doFinal(decodedValue);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[JtbPasswordDecryptor] Failed to decrypt password: " + e.getMessage());
            return null;
        }
    }

    public static String encrypt(String data) {
        if (data == null) {
            return null;
        }
        try {
            String keyStr = System.getenv("JTB_KEY");
            if (keyStr == null || keyStr.trim().isEmpty()) {
                keyStr = DEFAULT_KEY;
            }
            byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
            Key key = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.ENCRYPT_MODE, key);
            byte[] encVal = c.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String base64 = Base64.getEncoder().encodeToString(encVal);
            return ENC_PREFIX + base64;
        } catch (Exception e) {
            System.err.println("[JtbPasswordDecryptor] Failed to encrypt password: " + e.getMessage());
            return null;
        }
    }
}
