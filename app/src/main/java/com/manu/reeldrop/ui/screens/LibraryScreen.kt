package com.manu.reeldrop.ui.screens

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import coil.compose.AsyncImage
import com.manu.reeldrop.core.Formatters
import com.manu.reeldrop.core.ServiceLocator
import com.manu.reeldrop.domain.LibraryItem
import com.manu.reeldrop.ui.components.EmptyState
import com.manu.reeldrop.ui.components.GlassCard
import com.manu.reeldrop.ui.components.SectionHeader
import com.manu.reeldrop.ui.theme.LocalReelPalette
import com.manu.reeldrop.util.DeviceDownloads
import com.manu.reeldrop.util.LocalFolder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LibrarySource(val label: String) {
    SERVER("Servidor"),
    DEVICE("Carpeta del teléfono"),
}

data class LibraryUiState(
    val items: List<LibraryItem> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val cleaningUp: Boolean = false,
    val source: LibrarySource = LibrarySource.SERVER,
    val localVideos: List<LocalFolder.LocalVideo> = emptyList(),
    val localFolderConfigured: Boolean = false,
    val localFolderAccessible: Boolean = false,
)

class LibraryViewModel(private val app: Application) : AndroidViewModel(app) {

    private val library get() = ServiceLocator.library
    private val settings get() = ServiceLocator.settings

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun setSource(source: LibrarySource) {
        _state.value = _state.value.copy(source = source)
        if (source == LibrarySource.DEVICE) refreshLocal()
    }

    /** Lists the videos inside the folder the user picked with the system file picker. */
    fun refreshLocal() {
        val tree = settings.cached.saveFolderUri
        val configured = tree.isNotBlank()
        val accessible = configured && LocalFolder.isAccessible(app, tree)
        val videos = if (accessible) LocalFolder.list(app, tree) else emptyList()
        _state.value = _state.value.copy(
            localVideos = videos,
            localFolderConfigured = configured,
            localFolderAccessible = accessible,
        )
    }

    fun deleteLocal(video: LocalFolder.LocalVideo) {
        LocalFolder.delete(app, video.uri)
        refreshLocal()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { library.list(_state.value.query) }
                .onSuccess { items -> _state.value = _state.value.copy(items = items, loading = false) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = error.message ?: "No se pudo leer la biblioteca",
                    )
                }
        }
        refreshLocal()
    }

    fun delete(item: LibraryItem) {
        viewModelScope.launch {
            runCatching { library.delete(item.name) }
                .onSuccess {
                    _state.value = _state.value.copy(items = _state.value.items.filterNot { it.name == item.name })
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(error = error.message ?: "No se pudo eliminar")
                }
        }
    }

    fun saveToDevice(item: LibraryItem) {
        DeviceDownloads.saveToDevice(app, settings, item.name, library.fileUrl(item.name))
    }

    fun cleanup() {
        viewModelScope.launch {
            _state.value = _state.value.copy(cleaningUp = true)
            runCatching { library.cleanup() }
            _state.value = _state.value.copy(cleaningUp = false)
            refresh()
        }
    }

    fun filtered(): List<LibraryItem> {
        val query = _state.value.query.trim().lowercase()
        return if (query.isEmpty()) {
            _state.value.items
        } else {
            _state.value.items.filter { it.name.lowercase().contains(query) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                ServiceLocator.init(app)
                LibraryViewModel(app)
            }
        }
    }
}

