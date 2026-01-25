package io.github.cryolitia.stickerbot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import io.github.cryolitia.stickerbot.databinding.FragmentSearchBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

lateinit var stickerMap: Map<String, ArrayList<File>>

class SearchFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentSearchBinding.inflate(inflater)

        (requireHost() as MenuHost).addMenuProvider(object : MenuProvider {

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_search, menu)

                val searchItem = menu.findItem(R.id.search)
                val searchView = searchItem.actionView as SearchView
                searchView.queryHint = "Enter emoji..."
                searchView.setIconifiedByDefault(false)
                searchView.requestFocus()
                searchItem.expandActionView()

                searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                    override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                        findNavController().popBackStack()
                        return true
                    }

                    override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                        return true
                    }
                })

                val recyclerView = binding.searchRecyclerView
                val flexLayoutManager = FlexboxLayoutManager(context)
                flexLayoutManager.flexDirection = FlexDirection.ROW
                flexLayoutManager.justifyContent = JustifyContent.SPACE_AROUND
                recyclerView.layoutManager = flexLayoutManager

                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        if (!::stickerMap.isInitialized) {
                            val map: HashMap<String, ArrayList<File>> = HashMap()
                            val metadataDirectory =
                                File(context!!.getExternalFilesDir(null), "Metadata")
                            if (metadataDirectory.exists()) {
                                for (file in metadataDirectory.listFiles()!!) {
                                    val stickerSet =
                                        Json.decodeFromString<StickerSet>(file.readText())
                                    val stickerSetDirectory = File(
                                        context!!.getExternalFilesDir(null),
                                        "Stickers/${stickerSet.name}"
                                    )
                                    if (stickerSetDirectory.exists()) {
                                        val stickerFileMap: HashMap<String, File> = HashMap()
                                        for (file in stickerSetDirectory.listFiles()!!) {
                                            stickerFileMap[file.nameWithoutExtension] = file
                                        }
                                        for (sticker in stickerSet.stickers) {
                                            val emoji = sticker.emoji.toString()
                                            val list = map.getOrDefault(emoji, arrayListOf())
                                            if (stickerFileMap.contains(sticker.file_unique_id)) {
                                                list.add(stickerFileMap[sticker.file_unique_id]!!)
                                            }
                                            map[emoji] = list
                                        }
                                    }
                                }
                            }
                            stickerMap = map
                        }
                        stickerMap[searchView.query]?.apply {
                            recyclerView.adapter = GalleryAdapter(
                                requireContext(),
                                this@SearchFragment,
                                this.toTypedArray()
                            )
                        }
                    }

                    binding.progressBar.visibility = View.GONE

                    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean {
                            return false
                        }

                        override fun onQueryTextChange(newText: String?): Boolean {
                            stickerMap[newText]?.apply {
                                recyclerView.adapter = GalleryAdapter(
                                    requireContext(),
                                    this@SearchFragment,
                                    this.toTypedArray()
                                )
                            }
                            return true
                        }

                    })
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }

        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }
}