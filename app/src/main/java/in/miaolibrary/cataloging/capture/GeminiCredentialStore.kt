package in.miaolibrary.cataloging.capture

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class GeminiCredentialStore(context: Context) {
 private val prefs=context.getSharedPreferences("gemini_secure",Context.MODE_PRIVATE)

 fun get():String {
  val encrypted=prefs.getString("value",null)
  if(!encrypted.isNullOrBlank()){
   runCatching {
    val bytes=Base64.decode(encrypted,Base64.NO_WRAP)
    if(bytes.size<=12) return@runCatching ""
    val cipher=Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
    return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),StandardCharsets.UTF_8)
   }.getOrNull()?.takeIf{it.isNotBlank()}?.let{return it}
  }
  // Recovery for devices where the Android Keystore entry was reset/invalidated.
  return prefs.getString("value_fallback", "")?.trim().orEmpty()
 }

 fun set(value:String) {
  val clean=value.trim()
  if(clean.isBlank()){
   prefs.edit().remove("value").remove("value_fallback").apply()
   return
  }
  runCatching {
   val iv=ByteArray(12).also{java.security.SecureRandom().nextBytes(it)}
   val cipher=Cipher.getInstance(TRANSFORMATION)
   cipher.init(Cipher.ENCRYPT_MODE,key(),GCMParameterSpec(128,iv))
   val encrypted=iv+cipher.doFinal(clean.toByteArray(StandardCharsets.UTF_8))
   prefs.edit()
    .putString("value",Base64.encodeToString(encrypted,Base64.NO_WRAP))
    .putString("value_fallback",clean)
    .apply()
  }.onFailure {
   // Still keep the key so the app does not repeatedly ask for it.
   prefs.edit().putString("value_fallback",clean).apply()
  }
 }

 private fun key():SecretKey {
  val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  (ks.getKey(ALIAS,null) as? SecretKey)?.let{return it}
  val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  generator.init(
   KeyGenParameterSpec.Builder(
    ALIAS,
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
   ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .build()
  )
  return generator.generateKey()
 }

 companion object {
  private const val ALIAS="sdlm_gemini_cataloging"
  private const val TRANSFORMATION="AES/GCM/NoPadding"
 }
}
