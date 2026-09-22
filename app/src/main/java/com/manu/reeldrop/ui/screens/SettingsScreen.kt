package com.manu.reeldrop.ui.screens

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.manu.reeldrop.core.Constants
import com.manu.reeldrop.core.Formatters
import com.manu.reeldrop.core.ServiceLocator
import com.manu.reeldrop.data.local.AppSettings
import com.manu.reeldrop.domain.Quality
import com.manu.reeldrop.domain.ServerMode
import com.manu.reeldrop.domain.ServerHealth
import com.manu.reeldrop.domain.ThemeMode
import com.manu.reeldrop.ui.components.GlassCard
import com.manu.reeldrop.ui.components.ConfirmDialog
import com.manu.reeldrop.ui.components.SectionHeader
import com.manu.reeldrop.ui.theme.LocalReelPalette
import com.manu.reeldrop.ui.theme.Palettes
import com.manu.reeldrop.util.LocalFolder
import com.manu.reeldrop.util.NetworkInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {

    private val settings get() = ServiceLocator.settings
    private val server get() = ServiceLocator.server
    private val library get() = ServiceLocator.library
    private val engine get() = ServiceLocator.engine

    private val _settings = MutableStateFlow(settings.cached)
    val state: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _health = MutableStateFlow(ServerHealth(reachable = false))
    val health: StateFlow<ServerHealth> = _health.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _folderAccessible = MutableStateFlow(false)
    val folderAccessible: StateFlow<Boolean> = _folderAccessible.asStateFlow()
    private var folderCheckJob: Job? = null
    private var checkedFolderUri: String? = null

    init {
        viewModelScope.launch {
            settings.flow.collect { value ->
                _settings.value = value
                if (value.saveFolderUri != checkedFolderUri) {
                    checkedFolderUri = value.saveFolderUri
                    folderCheckJob?.cancel()
                    val uri = value.saveFolderUri
                    folderCheckJob = viewModelScope.launch(Dispatchers.IO) {
                        _folderAccessible.value = uri.isNotBlank() && LocalFolder.isAccessible(app, uri)
                    }
                }
            }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _busy.value = true
            val result = server.health()
            _health.value = result
            _message.value = if (result.reachable) {
                "Conectado en ${result.latencyMs} ms" + (result.version?.let { " · servidor v$it" } ?: "")
            } else {
                result.message ?: "No se pudo conectar"
            }
            _busy.value = false
        }
    }

    fun discover() {
        viewModelScope.launch {
            _busy.value = true
            val found = server.discover()
            if (found == null) {
                _message.value = "No encontré ningún servidor activo en las direcciones habituales."
            } else {
                settings.update { it.copy(serverUrl = found.first, lastSuccessfulServer = found.first) }
                _health.value = found.second
                _message.value = "Servidor encontrado: ${found.first}"
            }
            _busy.value = false
        }
    }

    fun cleanupServer() {
        viewModelScope.launch {
            _busy.value = true
            val (count, bytes) = runCatching { library.cleanup() }.getOrDefault(0 to 0L)
            _message.value = "Limpiados $count archivos temporales (${Formatters.bytes(bytes)})"
            _busy.value = false
        }
    }

    fun clearFinished() = engine.clearFinished()

    fun clearAll() = viewModelScope.launch { ServiceLocator.jobStore.clearAll() }

    fun dismissMessage() {
        _message.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                ServiceLocator.init(app)
                SettingsViewModel(app)
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val folderAccessible by viewModel.folderAccessible.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val palette = LocalReelPalette.current

    var urlDraft by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var tokenDraft by remember(settings.apiToken) { mutableStateOf(settings.apiToken) }
    var tokenVisible by remember(settings.apiToken, settings.apiTokenEnabled) {
        mutableStateOf(settings.apiTokenEnabled)
    }
    var confirmCleanup by remember { mutableStateOf(false) }
    var confirmClearFinished by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val label = LocalFolder.folderName(context, uri.toString()) ?: "carpeta elegida"
        viewModel.update {
            it.copy(
                saveFolderUri = uri.toString(),
                saveFolderLabel = label,
                useCustomFolder = true,
                autoSaveToDevice = true,
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionHeader(
                title = "Ajustes",
                subtitle = "Servidor, descargas, temas y notificaciones",
            )
        }

        // ---------------------------------------------------------- servidor
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cloud, contentDescription = null, tint = palette.accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Servidor Termux", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (health.reachable) palette.success else palette.danger),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (health.reachable) "En línea" else "Sin conexión",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (health.reachable) palette.success else palette.danger,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("¿Dónde está tu servidor?", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${Constants.APP_NAME} no inicia ningún servidor ni abre el puerto 8080: solo se conecta al PHP que tú levantas en Termux.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServerMode.entries.forEach { mode ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (settings.serverModeId == mode.id) palette.accent.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            onClick = {
                                viewModel.update { it.copy(serverModeId = mode.id) }
                                urlDraft = when (mode) {
                                    ServerMode.SAME_PHONE -> "http://127.0.0.1:${mode.defaultPort}"
                                    ServerMode.LAN -> "http://${NetworkInfo.localIpv4() ?: "192.168.0.10"}:${mode.defaultPort}"
                                    ServerMode.INTERNET -> "https://"
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                mode.label,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (settings.serverModeId == mode.id) palette.accent
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    settings.serverMode.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    label = { Text("Dirección del servidor") },
                    placeholder = { Text("http://127.0.0.1:8080") },
                    supportingText = { Text("Ejemplos: http://127.0.0.1:8080 · http://192.168.1.40:8080 · https://miserver.dominio.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                NetworkInfo.localIpv4()?.let { ip ->
                    TextButton(onClick = {
                        urlDraft = "http://$ip:${settings.serverMode.defaultPort}"
                        viewModel.update { it.copy(serverUrl = urlDraft) }
                    }) {
                        Text("Usar la IP de este teléfono ($ip)")
                    }
                }
                val hostDraft = urlDraft.substringAfter("://", "").substringBefore("/").substringBefore(":")
                if (urlDraft.startsWith("http://") && hostDraft.isNotBlank() && !NetworkInfo.isPrivateHost(hostDraft)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.warning.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Aviso: si el servidor está publicado en Internet, usa https:// y define un token de API. " +
                                "Sin HTTPS, cualquiera que vea tu tráfico podría leer el token.",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.warning,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = tokenVisible,
                        onCheckedChange = { tokenVisible = it },
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("Usar token de API", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Actívalo solo si el servidor lo requiere",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (tokenVisible) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = tokenDraft,
                        onValueChange = { tokenDraft = it },
                        label = { Text("Token de API") },
                        supportingText = { Text("Se conserva para cuando vuelvas a necesitarlo") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        viewModel.update {
                            it.copy(
                                serverUrl = urlDraft.trim(),
                                apiToken = tokenDraft.trim(),
                                apiTokenEnabled = tokenVisible,
                            )
                        }
                        viewModel.testConnection()
                    }, modifier = Modifier.weight(1f)) {
                        Text("Guardar y probar")
                    }
                    TextButton(onClick = viewModel::discover) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Text("  Buscar")
                    }
                }
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = palette.accent)
                }
                if (health.reachable) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        listOfNotNull(
                            health.version?.let { v -> "Servidor v$v" },
                            health.ytDlpVersion?.let { v -> "yt-dlp $v" },
                            if (health.ffmpegAvailable) "ffmpeg OK" else "ffmpeg ausente",
                            health.freeSpaceBytes.takeIf { it > 0 }?.let { "Libre ${Formatters.bytes(it)}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---------------------------------------------------------- descargas
        item {
            GlassCard {
                SectionTitle("Descargas", Icons.Filled.Straighten)
                Spacer(Modifier.height(8.dp))
                Text("Calidad predeterminada", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Quality.entries.toList()) { quality ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (settings.qualityId == quality.id) palette.accent.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            onClick = { viewModel.update { it.copy(qualityId = quality.id) } },
                        ) {
                            Text(
                                quality.label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (settings.qualityId == quality.id) palette.accent else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SliderRow(
                    label = "Descargas simultáneas",
                    value = settings.concurrentDownloads.toFloat(),
                    valueLabel = settings.concurrentDownloads.toString(),
                    range = 1f..5f,
                    steps = 3,
                    onChange = { value -> viewModel.update { it.copy(concurrentDownloads = value.toInt()) } },
                )
                SliderRow(
                    label = "Reintentos automáticos",
                    value = settings.maxAttempts.toFloat(),
                    valueLabel = settings.maxAttempts.toString(),
                    range = 0f..5f,
                    steps = 4,
                    onChange = { value -> viewModel.update { it.copy(maxAttempts = value.toInt()) } },
                )
                ToggleRow(
                    title = "Reintentar cuando falle",
                    subtitle = "Espera y vuelve a intentarlo con backoff exponencial",
                    checked = settings.autoRetry,
                    onChange = { value -> viewModel.update { it.copy(autoRetry = value) } },
                )
                ToggleRow(
                    title = "Guardar en el dispositivo al terminar",
                    subtitle = "Copia el video a Movies/ReelDrop usando el gestor de Android",
                    checked = settings.autoSaveToDevice,
                    onChange = { value -> viewModel.update { it.copy(autoSaveToDevice = value) } },
                )
                ToggleRow(
                    title = "Solo con Wi-Fi",
                    subtitle = "Evita gastar datos móviles",
                    checked = settings.wifiOnly,
                    onChange = { value -> viewModel.update { it.copy(wifiOnly = value) } },
                )
                ToggleRow(
                    title = "Mantener la pantalla activa",
                    subtitle = "Útil para descargas largas con la app abierta",
                    checked = settings.keepScreenAwakeWhileDownloading,
                    onChange = { value -> viewModel.update { it.copy(keepScreenAwakeWhileDownloading = value) } },
                )
            }
        }

        // ---------------------------------------------------------- apariencia
        item {
            GlassCard {
                SectionTitle("Temas", Icons.Filled.Palette)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Elige el estilo visual de ${Constants.APP_NAME}. El tema se aplica al instante en toda la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    Palettes.all.size.toString() + " estilos disponibles",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palettes.all.chunked(2).forEach { rowThemes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowThemes.forEach { theme ->
                                val selected = settings.themeId == theme.theme.id
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) palette.accent.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    onClick = { viewModel.update { it.copy(themeId = theme.theme.id) } },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Brush.linearGradient(theme.gradient)),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            theme.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) palette.accent else MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (selected) {
                                            Text("Aplicado", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                                        }
                                    }
                                }
                            }
                            if (rowThemes.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Modo de color", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (settings.themeModeId == mode.id) palette.accent.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            onClick = { viewModel.update { it.copy(themeModeId = mode.id) } },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                mode.label,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (settings.themeModeId == mode.id) palette.accent else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "Color dinámico (Material You)",
                    subtitle = "Usa la paleta del fondo de pantalla en Android 12 o superior",
                    checked = settings.dynamicColor,
                    onChange = { value -> viewModel.update { it.copy(dynamicColor = value) } },
                )
            }
        }

        // ---------------------------------------------------------- notificaciones
        item {
            GlassCard {
                SectionTitle("Notificaciones", Icons.Filled.Notifications)
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = "Progreso y resultados",
                    subtitle = "Notificación con velocidad, tamaño, tiempo restante y aviso final",
                    checked = settings.notificationsEnabled,
                    onChange = { value -> viewModel.update { it.copy(notificationsEnabled = value) } },
                )
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                }) {
                    Text("Abrir ajustes de notificaciones del sistema")
                }
            }
        }

        // ---------------------------------------------------------- permisos
        item {
            PermissionsSection(
                onPermissionsRequested = { viewModel.update { it.copy(permissionsRequested = true) } },
            )
        }

        // ---------------------------------------------------------- carpeta personalizada
        item {
            GlassCard {
                SectionTitle("Carpeta de descargas", Icons.Filled.Folder)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Elige cualquier carpeta del teléfono (o de la tarjeta SD) para guardar los videos y verlos desde ${Constants.APP_NAME}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = palette.accent)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                settings.saveFolderLabel.ifBlank { "Carpeta predeterminada (Movies/ReelDrop)" },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                if (settings.saveFolderUri.isBlank()) {
                                    "Sin carpeta personalizada"
                                } else if (folderAccessible) {
                                    "Acceso concedido · los videos se guardan aquí"
                                } else {
                                    "Permiso perdido: vuelve a elegir la carpeta"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (settings.saveFolderUri.isNotBlank() && folderAccessible) {
                                    palette.success
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { folderLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Elegir carpeta")
                    }
                    if (settings.saveFolderUri.isNotBlank()) {
                        TextButton(onClick = {
                            viewModel.update { it.copy(saveFolderUri = "", saveFolderLabel = "", useCustomFolder = false) }
                        }) {
                            Text("Quitar")
                        }
                    }
                }
                ToggleRow(
                    title = "Guardar los videos en esta carpeta",
                    subtitle = "Al terminar cada descarga se copia aquí automáticamente",
                    checked = settings.useCustomFolder && settings.saveFolderUri.isNotBlank(),
                    onChange = { value -> viewModel.update { it.copy(useCustomFolder = value) } },
                )
            }
        }

        // ---------------------------------------------------------- mantenimiento
        item {
            GlassCard {
                SectionTitle("Mantenimiento", Icons.Filled.CleaningServices)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { confirmCleanup = true }) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Limpiar temporales del servidor")
                }
                TextButton(onClick = { confirmClearFinished = true }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Borrar descargas finalizadas del historial")
                }
                TextButton(onClick = { confirmClearAll = true }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Vaciar todo el historial")
                }
            }
        }

        item {
            GlassCard(onClick = onOpenAbout) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = palette.accent)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Acerca de ${Constants.APP_NAME}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Versión, autor, licencias y créditos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = palette.accent)
                }
            }
        }
    }

    if (confirmCleanup) {
        ConfirmDialog(
            title = "Limpiar temporales",
            message = "Se eliminarán del servidor los fragmentos, metadatos y descargas incompletas. Los videos terminados no se borrarán.",
            confirmLabel = "Limpiar",
            onConfirm = {
                confirmCleanup = false
                viewModel.cleanupServer()
            },
            onDismiss = { confirmCleanup = false },
        )
    }
    if (confirmClearFinished) {
        ConfirmDialog(
            title = "Borrar finalizadas",
            message = "Se eliminarán del historial todas las descargas completadas, fallidas o canceladas.",
            onConfirm = {
                confirmClearFinished = false
                viewModel.clearFinished()
            },
            onDismiss = { confirmClearFinished = false },
        )
    }
    if (confirmClearAll) {
        ConfirmDialog(
            title = "Vaciar historial",
            message = "Se eliminarán todas las descargas del historial de la aplicación. Los archivos del servidor y del teléfono no se borrarán.",
            onConfirm = {
                confirmClearAll = false
                viewModel.clearAll()
            },
            onDismiss = { confirmClearAll = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = LocalReelPalette.current.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = LocalReelPalette.current.accent)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
