package ru.dvdishka.backuper.backend.util;

import ru.dvdishka.backuper.Backuper;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;

public class ObfuscateUtils {

    private static final byte[] keyValue = "lPXrMBtylzEUn422hzPqNN25".getBytes();

    public static String encrypt(String valueToEnc) throws Exception {
        Key key = generateKey();
        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.ENCRYPT_MODE, key);

        byte[] encValue = c.doFinal(valueToEnc.getBytes());
        byte[] encryptedValue = Base64.getEncoder().encode(encValue);

        return new String(encryptedValue);
    }

    public static String decrypt(String encryptedValue) {
        try {
            Key key = generateKey();
            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.DECRYPT_MODE, key);

            byte[] decodedValue = Base64.getDecoder().decode(encryptedValue.getBytes());
            byte[] decryptedVal = c.doFinal(decodedValue);

            return new String(decryptedVal);
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to decrypt String");
            Backuper.getInstance().getLogManager().warn(e);
            return null;
        }
    }

    private static Key generateKey() {
        return new SecretKeySpec(keyValue, "AES");
    }
}
