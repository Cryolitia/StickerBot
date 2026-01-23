package io.github.cryolitia.stickerbot

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.appindexing.FirebaseAppIndex
import com.google.firebase.appindexing.builders.Indexables
import com.google.firebase.appindexing.builders.StickerBuilder
import io.github.cryolitia.stickerbot.databinding.ActivityMainBinding
import io.github.cryolitia.stickerbot.databinding.DialogDownloadBinding
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.http
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.append
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.system.exitProcess


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    lateinit var fab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {

        DynamicColors.applyToActivityIfAvailable(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            lifecycleScope.launch {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Uncaught exception on thread $thread")
                    .setMessage(throwable.toString())
                    .setNeutralButton("Close") { dialog, _ ->
                        dialog.dismiss()
                        exitProcess(-1)
                    }
                    .create()
                    .show()
            }
        }

        fab = binding.fab
        binding.fab.setOnClickListener {
            lifecycleScope.launch {
                val token = getPreference(stringPreferencesKey(TELEGRAM_BOT_TOKEN))
                if (token.isNullOrBlank()) {
                    "Please set telegram bot token in settings firstly.".toast()
                } else {
                    MaterialAlertDialogBuilder(this@MainActivity).run {
                        var textInputEditText: TextInputEditText
                        val textInputLayout: TextInputLayout = TextInputLayout(
                            this@MainActivity,
                            null,
                            com.google.android.material.R.attr.textInputOutlinedStyle
                        ).apply {
                            prefixText = "https://t.me/addstickers/"
                            textInputEditText = TextInputEditText(this.context).apply {
                                doAfterTextChanged {
                                    if (text?.contains("https://t.me/addstickers/") == true) {
                                        setText(
                                            text?.replace(
                                                "https://t.me/addstickers/".toRegex(),
                                                ""
                                            )
                                        )
                                    }
                                }
                            }
                            addView(textInputEditText)
                        }
                        setTitle("Input Stickers link")
                        setView(textInputLayout)
                        setPositiveButton(
                            "OK"
                        ) { _, _ ->
                            downloadStickers(context, textInputEditText.text.toString())
                        }
                        setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                        create()
                        show()
                        val params = textInputLayout.layoutParams
                        if (params is ViewGroup.MarginLayoutParams) {
                            val dp = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                16F,
                                resources.displayMetrics
                            ).toInt()
                            params.setMargins(dp, dp, dp, 0)
                            textInputLayout.layoutParams = params
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val appLinkIntent: Intent = intent
        val appLinkData: String? = appLinkIntent.data?.toString()
        if (appLinkData != null) {
            downloadStickers(this, appLinkData.substring(appLinkData.lastIndexOf('/') + 1))
        }
        intent.data = null
    }

    @SuppressLint("SetTextI18n")
    val downloadStickers: (context: Context, stickersUrl: String) -> Unit =
        { context, stickersUrl ->
            lifecycleScope.launch onClickPositiveButton@{
                val token = getPreference(stringPreferencesKey(TELEGRAM_BOT_TOKEN))
                if (token.isNullOrBlank()) {
                    "Please set telegram bot token in settings firstly.".toast()
                    return@onClickPositiveButton
                }
                val prepareDialog = loadingDialog()
                if (stickersUrl.isBlank()) {
                    "Stickers URL is blank!".toast()
                }
                val useProxy = getPreference(booleanPreferencesKey(USE_HTTP_PROXY), false)
                var httpProxy = ""
                if (useProxy) {
                    httpProxy = getPreference(stringPreferencesKey(HTTP_PROXY), "")
                    if (httpProxy.isBlank()) {
                        "Proxy is enabled but not set, please check in settings!".toast()
                        return@onClickPositiveButton
                    }
                }
                val client = HttpClient(OkHttp) {
                    install(ContentNegotiation) {
                        json(Json {
                            ignoreUnknownKeys = true
                        })
                    }
                    if (useProxy) {
                        engine {
                            proxy = ProxyBuilder.http(httpProxy)
                        }
                    }
                }
                val response: HttpResponse

                try {
                    response = withContext(Dispatchers.IO) {
                        client.get("https://api.telegram.org/bot$token/getStickerSet") {
                            url {
                                parameters.append("name", stickersUrl)
                            }
                            method = HttpMethod.Get
                            headers {
                                append(HttpHeaders.Accept, ContentType.Application.Json)
                            }
                        }
                    }
                    prepareDialog.dismiss()
                    if (!response.status.isSuccess()) {
                        response.status.toString().alert()
                        return@onClickPositiveButton
                    }
                } catch (e: Throwable) {
                    e.toString().alert()
                    return@onClickPositiveButton
                }

                val stickerSetResult: TelegramResult<StickerSet> = response.body()
                if (!stickerSetResult.ok || stickerSetResult.result == null) {
                    stickerSetResult.toString().alert()
                    return@onClickPositiveButton
                }
                val dialogBinding = DialogDownloadBinding.inflate(layoutInflater, null, false)
                val dialog = MaterialAlertDialogBuilder(context)
                    .setTitle(stickerSetResult.result.title)
                    .setView(dialogBinding.root)
                    .setCancelable(false)
                    .create()
                dialog.show()
                withContext(Dispatchers.IO) {
                    withContext(Dispatchers.Main) {
                        dialogBinding.StickerSetDetail.text = stickerSetResult.result.toString()
                    }
                    val stickersDirectory = File(context.getExternalFilesDir(null), "Stickers")
                    val stickerSetDirectory = File(stickersDirectory, stickerSetResult.result.name)
                    if (!stickerSetDirectory.exists()) {
                        stickerSetDirectory.mkdirs()
                    }
                    if (stickerSetResult.result.thumb != null) {
                        client.getFile(
                            token,
                            stickerSetResult.result.thumb.file_id,
                            { data, _, file_path ->
                                val extension = file_path.substring(file_path.lastIndexOf('.'))
                                val stickerFile = File(stickerSetDirectory, "thumb$extension")
                                if (!stickerFile.exists()) {
                                    withContext(Dispatchers.IO) {
                                        stickerFile.createNewFile()
                                    }
                                }
                                stickerFile.writeBytes(data)
                                try {
                                    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                                    withContext(Dispatchers.Main) {
                                        dialogBinding.StickerSetImage.setImageBitmap(bitmap)
                                        dialogBinding.StickerSetImage.visibility = View.VISIBLE
                                    }
                                } catch (e: Exception) {
                                    e.toString().toast()
                                }
                            },
                            {
                                it.toast()
                            })
                    }
                    val size = stickerSetResult.result.stickers.size
                    var i = 0
                    withContext(Dispatchers.Main) {
                        dialogBinding.linearProgressIndicator.max = size
                        dialogBinding.linearProgressIndicator.progress = 0
                    }
                    val stickerPackBuilder = Indexables.stickerPackBuilder()
                        .setName(stickerSetResult.result.title)
                        .setUrl("cryolitia://stickerset/${stickerSetResult.result.name}")
                    val stickerList = mutableListOf<StickerBuilder>()
                    for (sticker in stickerSetResult.result.stickers) {
                        client.getFile(token, sticker.file_id, { data, file_unique_id, file_path ->
                            var data = data
                            var file_path = file_path
                            withContext(Dispatchers.Main) {
                                dialogBinding.StickerDetail.text = sticker.toString()
                                dialogBinding.emoji.text = sticker.emoji ?: ""
                            }
                            if (sticker.is_video) {
                                withContext(Dispatchers.Main) {
                                    dialogBinding.StickerImage.visibility = View.GONE
                                    dialogBinding.LottieView.visibility = View.GONE
                                }
                            } else if (sticker.is_animated) {
                                withContext(Dispatchers.Main) {
                                    dialogBinding.StickerImage.visibility = View.INVISIBLE
                                    dialogBinding.LottieView.visibility = View.VISIBLE
                                }
                                withContext(Dispatchers.IO) {
                                    try {
                                        val input = ByteArrayInputStream(data)
                                        val gzip = GZIPInputStream(input)
                                        data = gzip.readAllBytes()
                                        file_path = file_path.replace(".tgs", ".json")
                                        input.close()
                                        gzip.close()
                                        withContext(Dispatchers.Main) {
                                            dialogBinding.LottieView.setAnimation(
                                                ByteArrayInputStream(data),
                                                null
                                            )
                                            dialogBinding.LottieView.playAnimation()
                                        }
                                    } catch (e: Exception) {
                                        e.toString().alert()
                                        e.printStackTrace()
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    try {
                                        dialogBinding.LottieView.visibility = View.GONE
                                        val bitmap =
                                            BitmapFactory.decodeByteArray(data, 0, data.size)
                                        dialogBinding.StickerImage.setImageBitmap(bitmap)
                                        dialogBinding.StickerImage.visibility = View.VISIBLE
                                    } catch (e: Exception) {
                                        e.toString().toast()
                                    }
                                }
                            }
                            val extension = file_path.substring(file_path.lastIndexOf('.'))
                            val stickerFile = File(stickerSetDirectory, file_unique_id + extension)
                            if (!stickerFile.exists()) {
                                withContext(Dispatchers.IO) {
                                    stickerFile.createNewFile()
                                }
                            }
                            stickerFile.writeBytes(data)
                            if (!sticker.is_animated || !sticker.is_video) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "io.github.cryolitia.stickerbot.stickerprovider",
                                    stickerFile
                                )
                                grantUriPermission(
                                    "com.google.android.inputmethod.latin",
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                                )
                                val stickerBuilder = Indexables.stickerBuilder().apply {
                                    setName(sticker.file_unique_id)
                                    setUrl("Cryolitia://StickerSet/${stickerSetResult.result.name}/${sticker.file_unique_id}")
                                    setImage(uri.toString())
                                    if (!sticker.emoji.isNullOrBlank()) {
                                        setDescription(sticker.emoji)
                                        setKeywords(sticker.emoji)
                                        setIsPartOf(
                                            Indexables.stickerPackBuilder()
                                                .setName(stickerSetResult.result.title)
                                        )
                                    }
                                }
                                stickerList.add(stickerBuilder)
                            }
                        }, {
                            it.toast()
                        })
                        withContext(Dispatchers.Main) {
                            i++
                            dialogBinding.linearProgressIndicator.setProgressCompat(i, true)
                            dialogBinding.progressText.text = "$i / $size"
                        }
                    }
                    if (stickerList.isNotEmpty()) {
                        stickerPackBuilder.setHasSticker(*stickerList.toTypedArray())
                        FirebaseAppIndex.getInstance(context).update(
                            stickerPackBuilder.build(),
                            *stickerList.map {
                                it.build()
                            }.toTypedArray()
                        )
                    }
                    withContext(Dispatchers.Main) {
                        "Finish!".toast()
                        dialog.setCancelable(true)
                        dialog.setOnDismissListener {
                            recreate()
                        }
                    }
                }
            }
        }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}

suspend fun HttpClient.getFile(
    token: String,
    file_id: String,
    onSuccess: suspend (data: ByteArray, file_unique_id: String, file_path: String) -> Unit,
    onFailure: suspend (reason: String) -> Unit
) {
    try {
        val response2 = get("https://api.telegram.org/bot$token/getFile") {
            url {
                parameters.append("file_id", file_id)
            }
            method = HttpMethod.Get
            headers {
                append(HttpHeaders.Accept, ContentType.Application.Json)
            }
        }
        if (response2.status.isSuccess()) {
            val file: TelegramResult<TelegramFile> = response2.body()
            if (file.ok && file.result != null && file.result.file_path != null) {
                val response3 = get("https://api.telegram.org/file/bot$token/${file.result.file_path}")
                if (response3.status.isSuccess()) {
                    val byteArray: ByteArray = response3.body()
                    onSuccess(byteArray, file.result.file_unique_id, file.result.file_path)
                }
            } else {
                onFailure(file.toString())
            }
        } else {
            onFailure(response2.status.toString())
        }
    } catch (e: Throwable) {
        onFailure(e.toString())
    }
}

context(context: Context) suspend fun String.toast() {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, this@toast, Toast.LENGTH_LONG).show()
    }
}

context(context: Context) suspend fun String.alert() {
    withContext(Dispatchers.Main) {
        MaterialAlertDialogBuilder(context)
            .setMessage(this@alert)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}

context(context: Context) suspend fun loadingDialog(): AlertDialog {
    return withContext(Dispatchers.Main) {
        val linearProgressIndicator = LinearProgressIndicator(context).apply {
            isIndeterminate = true
        }
        val prepareDialog = MaterialAlertDialogBuilder(context)
            .setTitle("Loading……")
            .setView(linearProgressIndicator)
            .setCancelable(false)
            .create()
        prepareDialog.show()
        val params = linearProgressIndicator.layoutParams
        if (params is ViewGroup.MarginLayoutParams) {
            val dp = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                16F,
                context.resources.displayMetrics
            ).toInt()
            params.setMargins(dp, dp, dp, 0)
            linearProgressIndicator.layoutParams = params
        }
        return@withContext prepareDialog
    }
}