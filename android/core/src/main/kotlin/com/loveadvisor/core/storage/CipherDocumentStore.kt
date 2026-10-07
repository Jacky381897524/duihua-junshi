package com.loveadvisor.core.storage
import java.io.File
import javax.crypto.SecretKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.FileOutputStream
class CipherDocumentStore(private val directory:File,private val cipherFactory:()->Cipher={ Cipher.getInstance("AES/GCM/NoPadding") },private val key:()->SecretKey) {
    private fun path(name:String,suffix:String):File {
        require(Regex("[a-z0-9_-]{1,40}").matches(name)) { "本地文档名无效" }
        return File(directory,name+suffix)
    }
    @Synchronized fun read(name:String):String? {
        val file=path(name,".enc"); if(!file.exists()) return null
        return try {
            require(file.length() in 30..4*1024*1024)
            val data=file.readBytes(); require(data[0]==1.toByte())
            val cipher=cipherFactory(); cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,data.copyOfRange(1,13)))
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            cipher.doFinal(data.copyOfRange(13,data.size)).toString(Charsets.UTF_8)
        } catch(_:Exception) { throw SecurityException("本地资料无法解密，请检查设备密钥或在设置中重置资料") }
    }
    @Synchronized fun write(name:String,text:String) {
        require(text.toByteArray(Charsets.UTF_8).size<=3*1024*1024) { "本地资料超过存储限制" }
        directory.mkdirs()
        val target=path(name,".enc"); val pending=path(name,".pending")
        val cipher=cipherFactory(); cipher.init(Cipher.ENCRYPT_MODE,key()); val iv=cipher.iv
        require(iv.size==12) { "设备加密参数无效" }; cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val data=byteArrayOf(1)+iv+cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        FileOutputStream(pending).use { it.write(data); it.fd.sync() }
        Files.move(pending.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
}
