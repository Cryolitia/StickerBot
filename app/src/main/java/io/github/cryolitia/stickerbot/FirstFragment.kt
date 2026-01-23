package io.github.cryolitia.stickerbot

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bilibili.burstlinker.BurstLinker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        val stickersDirectory = File(requireContext().getExternalFilesDir(null), "Stickers")
        if (!stickersDirectory.exists()) {
            stickersDirectory.mkdirs()
        }

        for (directory in stickersDirectory.listFiles()!!) {
            if (directory.isDirectory) {
                val preference = Preference(requireContext())
                preference.title = directory.name
                var image: File? = null
                try {
                    image = directory.listFiles { file ->
                        file.name.contains("thumb", true)
                    }?.get(0)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                if (image == null) {
                    try {
                        image = directory.listFiles()?.get(0)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (image != null) {
                    try {
                        val stream = image.inputStream()
                        preference.icon =
                            BitmapFactory.decodeStream(stream).toDrawable(resources)
                        stream.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                preference.onPreferenceClickListener = OnPreferenceClickListener {
                    val recyclerView = RecyclerView(requireContext())
                    recyclerView.layoutManager =
                        StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                    recyclerView.adapter = GalleryAdapter(directory)
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(directory.name)
                        .setView(recyclerView)
                        .setNegativeButton("Delete") { _, _ ->
                            MaterialAlertDialogBuilder(requireContext())
                                .setMessage("Delete?")
                                .setNegativeButton("Confirm") { _, _ ->
                                    directory.deleteRecursively()
                                    requireActivity().recreate()
                                }
                                .setNeutralButton("Cancel") { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .create()
                                .show()
                        }
                        .setNeutralButton("Close") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()
                        .show()
                    val params = recyclerView.layoutParams
                    if (params is ViewGroup.MarginLayoutParams) {
                        val dp = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            16F,
                            resources.displayMetrics
                        ).toInt()
                        params.setMargins(dp, dp, dp, 0)
                        recyclerView.layoutParams = params
                    }
                    true
                }
                preferenceScreen.addPreference(preference)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        (requireHost() as MenuHost).addMenuProvider(object : MenuProvider {

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle action bar item clicks here. The action bar will
                // automatically handle clicks on the Home/Up button, so long
                // as you specify a parent activity in AndroidManifest.xml.
                return when (menuItem.itemId) {
                    R.id.action_settings -> {
                        findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
                        true
                    }

                    else -> false
                }
            }

        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).fab.visibility = View.VISIBLE
    }

    inner class GalleryAdapter(private val directory: File) :
        RecyclerView.Adapter<GalleryAdapter.ViewHolderTemplate<ImageView>>() {

        private val array: Array<File> = directory.listFiles { file ->
            val extension = file.extension
            extension.equals("png", true)
                    || extension.equals("webp", true)
                    || extension.equals("gif", true)
                    || extension.equals("webm", true)
        } ?: emptyArray()

        inner class ViewHolderTemplate<T : View>(val view: T) : ViewHolder(view)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ViewHolderTemplate<ImageView> {
            return ViewHolderTemplate(ImageView(context))
        }

        override fun getItemCount(): Int = array.size

        override fun onBindViewHolder(holder: ViewHolderTemplate<ImageView>, position: Int) {
            holder.view.setOnClickListener {
                lifecycleScope.launch {
                    var webpFile = array[position]
                    try {
                        val prepareDialog: AlertDialog = with(requireContext()) {
                            loadingDialog()
                        }
                        val encodeWebp = requireContext().getPreference(
                            booleanPreferencesKey(RECODE_WEBP), false
                        )
                        val replaceTransparent = requireContext().getPreference(
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
                                    File(requireContext().externalCacheDir, "Stickers")
                                val stickerCacheDirectory = File(cacheDirectory, directory.name)
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
                                        with(requireContext()) {
                                            e.toString().alert()
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
                                    if (requireContext().getPreference(
                                            booleanPreferencesKey(
                                                GIF_CODER
                                            ), true
                                        )
                                    ) {
                                        val burstLinker = BurstLinker()
                                        try {
                                            burstLinker.init(
                                                bitmap.width,
                                                bitmap.height,
                                                stickerCache.absolutePath
                                            )
                                            burstLinker.connect(
                                                bitmap,
                                                requireContext().getQuantizer(),
                                                requireContext().getDither(),
                                                0,
                                                0,
                                                1
                                            )
                                        } catch (e: Exception) {
                                            with(requireContext()) {
                                                e.toString().alert()
                                            }
                                        } finally {
                                            burstLinker.release()
                                        }
                                    } else {
                                        val encoder = AnimatedGifEncoder()
                                        val byteArrayOutputStream = ByteArrayOutputStream()
                                        encoder.start(byteArrayOutputStream)
                                        encoder.addFrame(bitmap)
                                        encoder.finish()
                                        stickerCache.writeBytes(byteArrayOutputStream.toByteArray())
                                    }
                                    webpFile = stickerCache
                                }

                            }
                        }
                        prepareDialog.dismiss()
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            val uri = FileProvider.getUriForFile(
                                requireContext(),
                                "io.github.cryolitia.stickerbot.stickerprovider",
                                webpFile
                            )
                            putExtra(Intent.EXTRA_STREAM, uri)
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            type = MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(webpFile.extension)
                        }
                        requireContext().startActivity(Intent.createChooser(intent, ""))
                    } catch (e: Exception) {
                        with(requireContext()) {
                            e.toString().alert()
                        }
                    }
                }
            }
            try {
                val stream = array[position].inputStream()
                holder.view.setImageBitmap(BitmapFactory.decodeStream(stream))
                stream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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