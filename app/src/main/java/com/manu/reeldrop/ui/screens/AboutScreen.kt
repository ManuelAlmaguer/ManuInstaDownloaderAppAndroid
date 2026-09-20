package com.manu.reeldrop.ui.screens

import android.app.Application
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.manu.reeldrop.BuildConfig
import com.manu.reeldrop.core.Constants
import com.manu.reeldrop.core.Formatters
import com.manu.reeldrop.core.ServiceLocator
import com.manu.reeldrop.domain.ServerHealth
import com.manu.reeldrop.ui.components.GlassCard
import com.manu.reeldrop.ui.components.SectionHeader
import com.manu.reeldrop.ui.components.StatChip
import com.manu.reeldrop.ui.theme.LocalReelPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AboutViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _health = MutableStateFlow(ServerHealth(reachable = false, message = "Pulsa «Comprobar servidor»"))
    val health: StateFlow<ServerHealth> = _health.asStateFlow()

    fun checkServer() {
        viewModelScope.launch {
            _health.value = ServiceLocator.server.health()
        }
    }

    init {
        checkServer()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                ServiceLocator.init(app)
                AboutViewModel(app)
            }
        }
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: AboutViewModel = viewModel(factory = AboutViewModel.Factory),
) {
    val health by viewModel.health.collectAsStateWithLifecycle()
    val palette = LocalReelPalette.current
    val context = LocalContext.current
    val settings = ServiceLocator.settings.cached

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text("Acerca de", style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(palette.gradientBrush()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(Constants.APP_NAME, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Descargador de vídeos con servidor propio (Termux)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accent,
                        )
                    }
                }
            }
        }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = palette.accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Desarrollado por", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                Text(Constants.AUTHOR_NAME, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    Constants.AUTHOR_EMAIL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.accent.copy(alpha = 0.14f),
                        onClick = { openUrl(Constants.GITHUB_REPO) },
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Code, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Repositorio en GitHub", style = MaterialTheme.typography.labelLarge, color = palette.accent)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Constants.AUTHOR_EMAIL}"))
                            runCatching { context.startActivity(intent) }
                        },
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Escribir", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChip("Servidor", if (health.reachable) "Conectado" else "Offline", modifier = Modifier.weight(1f))
                StatChip("Latencia", if (health.reachable) "${health.latencyMs} ms" else "—", modifier = Modifier.weight(1f))
                StatChip("Biblioteca", "${health.libraryCount}", modifier = Modifier.weight(1f))
            }
        }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Storage, contentDescription = null, tint = palette.accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Estado del servidor", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = viewModel::checkServer) { Text("Comprobar") }
                }
                Spacer(Modifier.height(8.dp))
                InfoLine("Dirección", settings.serverUrl)
                InfoLine("Token", if (settings.apiToken.isBlank()) "sin token" else "configurado")
                InfoLine("Versión servidor", health.version ?: "—")
                InfoLine("yt-dlp", health.ytDlpVersion ?: if (health.reachable) "no detectado" else "—")
                InfoLine("ffmpeg", if (health.ffmpegAvailable) "disponible" else "no detectado")
                InfoLine("Espacio libre", Formatters.bytes(health.freeSpaceBytes))
                InfoLine("Descargas activas", health.activeJobs.toString())
                health.message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = palette.warning)
                }
            }
        }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = palette.accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Qué puede hacer ${Constants.APP_NAME}", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Descarga vídeos de Instagram, YouTube y Facebook pegando un enlace o compartiéndolo desde otra app.",
                    "Cola de descargas con progreso en vivo: porcentaje, velocidad, tamaño y tiempo restante.",
                    "Reintentos automáticos con espera progresiva y recuperación tras perder la conexión.",
                    "Notificación de progreso con acciones y aviso al terminar o fallar.",
                    "Biblioteca con miniaturas, reproductor integrado, guardado en el dispositivo y borrado remoto.",
                    "12 temas visuales (incluido Material You) y modo claro/oscuro/automático.",
                ).forEach { line ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•", color = palette.accent, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = palette.accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Privacidad y uso responsable", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${Constants.APP_NAME} no envía tus enlaces ni tus archivos a ningún servicio externo: todo el trabajo lo hace " +
                        "tu propio servidor (Termux + yt-dlp). Las descargas se guardan en tu teléfono y la app solo " +
                        "habla con la dirección que configures. Descarga únicamente contenido propio o con permiso, " +
                        "y respeta los términos de uso de cada plataforma.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            GlassCard {
                SectionHeader("Novedades de la versión ${BuildConfig.VERSION_NAME}")
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Primera versión completa de la app Android.",
                    "Servidor PHP reescrito con cola de trabajos, progreso persistente y compatibilidad con la web antigua.",
                    "Notificaciones de progreso y de resultado con acciones.",
                    "12 temas visuales, modo claro/oscuro y color dinámico.",
                ).forEach { line ->
                    Text(
                        "· $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Créditos: yt-dlp, PHP, Jetpack Compose, Material 3, OkHttp, Coil y Media3.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Hecho con ❤️ por ${Constants.AUTHOR_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
