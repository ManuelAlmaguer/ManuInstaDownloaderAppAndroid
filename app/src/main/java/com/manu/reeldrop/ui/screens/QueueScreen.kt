package com.manu.reeldrop.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.manu.reeldrop.core.ServiceLocator
import com.manu.reeldrop.domain.DownloadJob
import com.manu.reeldrop.domain.JobStatus
import com.manu.reeldrop.service.DownloadService
import com.manu.reeldrop.ui.components.EmptyState
import com.manu.reeldrop.ui.components.JobCard
import com.manu.reeldrop.ui.components.SectionHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class QueueFilter(val label: String) {
    ACTIVE("Activas"),
    COMPLETED("Completadas"),
    FAILED("Con error"),
    ALL("Todas"),
}

class QueueViewModel(private val app: Application) : AndroidViewModel(app) {

    private val engine get() = ServiceLocator.engine

    private val _filter = MutableStateFlow(QueueFilter.ALL)
    val filter: StateFlow<QueueFilter> = _filter.asStateFlow()

    val jobs: StateFlow<List<DownloadJob>> = ServiceLocator.jobStore.jobs

    fun setFilter(value: QueueFilter) {
        _filter.value = value
    }

    fun visibleJobs(all: List<DownloadJob>): List<DownloadJob> = when (_filter.value) {
        QueueFilter.ACTIVE -> all.filter { it.isActive }
        QueueFilter.COMPLETED -> all.filter { it.status == JobStatus.COMPLETED }
        QueueFilter.FAILED -> all.filter { it.status == JobStatus.FAILED || it.status == JobStatus.CANCELED }
        QueueFilter.ALL -> all
    }

    fun cancel(localId: String) {
        engine.cancel(localId)
    }

    fun retry(localId: String) {
        engine.retry(localId)
        DownloadService.start(app.applicationContext)
    }

    fun save(localId: String) = engine.saveToDevice(localId)

    fun remove(localId: String) {
        viewModelScope.launch { engine.remove(localId) }
    }

    fun clearFinished() = engine.clearFinished()

    fun cancelAll() = engine.cancelAll()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                ServiceLocator.init(app)
                QueueViewModel(app)
            }
        }
    }
}

@Composable
fun QueueScreen(
    onOpenLibrary: () -> Unit,
    viewModel: QueueViewModel = viewModel(factory = QueueViewModel.Factory),
) {
    val allJobs by viewModel.jobs.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val visible = viewModel.visibleJobs(allJobs)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = "Descargas",
                subtitle = "${allJobs.count { it.isActive }} activas · ${allJobs.size} en total",
                actionText = if (allJobs.any { it.isActive }) "Cancelar todas" else null,
                onAction = { viewModel.cancelAll() },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QueueFilter.entries.forEach { option ->
                    FilterChip(
                        selected = option == filter,
                        onClick = { viewModel.setFilter(option) },
                        label = { Text(option.label) },
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Download,
                    title = "Nada por aquí",
                    message = "Cuando descargues un reel aparecerá en esta lista con su progreso en vivo.",
                    actionText = "Ver biblioteca",
                    onAction = onOpenLibrary,
                )
            }
        } else {
            items(visible, key = { it.localId }) { job ->
                JobCard(
                    job = job,
                    onCancel = { viewModel.cancel(job.localId) },
                    onRetry = { viewModel.retry(job.localId) },
                    onSave = { viewModel.save(job.localId) },
                    onRemove = { viewModel.remove(job.localId) },
                    onCopyLink = { clipboard.setText(AnnotatedString(job.url)) },
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.TextButton(onClick = { viewModel.clearFinished() }) {
                        Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.height(18.dp))
                        Text("  Limpiar finalizadas")
                    }
                }
                Text(
                    "El historial se guarda en el teléfono y sobrevive a reinicios de la app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
    }
}
