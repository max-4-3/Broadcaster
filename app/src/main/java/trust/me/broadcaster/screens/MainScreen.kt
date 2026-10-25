package trust.me.broadcaster.screens

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import trust.me.broadcaster.dataclasses.AppInfo
import trust.me.broadcaster.services.NotificationService
import trust.me.broadcaster.ui.theme.BroadcasterTheme


@SuppressLint("QueryPermissionsNeeded")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBack: () -> Unit,
    onNavigateToConfig: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(false) }
    var alive by remember { mutableStateOf(false) }
    var showAppList by remember { mutableStateOf(false) }

    try {
        granted = hasNotificationAccess(ctx)
        alive = NotificationService.alive
    } catch (_: NoClassDefFoundError) {  }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                actions = {
                    IconButton(onClick = onNavigateToConfig) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Open settings"
                        )
                    }
                },
            )
        },
        modifier = Modifier.fillMaxHeight().fillMaxWidth()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TextButton(onClick = { showAppList = !showAppList }) {
                    Text("Show Apps")
                }
            }

            item {

                // Permission Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notification Access", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (granted) "Granted" else "Not Granted",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        IconButton(onClick = { granted = hasNotificationAccess(ctx) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }

                        IconButton(onClick = {
                            try {
                                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                granted = hasNotificationAccess(ctx)
                            } catch (e: ActivityNotFoundException) {
                                e.printStackTrace()
                            }
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Open Settings")
                        }
                    }
                }
            }

            item {

                // Service Lifecycle & Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Service Status", style = MaterialTheme.typography.titleSmall)
                            Badge(
                                containerColor = if (alive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            ) {
                                Text(if (alive) "ACTIVE" else "STOPPED", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val componentName = ComponentName(ctx, NotificationService::class.java)
                                    NotificationListenerService.requestRebind(componentName)
                                    alive = NotificationService.alive
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Rebind")
                            }

                            OutlinedButton(
                                onClick = {
                                    val packageManager = ctx.packageManager
                                    val componentName = ComponentName(ctx, NotificationService::class.java)
                                    packageManager.setComponentEnabledSetting(
                                        componentName,
                                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                        PackageManager.DONT_KILL_APP
                                    )
                                    alive = NotificationService.alive
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Force-Stop")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAppList) {
        var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
        LaunchedEffect(Unit) {
            apps = withContext(Dispatchers.IO) {
                val pm = ctx.packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

                packages.map {
                    AppInfo(
                        packageName = it.packageName,
                        appName = pm.getApplicationLabel(it).toString(),
                        icon = pm.getApplicationIcon(it)
                    )
                }.sortedBy { it.appName }
            }
        }

        AppSelectionDialog(
            apps = apps,
            onDismiss = { showAppList = false },
            onAppSelected = {  }
        )
    }
}

@Composable
fun AppSelectionDialog(
    apps: List<AppInfo>,
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Application") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search apps...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }

                items(if (searchQuery.isEmpty()) apps else apps.filter {
                    it.packageName.contains(searchQuery, ignoreCase = true)
                            || it.appName.contains(searchQuery, ignoreCase = true)
                }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Copy package name to clipboard
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                                @Suppress("UsePropertyAccessSyntax")
                                clipboard.setPrimaryClip(ClipData.newPlainText("Package Name", app.packageName))

                                onAppSelected(app.packageName)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Render icon if available
                        app.icon?.let { drawable ->
                            val bitmap = drawable.toBitmap(width = 48, height = 48)
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = app.appName,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = app.appName, style = MaterialTheme.typography.bodyLarge)
                            Text(text = app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HomeScreenPreview() {
    BroadcasterTheme(darkTheme = true) {
        HomeScreen(
            onBack = {},
            onNavigateToConfig = {},
        )
    }
}
