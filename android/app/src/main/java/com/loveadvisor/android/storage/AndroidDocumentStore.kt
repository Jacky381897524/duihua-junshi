package com.loveadvisor.android.storage
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import com.loveadvisor.core.storage.CipherDocumentStore
class AndroidDocumentStore(context:Context,directory:String="private-data") {
    private val store=CipherDocumentStore(context.filesDir.resolve(directory),limits=mapOf("personal-memory-v1" to 24*1024*1024,"personal-memory-index" to 24*1024*1024,"pm-*" to 24*1024*1024)) { obtainKey() }
    fun read(name:String)=store.read(name)
    fun write(name:String,text:String)=store.write(name,text)
    fun remove(name:String)=store.remove(name)
    fun names()=store.names()
    companion object {
        @Synchronized private fun obtainKey():SecretKey {
            val alias="love-advisor-aes-v1"
            val keys=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keys.getKey(alias,null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
            }.generateKey()
        }
    }
}
