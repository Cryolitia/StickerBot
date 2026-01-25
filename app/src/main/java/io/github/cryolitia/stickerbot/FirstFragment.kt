package io.github.cryolitia.stickerbot

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.bilibili.burstlinker.BurstLinker
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
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
                val metadataFile = File(
                    requireContext().getExternalFilesDir(null),
                    "Metadata/${directory.name}.json"
                )
                var metadata: StickerSet? = null

                val preference = Preference(requireContext())

                if (metadataFile.exists()) {
                    try {
                        metadata = Json.decodeFromString<StickerSet>(metadataFile.readText())
                    } catch (e: Exception) {
                        Log.w("", e)
                    }
                }

                if (metadata != null) {
                    preference.title = metadata.title
                    preference.summary = metadata.name
                } else {
                    preference.title = directory.name
                }

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
                    lifecycleScope.launch {
                        val recyclerView = RecyclerView(requireContext())
                        val flexLayoutManager = FlexboxLayoutManager(context)
                        flexLayoutManager.flexDirection = FlexDirection.ROW
                        flexLayoutManager.justifyContent = JustifyContent.SPACE_AROUND
                        recyclerView.layoutManager = flexLayoutManager

                        recyclerView.adapter = GalleryAdapter(
                            requireContext(),
                            this@FirstFragment,
                            directory.listFiles()!!
                        )
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(preference.title)
                            .setView(recyclerView)
                            .setNegativeButton("Delete") { _, _ ->
                                MaterialAlertDialogBuilder(requireContext())
                                    .setMessage("Delete?")
                                    .setNegativeButton("Confirm") { _, _ ->
                                        directory.deleteRecursively()
                                        if (metadataFile.exists()) {
                                            metadataFile.delete()
                                        }
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
                        findNavController().navigate(R.id.action_FirstFragment_to_Setting)
                        true
                    }

                    R.id.action_search -> {
                        findNavController().navigate(R.id.action_FirstFragment_to_Search)
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