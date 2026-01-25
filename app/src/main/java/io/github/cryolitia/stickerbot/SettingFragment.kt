package io.github.cryolitia.stickerbot

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.http
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.append
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
const val STICKER_PER_LINE = "sticker_per_line"

class SettingFragment : PreferenceFragmentCompat() {

    val getContent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            lifecycleScope.launch {
                with(requireContext()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(ActivityResult.resultCodeToString(result.resultCode))
                        .setMessage(result.data.toString())
                        .setNeutralButton("Close") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()
                        .show()
                }
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = SettingDataStore(requireContext().dataStore)
        setPreferencesFromResource(R.xml.setting_preference, rootKey)
        findPreference<EditTextPreference>(STICKER_PER_LINE)?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        findPreference<SwitchPreferenceCompat>(GIF_CODER)?.summaryProvider =
            Preference.SummaryProvider<SwitchPreferenceCompat> {
                if (it.isChecked) "bilibili/BurstLinker" else "nbadal/android-gif-encoder"
            }
        findPreference<Preference>(OPEN_DOCUMENT)?.setOnPreferenceClickListener {
            Intent(ACTION_GET_CONTENT).apply {
                type = "*/*"
                getContent.launch(this)
            }
            true
        }
        findPreference<EditTextPreference>(HTTP_PROXY)?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
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
                    try {
                        val response = withContext(Dispatchers.IO) {
                            client.get("https://api.telegram.org/bot$token/getMe") {
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
                        response.body<String>().alert()
                    } catch (e: Throwable) {
                        e.toString().alert()
                    } finally {
                        prepareDialog.dismiss()
                    }
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