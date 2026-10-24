package trust.me.broadcaster

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import trust.me.broadcaster.dataclasses.ExternalConfig
import trust.me.broadcaster.dataclasses.ReactorConfig
import trust.me.broadcaster.screens.HomeScreen
import trust.me.broadcaster.screens.ReactorConfigScreen
import trust.me.broadcaster.ui.theme.BroadcasterTheme
import java.io.IOException
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
    private fun isValidRegex(pattern: String): Boolean {
        return try {
            Pattern.compile(pattern)
            true
        } catch (_: Exception) {
            false
        }
    }

    private val allowedVersions = setOf("1.0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val configType = object : TypeToken<List<ReactorConfig>>() {}.type
        val appSettings = AppSettings(this)
        val gson = Gson()
        val gsonPretty = GsonBuilder().setLenient().setPrettyPrinting().create()

        setContent {
            BroadcasterTheme(darkTheme = true) {
                var currentScreen by remember { mutableStateOf("home") }
                val context = this@MainActivity

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri ?: return@rememberLauncherForActivityResult

                    try {
                        val json = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: throw IOException("Unable to read file")
                        Log.d("ImportLauncher", "Loaded: $json")

                        // Validate before saving
                        val externalConfig = gson.fromJson(json, ExternalConfig::class.java)
                        Log.d("ImportLauncher", "Converted: $externalConfig")

                        if (externalConfig.version !in allowedVersions) throw IllegalStateException("'${externalConfig.version}' is not supported!")

                        require(externalConfig != null) { "Invalid reactor configuration" }

                        // Load prefs (includes ReactorConfig in one of its keys)
                        for ((key, value) in externalConfig.settings) {
                            appSettings.string(key, value)
                        }

                        // Just in case some desync scenarios
                        appSettings.string(AppSettings.Keys.ReactorConfig, gson.toJson(externalConfig.reactors))

                        Toast.makeText(
                            context,
                            "Imported ${externalConfig.reactors.size} reactors and ${externalConfig.settings.size - 1} preferences",
                            Toast.LENGTH_SHORT
                        ).show()

                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Import failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri ?: return@rememberLauncherForActivityResult

                    try {
                        val externalConfig = ExternalConfig(
                            version = "1.0",
                            reactors = gson.fromJson(appSettings.string(AppSettings.Keys.ReactorConfig), object: TypeToken<List<ReactorConfig>>() {}.type) ?: emptyList(),
                            settings = mapOf(*AppSettings.Keys.entries.map { it to appSettings.string(it) }.toTypedArray())
                        )
                        Log.d("ExportLauncher", "Config: $externalConfig")
                        val json = gsonPretty.toJson(externalConfig)
                        Log.d("ExportLauncher", "JSON: $json")

                        context.contentResolver.openOutputStream(uri, "wt")
                            ?.bufferedWriter()
                            ?.use { it.write(json) }
                            ?: throw IOException("Unable to write file")

                        Toast.makeText(
                            context,
                            "Configuration exported",
                            Toast.LENGTH_SHORT
                        ).show()

                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Export failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                when (currentScreen.lowercase()) {
                    "home" -> HomeScreen(
                        onPatternChange = { if (isValidRegex(it)) appSettings.string(AppSettings.Keys.Regex, it) },
                        onNavigateToReactors = { currentScreen = "config" },
                        initialPattern = appSettings.string(AppSettings.Keys.Regex).takeIf { it.isNotEmpty() && isValidRegex(it) } ?: ".*",
                        onImportRequest = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        onExportRequest = { exportLauncher.launch(arrayOf("application/json")) },
                    )
                    "config" -> ReactorConfigScreen(
                        onSaveConfigs = { items ->
                            appSettings.string(AppSettings.Keys.ReactorConfig, gson.toJson(items))
                        },
                        onBack = { currentScreen = "home" },
                        initialConfigs = appSettings.string(AppSettings.Keys.ReactorConfig)
                            .takeIf { it.isNotEmpty() }
                            ?.let { gson.fromJson(it, configType) } ?: emptyList()
                    )
                }
            }
        }
    }
}

