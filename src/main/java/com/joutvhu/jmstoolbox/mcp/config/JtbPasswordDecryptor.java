package com.joutvhu.jmstoolbox.mcp.config;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Replicates decryption of JMSToolBox passwords.
 * JMSToolBox encrypts passwords using AES/ECB/PKCS5Padding with default key: "JMSToolBox!?&$*-"
 * Format: {##}<base64_encoded_ciphertext>
 */
public class JtbPasswordDecryptor {
    private static final String DEFAULT_KEY = "JMSToolBox!?&$*-";
    private static final String ENC_PREFIX = "{##}";

    public static String decrypt(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.trim().isEmpty()) {
            return null;
        }

        if (!encryptedPassword.startsWith(ENC_PREFIX)) {
            return encryptedPassword; // Plaintext
        }

        String b64 = encryptedPassword.substring(ENC_PREFIX.length());

        try {
            String keyStr = System.getenv("JTB_KEY");
            if (keyStr == null || keyStr.trim().isEmpty()) {
                keyStr = DEFAULT_KEY;
            }

            byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = Base64.getDecoder().decode(b64);

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decrypted = cipher.doFinal(cipherBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[JtbPasswordDecryptor] Failed to decrypt password: " + e.getMessage());
            return null;
        }
    }
}
