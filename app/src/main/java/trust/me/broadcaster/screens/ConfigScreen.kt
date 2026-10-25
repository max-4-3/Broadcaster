package trust.me.broadcaster.screens

import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import trust.me.broadcaster.AppSettings
import trust.me.broadcaster.dataclasses.ExternalConfig
import trust.me.broadcaster.dataclasses.ReactorConfig
import trust.me.broadcaster.ui.theme.BroadcasterTheme
import java.io.IOException
import kotlin.enums.enumEntries

private val gson = GsonBuilder().setStrictness(Strictness.LENIENT).create()
private val gsonPretty = GsonBuilder().setPrettyPrinting().setStrictness(Strictness.LENIENT).create()
private val reactorListType = object : TypeToken<List<ReactorConfig>>() {}.type

private fun SharedPreferences.readSettings(): Map<AppSettings.Keys, Any?> =
    all.mapNotNull { (name, value) ->
        val key = enumEntries<AppSettings.Keys>().firstOrNull { it.name == name }
            ?: return@mapNotNull null
        if (key == AppSettings.Keys.ReactorConfig) return@mapNotNull null
        key to value
    }.toMap()

private fun SharedPreferences.readReactors(): List<ReactorConfig> =
    runCatching {
        gson.fromJson<List<ReactorConfig>>(
            getString(AppSettings.Keys.ReactorConfig.name, null) ?: "[]",
            reactorListType
        )
    }.getOrNull() ?: emptyList()

private fun SharedPreferences.writeSetting(key: AppSettings.Keys, value: Any) {
    edit {
        when (value) {
            is Boolean -> putBoolean(key.name, value)
            is String -> putString(key.name, value)
            is Int -> putInt(key.name, value)
            is Long -> putLong(key.name, value)
            is Float -> putFloat(key.name, value)
            is Set<*> -> if (value.all { it is String }) {
                putStringSet(key.name, value.filterIsInstance<String>().toSet())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onBack: () -> Unit,
    defaultSettings: Map<AppSettings.Keys, Any?>? = null,
    defaultReactorConfigs: List<ReactorConfig>? = null,
    shouldShowSettingConfig: (AppSettings.Keys) -> Boolean = { true },
    shouldShowReactorConfig: (ReactorConfig) -> Boolean = { true },
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val previewMode = LocalInspectionMode.current

    val prefs = remember(context, previewMode) {
        if (previewMode) null else AppSettings.forContext(context)
    }

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

            if (externalConfig.version !in AppSettings.allowedVersions) throw IllegalStateException("'${externalConfig.version}' is not supported!")

            require(externalConfig != null) { "Invalid reactor configuration" }

            // Load prefs (includes ReactorConfig in one of its keys)
            AppSettings.load(externalConfig.settings)

            // Just in case some desync scenarios
            AppSettings.string(AppSettings.Keys.ReactorConfig, gson.toJson(externalConfig.reactors))

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
            Log.e("ImportLauncher", "Import Failure: $e", e)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult

        try {
            val externalConfig = ExternalConfig(
                version = "1.0",
                reactors = gson.fromJson(
                    AppSettings.string(AppSettings.Keys.ReactorConfig),
                    object : TypeToken<List<ReactorConfig>>() {}.type
                ) ?: emptyList(),
                settings = AppSettings.dump()
            )

            Log.d("ExportLauncher", "Config: $externalConfig")
            val json = gsonPretty.toJson(externalConfig)

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
            Log.e("ExportLauncher", "Export Failure: $e", e)
        }
    }

    var settings by remember(prefs, defaultSettings) {
        mutableStateOf(defaultSettings ?: prefs?.readSettings().orEmpty())
    }
    var reactors by remember(prefs, defaultReactorConfigs) {
        mutableStateOf(defaultReactorConfigs ?: prefs?.readReactors().orEmpty())
    }

    DisposableEffect(prefs) {
        if (prefs == null) {
            onDispose { }
        } else {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, name ->
                if (name == AppSettings.Keys.ReactorConfig.name) {
                    reactors = changed.readReactors()
                } else {
                    settings = changed.readSettings()
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            // Close the small gap between initial read and listener registration.
            settings = prefs.readSettings()
            reactors = prefs.readReactors()
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Config") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Import external config"
                        )
                    }
                    IconButton(onClick = { exportLauncher.launch("app-config.json") }) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Export external config"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item {
                SettingsConfigSection(
                    settings = settings,
                    onConfigChange = { key, value ->
                        settings = settings + (key to value)
                        prefs?.writeSetting(key, value)
                    },
                    shouldShowConfig = shouldShowSettingConfig
                )
            }

            item {
                ReactorConfigSection(
                    reactorConfigs = reactors,
                    onConfigChange = { updated ->
                        reactors = updated
                        prefs?.edit {
                            putString(AppSettings.Keys.ReactorConfig.name, gson.toJson(updated))
                        }
                    },
                    shouldShowConfig = shouldShowReactorConfig
                )
            }
        }
    }
}

