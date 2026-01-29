package io.github.cryolitia.stickerbot

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bilibili.burstlinker.BurstLinker
import com.bilibili.burstlinker.GifEncodeException
import java.io.ByteArrayOutputStream
import java.io.File

sealed class GifEncoder(val dstFile: File) {
    abstract fun start()
    abstract fun addFrame(bitmap: Bitmap)
    abstract fun end()
    abstract fun setDelay(delay: Int)
}

class NbadalGifEncoder(dstFile: File) : GifEncoder(dstFile) {

    private val encoder: AnimatedGifEncoder = AnimatedGifEncoder()
    private val byteArrayOutputStream = ByteArrayOutputStream()
    private var mDelay: Int = 1

    override fun start() {
        encoder.start(byteArrayOutputStream)
        encoder.repeat = 0
    }

    override fun addFrame(bitmap: Bitmap) {
        encoder.setDelay(mDelay)
        encoder.addFrame(bitmap)
    }

    override fun end() {
        encoder.finish()
        dstFile.writeBytes(byteArrayOutputStream.toByteArray())
    }

    override fun setDelay(delay: Int) {
        mDelay = delay
    }

}

class BilibiliGifEncoder(
    dstFile: File,
    val quantizer: Int,
    val dither: Int,
    val width: Int,
    val height: Int
) : GifEncoder(dstFile) {

    private var encoder = BurstLinker()
    private var mDelay: Int = 1

    @Throws(GifEncodeException::class)
    override fun start() {
        encoder.init(
            width,
            height,
            dstFile.absolutePath
        )
    }

    @Throws(GifEncodeException::class)
    override fun addFrame(bitmap: Bitmap) {
        val newBitmap =
            if (bitmap.config != Bitmap.Config.ARGB_8888 && bitmap.config != Bitmap.Config.RGB_565)
                bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        encoder.connect(
            newBitmap,
            quantizer,
            dither,
            0,
            0,
            mDelay
        )
    }

    override fun end() {
        encoder.release()
    }

    override fun setDelay(delay: Int) {
        mDelay = delay
    }

}

suspend fun Context.getQuantizer(): Int {
    val quantizer = getPreference(stringPreferencesKey(QUANTIZER))
    return when (quantizer) {
        "uniform" -> BurstLinker.UNIFROM_QUANTIZER
        "median_cut" -> BurstLinker.MEDIAN_CUT_QUANTIZER
        "kmeans" -> BurstLinker.KMEANS_QUANTIZER
        "random" -> BurstLinker.RANDOM_QUANTIZER
        "octree" -> BurstLinker.OCTREE_QUANTIZER
        "neu_quant" -> BurstLinker.NEU_QUANT_QUANTIZER
        else -> BurstLinker.KMEANS_QUANTIZER
    }
}

suspend fun Context.getDither(): Int {
    val dither = getPreference(stringPreferencesKey(DITHER))
    return when (dither) {
        "no" -> BurstLinker.NO_DITHER
        "m2" -> BurstLinker.M2_DITHER
        "bayer" -> BurstLinker.BAYER_DITHER
        "floyd_steinberg" -> BurstLinker.FLOYD_STEINBERG_DITHER
        else -> BurstLinker.NO_DITHER
    }
}