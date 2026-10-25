package trust.me.broadcaster.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import trust.me.broadcaster.dataclasses.ReactorConfig
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation


fun highlightLua(code: String): AnnotatedString {
    val keywords = setOf(
        "and", "break", "do", "else", "elseif", "end",
        "false", "for", "function", "goto", "if", "in",
        "local", "nil", "not", "or", "repeat", "return",
        "then", "true", "until", "while"
    )

    val pattern = Regex(
        """--\[\[[\s\S]*?]]|--[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b\d+(?:\.\d+)?\b|\b[A-Za-z_]\w*\b"""
    )

    return buildAnnotatedString {
        append(code)

        pattern.findAll(code).forEach { match ->
            val token = match.value

            val color = when {
                token in keywords -> Color(0xFFC586C0)
                token.startsWith("--") -> Color(0xFF6A9955)
                token.first().isDigit() -> Color(0xFFB5CEA8)
                token.startsWith("\"") || token.startsWith("'") -> Color(0xFFCE9178)
                else -> null
            }

            if (color != null) {
                addStyle(
                    SpanStyle(color = color),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactorConfigScreen(
    onSaveConfigs: (List<ReactorConfig>) -> Unit,
    onBack: () -> Unit,
    initialConfigs: List<ReactorConfig> = emptyList()
) {
    BackHandler(onBack = onBack)
    var configs by remember { mutableStateOf(initialConfigs.ifEmpty { listOf(ReactorConfig()) }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reactor Configurations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onSaveConfigs(configs) }) {
                        Text("Save")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                configs = configs + ReactorConfig()
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Config")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(configs) { index, config ->
                ReactorConfigCard(
                    config = config,
                    onConfigChanged = { updatedConfig ->
                        configs = configs.toMutableList().apply { set(index, updatedConfig) }
                    },
                    onDelete = {
                        if (configs.size > 1) {
                            configs = configs.toMutableList().apply { removeAt(index) }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ReactorConfigCard(
    config: ReactorConfig,
    onConfigChanged: (ReactorConfig) -> Unit,
    onDelete: () -> Unit
) {
    var expectedText by remember { mutableStateOf(config.expected.joinToString(", ")) }
    var name by remember { mutableStateOf(config.name) }
    val highlighter = remember {
        VisualTransformation { text ->
            TransformedText(
                highlightLua(text.text),
                OffsetMapping.Identity
            )
        }
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Header Row with Title and Delete Button
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; onConfigChanged(config.copy(name = name)) },
                    label = { Text("Name") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Config",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Method (GET/POST) & Endpoint URL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

            // Content Type
            OutlinedTextField(
                value = config.ctype,
                onValueChange = { onConfigChanged(config.copy(ctype = it)) },
                label = { Text("Content-Type") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Body Template
            OutlinedTextField(
                value = config.bodyLua,
                onValueChange = { onConfigChanged(config.copy(bodyLua = it)) },
                label = { Text("Body Template") },
                visualTransformation = highlighter,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace
                ),
                minLines = 3,
                maxLines = 20,
                modifier = Modifier.fillMaxWidth()
            )

            // Expected Status Codes
            OutlinedTextField(
                value = expectedText,
                onValueChange = {
                    expectedText = it
                    val parsedList = it.split(",")
                        .mapNotNull { token -> token.trim().toIntOrNull() }
                    onConfigChanged(config.copy(expected = parsedList))
                },
                label = { Text("Expected Status Codes (e.g. 200, 204)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
fun ReactorConfigPreview() {
    ReactorConfigScreen(onSaveConfigs = {}, onBack = {})
}