@Composable
fun SettingsConfigSection(
    modifier: Modifier = Modifier,
    settings: Map<AppSettings.Keys, Any?>,
    onConfigChange: (AppSettings.Keys, Any) -> Unit,
    shouldShowConfig: (AppSettings.Keys) -> Boolean,
) {
    val context = LocalContext.current

    Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("General settings", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { 
                    for ((key, value) in settings) {
                        onConfigChange(key, value ?: continue)
                    }

                    Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Save reactors")
                }
            }

            settings.forEach { (settingKey, value) ->
                if (!shouldShowConfig(settingKey)) return@forEach

                key(settingKey) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(settingKey.name, modifier = Modifier.weight(1f))
                        when (value) {
                            is Boolean -> Switch(
                                checked = value,
                                onCheckedChange = { onConfigChange(settingKey, it) }
                            )
                            is String -> OutlinedTextField(
                                value = value,
                                onValueChange = { onConfigChange(settingKey, it) },
                                modifier = Modifier.weight(1.5f),
                                singleLine = true
                            )
                            is Int -> {
                                var input by remember(settingKey) { mutableStateOf(value.toString()) }
                                LaunchedEffect(value) {
                                    if (input.toIntOrNull() != value) input = value.toString()
                                }
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { text ->
                                        input = text
                                        text.toIntOrNull()?.let { onConfigChange(settingKey, it) }
                                    },
                                    modifier = Modifier.weight(1.5f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                            else -> Text(value?.toString() ?: "Not set")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReactorConfigSection(
    modifier: Modifier = Modifier,
    reactorConfigs: List<ReactorConfig>,
    onConfigChange: (List<ReactorConfig>) -> Unit,
    shouldShowConfig: (ReactorConfig) -> Boolean,
) {
    val context = LocalContext.current

    Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reactor configurations", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { 
                    onConfigChange(reactorConfigs) 

                    Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Save reactors")
                }
                IconButton(onClick = { onConfigChange(reactorConfigs + ReactorConfig()) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add reactor")
                }
            }
            reactorConfigs.forEachIndexed { index, config ->
                if (!shouldShowConfig(config)) return@forEachIndexed

                key(index) {
                    ReactorConfigSectionCard(
                        config = config,
                        onConfigChanged = { updated ->
                            onConfigChange(reactorConfigs.toMutableList().apply { set(index, updated) })
                        },
                        onDelete = {
                            onConfigChange(reactorConfigs.toMutableList().apply { removeAt(index) })
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ReactorConfigSectionCard(
    config: ReactorConfig,
    onConfigChanged: (ReactorConfig) -> Unit,
    onDelete: () -> Unit
) {
    var expectedText by remember { mutableStateOf(config.expected.joinToString(", ")) }
    LaunchedEffect(config.expected) {
        if (expectedText.split(",").mapNotNull { it.trim().toIntOrNull() } != config.expected) {
            expectedText = config.expected.joinToString(", ")
        }
    }
    val highlighter = remember {
        VisualTransformation { text ->
            TransformedText(highlightLua(text.text), OffsetMapping.Identity)
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = config.name,
                    onValueChange = { onConfigChanged(config.copy(name = it)) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete config",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config.method,
                    onValueChange = { onConfigChanged(config.copy(method = it.uppercase())) },
                    label = { Text("Method") },
                    singleLine = true,
                    modifier = Modifier.width(110.dp)
                )
                OutlinedTextField(
                    value = config.endpoint,
                    onValueChange = { onConfigChanged(config.copy(endpoint = it)) },
                    label = { Text("Endpoint URL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = config.ctype,
                onValueChange = { onConfigChanged(config.copy(ctype = it)) },
                label = { Text("Content-Type") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = config.bodyLua,
                onValueChange = { onConfigChanged(config.copy(bodyLua = it)) },
                label = { Text("Body Template") },
                visualTransformation = highlighter,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                minLines = 3,
                maxLines = 20,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = expectedText,
                onValueChange = { text ->
                    expectedText = text
                    onConfigChanged(config.copy(expected = text.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }))
                },
                label = { Text("Expected Status Codes (e.g. 200, 204)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConfigScreenPreview() {
    BroadcasterTheme(darkTheme = true) {
        ConfigScreen(
            onBack = {},
            defaultSettings = mapOf(
                AppSettings.Keys.Regex to "Hello, World!",
                AppSettings.Keys.ShowNotifications to false
            ),
            defaultReactorConfigs = listOf(ReactorConfig(
                name = "Telegram",
                method = "POST"
            ))
        )
    }
}
