package com.loveadvisor.android.security

import android.content.Context
import android.content.pm.PackageManager
import com.loveadvisor.core.security.SignedAuthorIdentity

data class VerifiedAuthor(val name:String,val avatar:ByteArray?,val official:Boolean) {
    companion object {
        // Public pin only. Signing private key and author assets remain outside the repository.
        const val OFFICIAL_CERT="f7c17a0bcbcdfa96b610c13fc2e5d4ece82cb71d5ff454e80834c21ca1b0aa92"
        fun load(context:Context):VerifiedAuthor = try {
            val rows=context.assets.open("verified-author/identity.txt").bufferedReader().use { it.readLines() }
            val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
            val installed=info.signingInfo?.apkContentsSigners?.singleOrNull()?.toByteArray()?.let(SignedAuthorIdentity::hash).orEmpty()
            val avatar=context.assets.open("verified-author/avatar.jpg").use { it.readBytes() }
            val name=SignedAuthorIdentity.verify(rows,avatar,installed,OFFICIAL_CERT) ?: error("Unverified author identity")
            VerifiedAuthor(name,avatar,true)
        } catch(_:Exception) { VerifiedAuthor("非官方构建",null,false) }
    }
}
