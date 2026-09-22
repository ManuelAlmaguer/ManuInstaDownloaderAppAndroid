package com.manu.reeldrop.ui.screens

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import com.manu.reeldrop.domain.DownloadJob
import com.manu.reeldrop.domain.LinkAnalysis
import com.manu.reeldrop.domain.JobStatus
import com.manu.reeldrop.domain.QualityOption
import com.manu.reeldrop.domain.ServerHealth
import com.manu.reeldrop.service.DownloadService
import com.manu.reeldrop.ui.components.GlassCard
import com.manu.reeldrop.ui.components.GradientButton
import com.manu.reeldrop.ui.components.ProgressRing
import com.manu.reeldrop.ui.components.StatChip
import com.manu.reeldrop.ui.theme.LocalReelPalette
import com.manu.reeldrop.util.TermuxLauncher
import com.manu.reeldrop.util.UrlUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class HomeUiState(
    val url: String = "",
    val analysis: LinkAnalysis? = null,
    val selectedQualityId: String? = null,
    val analyzing: Boolean = false,
    val busy: Boolean = false,
    val discovering: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val health: ServerHealth = ServerHealth(reachable = false, message = "Comprobando servidor…"),
    val activeJobs: List<DownloadJob> = emptyList(),
    val completedJobs: List<DownloadJob> = emptyList(),
    val libraryCount: Int = 0,
    val serverUrl: String = "",
) {
    val urlIsValid: Boolean get() = UrlUtils.isSupportedUrl(url)
    val detectedKind: String get() = UrlUtils.contentKind(url)
}

class HomeViewModel(private val app: Application) : AndroidViewModel(app) {

    private val settings get() = ServiceLocator.settings
    private val engine get() = ServiceLocator.engine
    private val server get() = ServiceLocator.server
    private val library get() = ServiceLocator.library
    private val monitorMutex = Mutex()

