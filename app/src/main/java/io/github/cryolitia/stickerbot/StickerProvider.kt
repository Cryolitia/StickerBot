package io.github.cryolitia.stickerbot

import android.content.Context
import android.content.pm.ProviderInfo
import androidx.core.content.FileProvider

class StickerProvider : FileProvider(R.xml.file_paths) {

    override fun attachInfo(context: Context, info: ProviderInfo) {

        try {
            super.attachInfo(context, info)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val authority = info.authority.split(";")[0]

        val mLockField = FileProvider::class.java.getDeclaredField("mLock")
        mLockField.isAccessible = true
        val mLock = mLockField.get(this)

        val sCacheField = FileProvider::class.java.getDeclaredField("sCache")
        sCacheField.isAccessible = true
        val sCache: HashMap<String, *> = sCacheField.get(this) as HashMap<String, *>

        val mAuthorityField = FileProvider::class.java.getDeclaredField("mAuthority")
        mAuthorityField.isAccessible = true

        synchronized(mLock!!) {
            mAuthorityField.set(this, authority)
        }

        synchronized(sCache) {
            sCache.remove(authority)
        }
    }

}