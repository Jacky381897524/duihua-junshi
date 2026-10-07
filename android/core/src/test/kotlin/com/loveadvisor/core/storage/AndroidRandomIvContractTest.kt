package com.loveadvisor.core.storage
import java.nio.file.Files
import java.security.*
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.*
import org.junit.Assert.*
import org.junit.Test

// Models the documented AndroidKeyStore boundary: ENCRYPT_MODE rejects caller IV.
// It does not claim to execute AndroidKeyStore; all cryptography delegates to real JVM AES-GCM.
private class RandomizedCipher:Cipher(RandomizedSpi(),object:Provider("RandomizedBoundary","1.0","Test platform boundary"){},"AES/GCM/NoPadding")
private class RandomizedSpi:CipherSpi() {
    private val real=Cipher.getInstance("AES/GCM/NoPadding")
    override fun engineSetMode(mode:String) {}
    override fun engineSetPadding(padding:String) {}
    override fun engineGetBlockSize()=real.blockSize
    override fun engineGetOutputSize(len:Int)=real.getOutputSize(len)
    override fun engineGetIV():ByteArray?=real.iv
    override fun engineGetParameters():AlgorithmParameters?=real.parameters
    override fun engineInit(mode:Int,key:Key,random:SecureRandom?) { real.init(mode,key,random) }
    override fun engineInit(mode:Int,key:Key,params:AlgorithmParameterSpec?,random:SecureRandom?) { if(mode==Cipher.ENCRYPT_MODE && params!=null) throw InvalidAlgorithmParameterException("Caller IV forbidden by randomized key"); real.init(mode,key,params,random) }
    override fun engineInit(mode:Int,key:Key,params:AlgorithmParameters?,random:SecureRandom?) { if(mode==Cipher.ENCRYPT_MODE && params!=null) throw InvalidAlgorithmParameterException("Caller IV forbidden by randomized key"); real.init(mode,key,params,random) }
    override fun engineUpdate(input:ByteArray,offset:Int,len:Int):ByteArray?=real.update(input,offset,len)
    override fun engineUpdate(input:ByteArray,offset:Int,len:Int,output:ByteArray,outputOffset:Int)=real.update(input,offset,len,output,outputOffset)
    override fun engineDoFinal(input:ByteArray,offset:Int,len:Int):ByteArray=real.doFinal(input,offset,len)
    override fun engineDoFinal(input:ByteArray,offset:Int,len:Int,output:ByteArray,outputOffset:Int)=real.doFinal(input,offset,len,output,outputOffset)
    override fun engineUpdateAAD(input:ByteArray,offset:Int,len:Int) { real.updateAAD(input,offset,len) }
}
class AndroidRandomIvContractTest {
    @Test fun platformGeneratesFreshIvAndAuthenticatedDocumentStillRoundTrips() {
        val dir=Files.createTempDirectory("randomized-boundary").toFile(); val key=KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store=CipherDocumentStore(dir,{ RandomizedCipher() }) { key }
        try {
            store.write("config","测试配置"); val first=dir.resolve("config.enc").readBytes(); assertEquals("测试配置",store.read("config"))
            store.write("config","测试配置"); val second=dir.resolve("config.enc").readBytes()
            assertFalse(first.copyOfRange(1,13).contentEquals(second.copyOfRange(1,13))); assertEquals("测试配置",store.read("config"))
        } finally { dir.deleteRecursively() }
    }
}