    private val _state = MutableStateFlow(
        HomeUiState(serverUrl = settings.cached.serverUrl),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ServiceLocator.jobStore.jobs.collect { jobs ->
                _state.value = _state.value.copy(
                    activeJobs = jobs.filter { it.isActive || it.status == JobStatus.FAILED }.take(3),
                    completedJobs = jobs.filter { it.status == JobStatus.COMPLETED },
                )
            }
        }
        refreshHealth()
        refreshLibraryCount()
        startServerMonitor()
    }

    fun onUrlChange(value: String) {
        val clean = if (value.length > 800) value.take(800) else value
        val current = _state.value
        val sameUrl = current.url == clean
        _state.value = current.copy(
            url = clean,
            analysis = current.analysis.takeIf { sameUrl },
            selectedQualityId = current.selectedQualityId.takeIf { sameUrl },
            analyzing = false,
            message = null,
        )
    }

    fun pasteFromClipboard() {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString().orEmpty()
        val url = UrlUtils.extractUrl(text)
        if (url.isNullOrBlank()) {
            _state.value = _state.value.copy(message = "El portapapeles no contiene ningún enlace", messageIsError = true)
            return
        }
        onUrlChange(url)
        if (UrlUtils.isSupportedUrl(url)) {
            analyze()
        } else {
            _state.value = _state.value.copy(
                message = "Ojo: no parece un enlace compatible",
                messageIsError = true,
            )
        }
    }

    fun clearUrl() {
        _state.value = _state.value.copy(
            url = "",
            analysis = null,
            selectedQualityId = null,
            analyzing = false,
            message = null,
        )
    }

    fun selectQuality(quality: QualityOption) {
        _state.value = _state.value.copy(selectedQualityId = quality.id)
        viewModelScope.launch { settings.update { it.copy(qualityId = quality.id) } }
    }

    fun analyze() {
        val current = _state.value
        if (current.url.isBlank()) {
            _state.value = current.copy(message = "Pega primero un enlace de vídeo", messageIsError = true)
            return
        }
        if (!current.urlIsValid) {
            _state.value = current.copy(
                message = "El enlace debe ser de Instagram, YouTube o Facebook",
                messageIsError = true,
            )
            return
        }
        _state.value = current.copy(
            analyzing = true,
            analysis = null,
            selectedQualityId = null,
            message = "Analizando el enlace…",
            messageIsError = false,
        )
        viewModelScope.launch {
            runCatching { server.analyze(current.url) }
                .onSuccess { analyzed ->
                    val qualities = analyzed.qualities.ifEmpty {
                        listOf(
                            QualityOption(
                                id = "best",
                                label = "Mejor calidad",
                                description = "Máxima disponible · video y audio",
                            ),
                        )
                    }
                    val preferred = settings.cached.qualityId
                    val selected = qualities.firstOrNull { it.id == preferred }?.id
                        ?: qualities.first().id
                    _state.value = _state.value.copy(
                        analyzing = false,
                        analysis = analyzed.copy(qualities = qualities),
                        selectedQualityId = selected,
                        message = "Enlace analizado · elige una calidad para descargar",
                        messageIsError = false,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        analyzing = false,
                        message = error.message ?: "No se pudo analizar el enlace",
                        messageIsError = true,
                    )
                }
        }
    }

    fun download() {
        val current = _state.value
        if (current.url.isBlank()) {
            _state.value = current.copy(message = "Pega primero un enlace de vídeo", messageIsError = true)
            return
        }
        if (!current.urlIsValid) {
            _state.value = current.copy(message = "El enlace debe ser de Instagram, YouTube o Facebook", messageIsError = true)
            return
        }
        val analysis = current.analysis
        val quality = analysis?.qualities?.firstOrNull { it.id == current.selectedQualityId }
        if (analysis == null || quality == null) {
            _state.value = current.copy(
                message = "Analiza el enlace y elige una calidad antes de descargar",
                messageIsError = true,
            )
            return
        }
        _state.value = current.copy(busy = true, message = null)
        viewModelScope.launch {
            val result = engine.enqueue(current.url, quality.id)
            result.onSuccess {
                // The local job is already enqueued above. The service only keeps the
                // foreground lifecycle alive; passing the URL here would enqueue it twice.
                DownloadService.start(app.applicationContext)
                _state.value = _state.value.copy(
                    busy = false,
                    url = "",
                    analysis = null,
                    selectedQualityId = null,
                    message = "Descarga añadida a la cola · " + quality.label,
                    messageIsError = false,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    busy = false,
                    message = error.message ?: "No se pudo iniciar la descarga",
                    messageIsError = true,
                )
            }
        }
    }

    fun refreshHealth(baseUrl: String? = null) {
        viewModelScope.launch {
            monitorMutex.withLock {
                _state.value = _state.value.copy(health = _state.value.health.copy(message = "Comprobando servidor…"))
                val health = server.health(baseUrl)
                applyHealth(health)
            }
        }
    }

    fun discoverServer() {
        viewModelScope.launch {
            monitorMutex.withLock {
                _state.value = _state.value.copy(discovering = true, message = "Buscando servidor…")
                val found = server.discover()
                if (found == null) {
                    _state.value = _state.value.copy(
                        discovering = false,
                        message = "No encontré ningún servidor. Inicia Termux y ejecuta el servidor.",
                        messageIsError = true,
                        health = ServerHealth(reachable = false, message = "Inaccesible"),
                    )
                } else {
                    val (url, health) = found
                    settings.update { it.copy(serverUrl = url, lastSuccessfulServer = url) }
                    _state.value = _state.value.copy(
                        discovering = false,
                        health = health,
                        serverUrl = url,
                        message = "Servidor encontrado en $url",
                        messageIsError = false,
                    )
                }
            }
        }
    }

    private fun applyHealth(health: ServerHealth) {
        _state.value = _state.value.copy(
            health = health,
            serverUrl = settings.cached.serverUrl,
            libraryCount = health.libraryCount,
        )
    }

    private fun startServerMonitor() {
        viewModelScope.launch {
            delay(5_000L)
            while (currentCoroutineContext().isActive) {
                monitorMutex.withLock {
                    if (_state.value.health.reachable) {
                        applyHealth(server.health())
                    } else {
                        val found = server.discover()
                        if (found != null) {
                            val (url, health) = found
                            settings.update { it.copy(serverUrl = url, lastSuccessfulServer = url) }
                            _state.value = _state.value.copy(
                                health = health,
                                serverUrl = url,
                                discovering = false,
                                message = null,
                                messageIsError = false,
                            )
                        } else {
                            _state.value = _state.value.copy(
                                health = ServerHealth(reachable = false, message = "Servidor no disponible"),
                                discovering = false,
                            )
                        }
                    }
                }
                delay(if (_state.value.health.reachable) 5_000L else 10_000L)
            }
        }
    }

    fun openTermux() {
        val opened = TermuxLauncher.open(app)
        _state.value = _state.value.copy(
            message = if (opened) {
                "Termux abierto. Ejecuta ./start.sh para iniciar el servidor."
            } else {
                "Termux no está instalado o no se puede abrir desde este dispositivo."
            },
            messageIsError = !opened,
        )
    }

    private fun refreshLibraryCount() {
        viewModelScope.launch {
            runCatching { library.list() }.onSuccess { items ->
                _state.value = _state.value.copy(libraryCount = items.size)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                ServiceLocator.init(app)
                HomeViewModel(app)
            }
        }
    }
}

