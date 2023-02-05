package io.github.cryolitia.stickerbot

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */

const val TELEGRAM_BOT_TOKEN = "telegram_bot_token"
const val USE_HTTP_PROXY = "use_http_proxy"
const val HTTP_PROXY = "http_proxy"
const val RECODE_WEBP = "recode_webp"
const val GIF_CODER = "gif_encoder"
const val QUANTIZER = "quantizer"
const val DITHER = "dither"
const val REPLACE_TRANSPARENT = "replace_transparent"
const val OPEN_DOCUMENT = "open_document"
const val TEST_BOT = "test_bot"

class SecondFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = SettingDataStore(requireContext().dataStore)
        setPreferencesFromResource(R.xml.setting_preference, rootKey)
        findPreference<SwitchPreferenceCompat>(GIF_CODER)?.summaryProvider =
            Preference.SummaryProvider<SwitchPreferenceCompat> {
                if (it.isChecked) "bilibili/BurstLinker" else "nbadal/android-gif-encoder"
            }
        findPreference<Preference>(OPEN_DOCUMENT)?.setOnPreferenceClickListener {
            Intent(ACTION_GET_CONTENT).apply {
                type = "*/*"
                startActivityForResult(this, 42)
            }
            true
        }
        findPreference<Preference>(TEST_BOT)?.setOnPreferenceClickListener {
            lifecycleScope.launch {
                with(requireContext()) {
                    val token = getPreference(stringPreferencesKey(TELEGRAM_BOT_TOKEN))
                    if (token.isNullOrBlank()) {
                        "Please set telegram bot token in settings firstly.".toast()
                        return@launch
                    }
                    val prepareDialog = loadingDialog()
                    val useProxy = getPreference(booleanPreferencesKey(USE_HTTP_PROXY), false)
                    var httpProxy = ""
                    if (useProxy) {
                        httpProxy = getPreference(stringPreferencesKey(HTTP_PROXY), "")
                        if (httpProxy.isBlank()) {
                            "Proxy is enabled but not set, please check in settings!".toast()
                            return@launch
                        }
                    }
                    val client = HttpClient(OkHttp) {
                        if (useProxy) {
                            engine {
                                proxy = ProxyBuilder.http(httpProxy)
                            }
                        }
                    }
                    val response = withContext(Dispatchers.IO) {
                        client.get("https://api.tlgr.org/bot$token/getMe") {
                            method = HttpMethod.Get
                            headers {
                                append(HttpHeaders.Accept, ContentType.Application.Json)
                            }
                        }
                    }
                    if (!response.status.isSuccess()) {
                        response.status.toString().alert()
                        return@launch
                    }
                    prepareDialog.dismiss()
                    response.body<String>().alert()
                }
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).fab.visibility = View.GONE
    }

    class SettingDataStore(val dataStore: DataStore<Preferences>) : PreferenceDataStore() {

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return runBlocking {
                dataStore.data.map {
                    it[booleanPreferencesKey(key)] ?: defValue
                }.first()
            }
        }

        override fun putBoolean(key: String, value: Boolean) {
            runBlocking {
                dataStore.edit {
                    it[booleanPreferencesKey(key)] = value
                }
            }
        }

        override fun getString(key: String, defValue: String?): String? {
            return runBlocking {
                dataStore.data.map {
                    it[stringPreferencesKey(key)] ?: defValue
                }.first()
            }
        }

        override fun putString(key: String, value: String?) {
            runBlocking {
                dataStore.edit {
                    it[stringPreferencesKey(key)] = value ?: ""
                }
            }
        }

    }
}

suspend fun <T> Context.getPreference(key: Preferences.Key<T>): T? {
    return try {
        dataStore.data.map {
            it[key]
        }.first()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun <T> Context.getPreference(key: Preferences.Key<T>, defaultValue: T): T {
    return getPreference(key) ?: defaultValue
}