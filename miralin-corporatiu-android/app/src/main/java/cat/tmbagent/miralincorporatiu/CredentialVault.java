package cat.tmbagent.miralincorporatiu;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM credential encryption with non-exportable AndroidKeyStore key. */
final class CredentialVault {
    private static final String ALIAS = "cat.tmbagent.miralincorporatiu.basic.v1";
    private static final String PREFS = "corporate-login-v1";
    private static final String IV = "iv";
    private static final String ENCRYPTED = "data";
    private final SharedPreferences prefs;

    CredentialVault(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static final class Login {
        final String username;
        final String password;
        Login(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    private SecretKey getKey(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) {
            KeyStore.Entry entry = store.getEntry(ALIAS, null);
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        if (!create) return null;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return generator.generateKey();
    }

    synchronized void save(String username, String password) throws Exception {
        SecretKey key = getKey(true);
        JSONObject payload = new JSONObject().put("username", username).put("password", password);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] ciphertext = cipher.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
        // commit() ensures both values are on disk before reporting success.
        if (!prefs.edit()
            .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .putString(ENCRYPTED, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()) throw new IllegalStateException("No s'han pogut desar les credencials.");
    }

    synchronized Login load() throws Exception {
        String raw = prefs.getString(ENCRYPTED, null);
        String nonce = prefs.getString(IV, null);
        if (raw == null || nonce == null) return null;
        SecretKey key = getKey(false);
        if (key == null) throw new IllegalStateException("La clau d'accés guardada no està disponible.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(nonce, Base64.NO_WRAP)));
        String json = new String(cipher.doFinal(Base64.decode(raw, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        JSONObject value = new JSONObject(json);
        return new Login(value.getString("username"), value.getString("password"));
    }

    synchronized void clear() throws Exception {
        prefs.edit().clear().commit();
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS);
    }
}