@Composable
fun HomeScreen(
    sharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalReelPalette.current
    val context = LocalContext.current

    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            viewModel.onUrlChange(sharedUrl)
            viewModel.analyze()
            onSharedUrlConsumed()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 18.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BrandHeader(state, onOpenSettings, onRefresh = { viewModel.refreshHealth() }) }

        item {
            GlassCard {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enlace de vídeo") },
                    placeholder = { Text("Instagram, YouTube o Facebook…") },
                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = viewModel::pasteFromClipboard) {
                                Icon(Icons.Outlined.ContentPasteGo, contentDescription = "Pegar")
                            }
                            if (state.url.isNotBlank()) {
                                IconButton(onClick = viewModel::clearUrl) {
                                    Icon(Icons.Filled.CloudOff, contentDescription = "Limpiar")
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { viewModel.analyze() }),
                )

                val analysis = state.analysis
                if (analysis == null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Analiza el enlace para consultar las calidades disponibles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    GradientButton(
                        text = if (state.analyzing) "Analizando…" else "Analizar enlace",
                        onClick = viewModel::analyze,
                        enabled = state.url.isNotBlank(),
                        loading = state.analyzing,
                        icon = Icons.Filled.Search,
                    )
                } else {
                    AnalysisPreview(analysis)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Calidad disponible",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    val selectedQuality = analysis.qualities.firstOrNull { it.id == state.selectedQualityId }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(analysis.qualities) { quality ->
                            QualityChip(
                                quality = quality,
                                selected = quality.id == state.selectedQualityId,
                                onClick = { viewModel.selectQuality(quality) },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    GradientButton(
                        text = if (state.busy) "Añadiendo…"
                        else "Descargar " + (selectedQuality?.label ?: "calidad elegida"),
                        onClick = viewModel::download,
                        enabled = selectedQuality != null,
                        loading = state.busy,
                        icon = Icons.Filled.ArrowDownward,
                    )
                }

                state.message?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (state.messageIsError) palette.danger.copy(alpha = 0.14f) else palette.success.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.messageIsError) palette.danger else palette.success,
                        )
                    }
                }
            }
        }

        if (state.activeJobs.isNotEmpty()) {
            item {
                GlassCard(onClick = onOpenQueue) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val top = state.activeJobs.first()
                        ProgressRing(progress = top.fraction, size = 74.dp, strokeWidth = 8.dp) {
                            Text("${top.progress.toInt()}%", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Descargas activas",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                top.displayTitle(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${Formatters.speed(top.speedBps)} · resta ${Formatters.eta(top.etaSeconds)} · " +
                                    "${Formatters.bytes(top.downloadedBytes)} de ${Formatters.bytes(top.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accent,
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChip(
                    label = "Activas",
                    value = state.activeJobs.count { it.isActive }.toString(),
                    icon = Icons.Filled.Bolt,
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Completadas",
                    value = state.completedJobs.size.toString(),
                    icon = Icons.Filled.CheckCircle,
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Biblioteca",
                    value = state.libraryCount.toString(),
                    icon = Icons.Filled.Cloud,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            ServerCard(
                state,
                onDiscover = viewModel::discoverServer,
                onOpenSettings = onOpenSettings,
                onOpenTermux = viewModel::openTermux,
            )
        }

        item {
            GlassCard {
                Text("Cómo funciona", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Copia el enlace del vídeo desde Instagram, YouTube o Facebook (Compartir → ${Constants.APP_NAME} también sirve).",
                    "${Constants.APP_NAME} envía el enlace a tu servidor de Termux, que descarga con yt-dlp.",
                    "La app muestra progreso, velocidad y tiempo restante en vivo, y te avisa al terminar.",
                ).forEachIndexed { index, line ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(palette.accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(state: HomeUiState, onOpenSettings: () -> Unit, onRefresh: () -> Unit) {
    val palette = LocalReelPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.gradientBrush()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Constants.APP_NAME, style = MaterialTheme.typography.headlineMedium)
            Text(
                "Descarga vídeos a la velocidad de tu servidor",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = CircleShape,
            color = if (state.health.reachable) palette.success.copy(alpha = 0.15f) else palette.danger.copy(alpha = 0.15f),
            modifier = Modifier.clip(CircleShape),
            onClick = onOpenSettings,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (state.health.reachable) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = if (state.health.reachable) palette.success else palette.danger,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.health.reachable) "${state.health.latencyMs} ms" else "Sin servidor",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.health.reachable) palette.success else palette.danger,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onRefresh) {
            if (state.discovering) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Search, contentDescription = "Buscar servidor", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ServerCard(
    state: HomeUiState,
    onDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTermux: () -> Unit,
) {
    val palette = LocalReelPalette.current
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Servidor", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    state.serverUrl.ifBlank { "sin configurar" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.health.message?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = palette.warning, maxLines = 2)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = palette.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (state.health.reachable) "Conectado" else "Desconectado",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.health.reachable) palette.success else palette.danger,
                    )
                }
                if (state.health.freeSpaceBytes > 0) {
                    Text(
                        "Libre ${Formatters.bytes(state.health.freeSpaceBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.accent.copy(alpha = 0.14f),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
                onClick = onOpenTermux,
            ) {
                Row(
                    Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir Termux", style = MaterialTheme.typography.labelLarge, color = palette.accent)
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
                onClick = onOpenSettings,
            ) {
                Row(
                    Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Configurar", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        TextButton(
            onClick = onDiscover,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("Buscar servidor automáticamente")
        }
    }
}

@Composable
private fun AnalysisPreview(analysis: LinkAnalysis) {
    val palette = LocalReelPalette.current
    val title = analysis.title?.takeIf { it.isNotBlank() } ?: "Vídeo listo para descargar"
    val metadata = listOfNotNull(
        analysis.author?.takeIf { it.isNotBlank() },
        Formatters.duration(analysis.durationSeconds).takeUnless { it == "—" },
    ).joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.accent.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.accent.copy(alpha = 0.30f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (metadata.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                analysis.qualities.size.toString() + " calidades disponibles",
                style = MaterialTheme.typography.labelSmall,
                color = palette.accent,
            )
        }
    }
}

@Composable
private fun QualityChip(quality: QualityOption, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalReelPalette.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) palette.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, palette.accent.copy(alpha = 0.6f)) else null,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        onClick = onClick,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                quality.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) palette.accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                quality.description.take(24),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
