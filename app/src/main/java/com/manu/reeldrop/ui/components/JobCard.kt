package com.manu.reeldrop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manu.reeldrop.core.Formatters
import com.manu.reeldrop.domain.DownloadJob
import com.manu.reeldrop.domain.JobStatus
import com.manu.reeldrop.ui.theme.LocalReelPalette

/** One row in the download queue with live progress, speed, ETA and contextual actions. */
@Composable
fun JobCard(
    job: DownloadJob,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val palette = LocalReelPalette.current

    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = job.displayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(job.status)
                    if (job.attempts > 1) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Intento ${job.attempts}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            ProgressRing(
                progress = job.fraction,
                size = 62.dp,
                strokeWidth = 7.dp,
            ) {
                Text(
                    text = "${job.progress.toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        LinearProgressGradient(progress = job.fraction)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatChip(
                label = "Velocidad",
                value = if (job.status == JobStatus.DOWNLOADING) Formatters.speed(job.speedBps) else "—",
                modifier = Modifier.weight(1f),
            )
            StatChip(
                label = "Restante",
                value = if (job.status == JobStatus.DOWNLOADING) Formatters.eta(job.etaSeconds) else "—",
                modifier = Modifier.weight(1f),
            )
            StatChip(
                label = "Tamaño",
                value = Formatters.bytes(job.totalBytes.takeIf { it > 0 } ?: job.downloadedBytes),
                modifier = Modifier.weight(1f),
            )
        }

        job.errorMessage?.takeIf { job.status == JobStatus.FAILED }?.let { message ->
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.danger.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.danger,
                )
            }
        }

        job.serverMessage?.takeIf { job.isActive && it.isNotBlank() }?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (job.isActive) "Iniciado ${Formatters.relativeTime(job.startedAt ?: job.createdAt)}"
                else Formatters.relativeTime(job.finishedAt ?: job.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            if (job.isActive) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Cancel, contentDescription = "Cancelar", tint = palette.danger)
                }
            } else {
                if (job.status == JobStatus.FAILED || job.status == JobStatus.CANCELED) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reintentar", tint = palette.accent)
                    }
                }
                if (job.status == JobStatus.COMPLETED) {
                    IconButton(onClick = onSave) {
                        Icon(
                            Icons.Filled.SaveAlt,
                            contentDescription = "Guardar en el dispositivo",
                            tint = palette.accent,
                        )
                    }
                }
                IconButton(onClick = onCopyLink) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copiar enlace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Quitar de la lista",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
