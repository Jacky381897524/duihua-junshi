package com.loveadvisor.core.security

import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.Assert.*
import org.junit.Test

class SignedAuthorIdentityTest {
    @Test fun modificationOfNameAvatarOrSignatureIsRejected() {
        val key=KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val avatar=byteArrayOf(1,2,3)
        val payload="author-v1\n测试作者\n${SignedAuthorIdentity.hash(avatar)}\n".toByteArray()
        val signature=Signature.getInstance("SHA256withRSA").run { initSign(key.private);update(payload);sign() }
        assertEquals("测试作者",SignedAuthorIdentity.verifyPayload(payload,signature,key.public,avatar))
        assertNull(SignedAuthorIdentity.verifyPayload(payload.toString(Charsets.UTF_8).replace("测试作者","另一作者").toByteArray(),signature,key.public,avatar))
        assertNull(SignedAuthorIdentity.verifyPayload(payload,signature,key.public,byteArrayOf(4,5,6)))
        assertNull(SignedAuthorIdentity.verifyPayload(payload,signature.copyOf().apply { this[0]=(this[0].toInt() xor 1).toByte() },key.public,avatar))
    }
    @Test fun anUnrelatedSigningKeyCannotValidatePublishedIdentity() {
        val first=KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val second=KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val avatar=byteArrayOf(1)
        val payload="author-v1\n测试作者\n${SignedAuthorIdentity.hash(avatar)}\n".toByteArray()
        val signature=Signature.getInstance("SHA256withRSA").run { initSign(first.private);update(payload);sign() }
        assertNull(SignedAuthorIdentity.verifyPayload(payload,signature,second.public,avatar))
        assertNull(SignedAuthorIdentity.verify(listOf("","",""),avatar,"wrong","official"))
    }
}
