package com.loveadvisor.core.security

import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.util.Base64

/** Verifies published author data, not the integrity of an arbitrary modified fork. */
object SignedAuthorIdentity {
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun verify(rows:List<String>,avatar:ByteArray,installedCertificate:String,pin:String):String?=runCatching {
        require(rows.size==3 && rows.all { it.length<=8192 })
        val decoder=Base64.getDecoder()
        val payload=decoder.decode(rows[0]);val signature=decoder.decode(rows[1]);val certificate=decoder.decode(rows[2])
        require(installedCertificate==pin && hash(certificate)==pin)
        val key=CertificateFactory.getInstance("X.509").generateCertificate(certificate.inputStream()).publicKey
        verifyPayload(payload,signature,key,avatar)
    }.getOrNull()
    internal fun verifyPayload(payload:ByteArray,signed:ByteArray,key:PublicKey,avatar:ByteArray):String?=runCatching {
        require(Signature.getInstance("SHA256withRSA").run { initVerify(key);update(payload);verify(signed) })
        val fields=payload.toString(Charsets.UTF_8).split('\n')
        require(fields.size==4 && fields[0]=="author-v1" && fields[3].isEmpty())
        require(fields[1].length in 1..80 && fields[1].none { it.isISOControl() })
        require(hash(avatar)==fields[2])
        fields[1]
    }.getOrNull()
}
