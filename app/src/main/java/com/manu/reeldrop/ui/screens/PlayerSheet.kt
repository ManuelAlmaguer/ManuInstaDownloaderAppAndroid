package com.manu.reeldrop.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.manu.reeldrop.core.ServiceLocator
import com.manu.reeldrop.domain.LibraryItem
import com.manu.reeldrop.util.DeviceDownloads

/** Bottom sheet player so the user can preview a downloaded reel without leaving the app. */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun PlayerSheet(item: LibraryItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val url = ServiceLocator.library.fileUrl(item.name)

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .height(420.dp),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Servido por tu Termux · ${ServiceLocator.settings.cached.serverUrl}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    DeviceDownloads.saveToDevice(context, ServiceLocator.settings, item.name, url)
                }) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  Guardar en el dispositivo")
                }
                TextButton(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, url)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Compartir enlace"))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("  Compartir")
                }
            }
        }
    }
}
