package io.github.cryolitia.stickerbot

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.google.android.flexbox.FlexboxLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDrawableBuilder
import pl.droidsonroids.gif.GifImageView
import java.io.File
import java.util.Arrays

class GalleryAdapter(
    val context: Context,
    val lifecycleOwner: LifecycleOwner,
    fileList: Array<File>
) :
    RecyclerView.Adapter<GalleryAdapter.ViewHolderTemplate<GifImageView>>() {

    var previewScale: Float = 0.5f

    init {
        lifecycleOwner.lifecycleScope.launch {
            previewScale = context.getPreference(
                stringPreferencesKey(STICKER_PER_LINE), "0.5"
            ).toFloatOrNull() ?: 0.5f
        }
    }

    private val array: Array<File> = Arrays.stream(fileList).filter { file ->
        val extension = file.extension
        extension.equals("png", true)
                || extension.equals("webp", true)
                || extension.equals("gif", true)
                || extension.equals("webm", true)
    }.toList().toTypedArray()

    class ViewHolderTemplate<T : View>(val view: T) : ViewHolder(view)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolderTemplate<GifImageView> {
        return ViewHolderTemplate(GifImageView(context))
    }

    override fun getItemCount(): Int = array.size

    override fun onBindViewHolder(holder: ViewHolderTemplate<GifImageView>, position: Int) {
        val params = holder.view.layoutParams
        if (params is FlexboxLayout.LayoutParams) {
            params.flexGrow = 1.0f
        }
        holder.view.setOnClickListener {
            lifecycleOwner.lifecycleScope.launch {
                var webpFile = array[position]
                try {
                    val prepareDialog: AlertDialog = with(context) {
                        loadingDialog()
                    }
                    val encodeWebp = context.getPreference(
                        booleanPreferencesKey(RECODE_WEBP), false
                    )
                    val replaceTransparent = context.getPreference(
                        booleanPreferencesKey(REPLACE_TRANSPARENT), false
                    )
                    if ((webpFile.extension.equals(
                            "webp",
                            true
                        ) || webpFile.extension.equals(
                            "png",
                            true
                        )) && (encodeWebp || replaceTransparent)
                    ) {
                        withContext(Dispatchers.IO) {
                            val cacheDirectory =
                                File(context.externalCacheDir, "Stickers")
                            val stickerCacheDirectory =
                                File(cacheDirectory, webpFile.parentFile?.name ?: "default")
                            if (!stickerCacheDirectory.exists()) {
                                stickerCacheDirectory.mkdirs()
                            }
                            val inputStream = webpFile.inputStream()
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            inputStream.close()

                            if (replaceTransparent) {
                                try {
                                    val replaceCache = File(
                                        stickerCacheDirectory,
                                        webpFile.nameWithoutExtension + ".png"
                                    )
                                    if (!replaceCache.exists()) {
                                        replaceCache.createNewFile()
                                    }

                                    val replaceBitmap = createBitmap(
                                        bitmap.width,
                                        bitmap.height,
                                        bitmap.config ?: Bitmap.Config.ARGB_8888
                                    )
                                    replaceBitmap.eraseColor(Color.WHITE)
                                    val canvas = Canvas(replaceBitmap)
                                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                                    val outputStream = replaceCache.outputStream()
                                    replaceBitmap.compress(
                                        Bitmap.CompressFormat.PNG,
                                        100,
                                        outputStream
                                    )
                                    outputStream.flush()
                                    outputStream.close()
                                    webpFile = replaceCache
                                } catch (e: Exception) {
                                    with(context) {
                                        e.toString().alert()
                                        e.printStackTrace()
                                    }
                                }
                            }

                            if (encodeWebp) {
                                val stickerCache = File(
                                    stickerCacheDirectory,
                                    webpFile.nameWithoutExtension + ".gif"
                                )
                                if (!stickerCache.exists()) {
                                    stickerCache.createNewFile()
                                }

                                var encoder: GifEncoder? = null
                                try {
                                    encoder = if (context.getPreference(
                                            booleanPreferencesKey(
                                                GIF_CODER
                                            ), true
                                        )
                                    ) BilibiliGifEncoder(
                                        stickerCache,
                                        context.getQuantizer(),
                                        context.getDither(),
                                        bitmap.width,
                                        bitmap.height
                                    ) else NbadalGifEncoder(stickerCache)

                                    encoder.start()
                                    encoder.addFrame(bitmap)
                                } catch (e: Exception) {
                                    with(context) {
                                        e.toString().alert()
                                        e.printStackTrace()
                                    }
                                } finally {
                                    encoder?.end()
                                }

                                webpFile = stickerCache
                            }

                        }
                    }
                    prepareDialog.dismiss()
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        val uri = FileProvider.getUriForFile(
                            context,
                            "io.github.cryolitia.stickerbot.stickerprovider",
                            webpFile
                        )
                        putExtra(Intent.EXTRA_STREAM, uri)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        type = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(webpFile.extension)
                    }
                    context.startActivity(Intent.createChooser(intent, ""))
                } catch (e: Exception) {
                    with(context) {
                        e.toString().alert()
                        e.printStackTrace()
                    }
                }
            }
        }

        // android/graphics/RecordingCanvas.java#MAX_BITMAP_SIZE
        val input = array[position].inputStream()
        try {
            BitmapFactory.decodeStream(array[position].inputStream())
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            input.close()
        }

        val stream = array[position].inputStream()
        try {
            val file = array[position]
            if (file.extension == "gif") {
                val drawable =
                    GifDrawableBuilder().sampleSize((1 / previewScale).toInt()).from(file).build()
                holder.view.setImageDrawable(drawable)
            } else {
                val bitmap = BitmapFactory.decodeStream(stream)
                holder.view.setImageBitmap(
                    bitmap.scale(
                        (bitmap.width * previewScale).toInt(),
                        (bitmap.height * previewScale).toInt(),
                        false
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            stream.close()
        }
    }
}