@Composable
fun LibraryScreen(
    initialSelectedFile: String?,
    onPlayerClosed: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val palette = LocalReelPalette.current
    var playing by remember { mutableStateOf<LibraryItem?>(null) }

    val visible = viewModel.filtered()

    LaunchedEffect(state.items.size) {
        if (state.items.isEmpty() && state.error == null) viewModel.refresh()
    }

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp, 18.dp, 16.dp, 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "Biblioteca",
                subtitle = when (state.source) {
                    LibrarySource.SERVER -> "${state.items.size} archivos en el servidor"
                    LibrarySource.DEVICE -> "${state.localVideos.size} videos en la carpeta del teléfono"
                },
                actionText = if (state.cleaningUp) "Limpiando…" else "Limpiar temporales",
                onAction = { viewModel.cleanup() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySource.entries.forEach { source ->
                    FilterChip(
                        selected = state.source == source,
                        onClick = { viewModel.setSource(source) },
                        label = { Text(source.label) },
                    )
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por nombre…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refrescar")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }

        if (state.source == LibrarySource.DEVICE) {
            when {
                !state.localFolderConfigured -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Sin carpeta personalizada",
                    message = "Elige una carpeta en Ajustes › Carpeta de descargas para guardar y ver tus videos aquí.",
                    actionText = "Refrescar",
                    onAction = { viewModel.refreshLocal() },
                )

                state.localVideos.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.VideoLibrary,
                    title = "Carpeta vacía",
                    message = if (state.localFolderAccessible) {
                        "Aún no hay videos en la carpeta que elegiste."
                    } else {
                        "ReelDrop perdió el permiso de esa carpeta. Vuelve a elegirla en Ajustes."
                    },
                    actionText = "Refrescar",
                    onAction = { viewModel.refreshLocal() },
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.localVideos, key = { it.uri.toString() }) { video ->
                        LocalVideoCard(
                            video = video,
                            onPlay = {
                                runCatching { context.startActivity(LocalFolder.playIntent(video)) }
                                    .onFailure { viewModel.refreshLocal() }
                            },
                            onShare = {
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(LocalFolder.shareIntent(video), "Compartir video"),
                                    )
                                }
                            },
                            onDelete = { viewModel.deleteLocal(video) },
                        )
                    }
                }
            }
        } else when {
            state.loading && state.items.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            visible.isEmpty() -> EmptyState(
                icon = Icons.Outlined.VideoLibrary,
                title = if (state.error != null) "Sin conexión" else "Biblioteca vacía",
                message = state.error
                    ?: "Los videos descargados se guardan en la carpeta `downloads` de tu servidor.",
                actionText = "Reintentar",
                onAction = { viewModel.refresh() },
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visible, key = { it.name }) { item ->
                    LibraryCard(
                        item = item,
                        onPlay = { playing = item },
                        onSave = { viewModel.saveToDevice(item) },
                        onShare = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, ServiceLocator.library.fileUrl(item.name))
                            }
                            context.startActivity(Intent.createChooser(share, "Compartir enlace"))
                        },
                        onDelete = { viewModel.delete(item) },
                    )
                }
            }
        }
    }

    playing?.let { item ->
        PlayerSheet(
            item = item,
            onDismiss = {
                playing = null
                onPlayerClosed()
            },
        )
    }
}

@Composable
private fun LocalVideoCard(
    video: LocalFolder.LocalVideo,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalReelPalette.current
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .height(132.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = video.uri,
                    contentDescription = video.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(14.dp)),
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    onClick = onPlay,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = palette.accent,
                        modifier = Modifier.padding(6.dp).size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(
                        Formatters.bytes(video.size),
                        "carpeta del teléfono",
                        Formatters.relativeTime(video.lastModified),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir", tint = palette.accent)
                    }
                    IconButton(onClick = onShare) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "Compartir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = palette.danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(
    item: LibraryItem,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val palette = LocalReelPalette.current

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .aspectRatio(0.78f)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(14.dp)),
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    onClick = onPlay,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = palette.accent,
                        modifier = Modifier.padding(6.dp).size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        Formatters.bytes(item.size),
                        item.resolution,
                        item.durationSeconds?.let { Formatters.duration(it) },
                        Formatters.relativeTime(item.modified),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir", tint = palette.accent)
                    }
                    IconButton(onClick = onSave) {
                        Icon(Icons.Filled.Save, contentDescription = "Guardar en el dispositivo", tint = palette.accent)
                    }
                    IconButton(onClick = onShare) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "Compartir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Más",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Eliminar del servidor") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
