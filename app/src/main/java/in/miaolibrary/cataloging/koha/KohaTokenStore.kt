package `in`.miaolibrary.cataloging.koha

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class KohaTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("koha_secure", Context.MODE_PRIVATE)
    private val alias = "MiaoLibraryKohaApiToken"

    private fun key(): SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    fun save(token: String) {
        if (token.isBlank()) { clear(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun get(): String? = runCatching {
        val raw = prefs.getString("token", null) ?: return null
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
    }.getOrNull()

    fun clear() { prefs.edit().remove("token").apply() }
}