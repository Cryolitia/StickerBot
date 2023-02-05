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

        val authority = info.authority.split(";".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()[0]

        val sCacheField = FileProvider::class.java.getDeclaredField("sCache")
        sCacheField.isAccessible = true
        val sCache: HashMap<String, *> = sCacheField.get(this) as HashMap<String, *>

        val mStrategyField = FileProvider::class.java.getDeclaredField("mStrategy")
        mStrategyField.isAccessible = true

        val getPathStrategyMethod = FileProvider::class.java.getDeclaredMethod(
            "getPathStrategy",
            Context::class.java,
            String::class.java,
            Int::class.javaPrimitiveType
        )
        getPathStrategyMethod.isAccessible = true

        synchronized(sCache) { sCache.remove(authority) }
        mStrategyField.set(
            this,
            getPathStrategyMethod.invoke(this, context, authority, R.xml.file_paths)
        )
    }

}