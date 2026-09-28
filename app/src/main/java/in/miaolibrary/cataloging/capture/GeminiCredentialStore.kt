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
 fun get():String{
  val raw=prefs.getString("value",null)?:return ""
  return runCatching{
   val bytes=Base64.decode(raw,Base64.NO_WRAP)
   val cipher=Cipher.getInstance(TRANSFORMATION)
   cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
   String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),StandardCharsets.UTF_8)
  }.getOrDefault("")
 }
 fun set(value:String){
  if(value.isBlank()){prefs.edit().remove("value").apply();return}
  val iv=ByteArray(12).also{java.security.SecureRandom().nextBytes(it)}
  val cipher=Cipher.getInstance(TRANSFORMATION)
  cipher.init(Cipher.ENCRYPT_MODE,key(),GCMParameterSpec(128,iv))
  val encrypted=iv+cipher.doFinal(value.trim().toByteArray(StandardCharsets.UTF_8))
  prefs.edit().putString("value",Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply()
 }
 private fun key():SecretKey{
  val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  (ks.getKey(ALIAS,null) as? SecretKey)?.let{return it}
  val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  generator.init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  return generator.generateKey()
 }
 companion object{private const val ALIAS="sdlm_gemini_cataloging";private const val TRANSFORMATION="AES/GCM/NoPadding"}
}
