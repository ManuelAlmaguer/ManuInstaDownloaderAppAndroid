package com.manu.reeldrop.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.manu.reeldrop.ui.components.GlassCard
import com.manu.reeldrop.ui.theme.LocalReelPalette
import com.manu.reeldrop.util.Permissions

/**
 * "Permisos" card: shows every Android permission ReelDrop uses, its current state and a
 * button to request it. Also exposes the battery-optimisation exemption needed for long
 * background downloads.
 */
@Composable
fun PermissionsSection(onPermissionsRequested: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalReelPalette.current
    var refreshTick by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshTick++
        onPermissionsRequested()
    }

    // Re-read the state every time the user comes back from a system dialog or the settings app.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshTick++ }

    val requirements = remember(refreshTick) { Permissions.requirements(context) }
    val (granted, total) = remember(refreshTick) { Permissions.grantedCount(context) }
    val pending = remember(refreshTick) { Permissions.missingRuntimePermissions(context) }

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Permisos de Android", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "$granted/$total",
                style = MaterialTheme.typography.labelLarge,
                color = if (granted == total) palette.success else palette.warning,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Manu ReelDrop necesita estos permisos para descargar, avisarte del progreso y guardar los videos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        requirements.forEach { requirement ->
            val isGranted = Permissions.isGranted(context, requirement)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGranted) palette.success.copy(alpha = 0.16f) else palette.warning.copy(alpha = 0.16f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.LockOpen,
                        contentDescription = null,
                        tint = if (isGranted) palette.success else palette.warning,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(requirement.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        requirement.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(6.dp))
                if (isGranted) {
                    Text(
                        "Concedido",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.success,
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.accent.copy(alpha = 0.16f),
                        onClick = {
                            when (requirement.kind) {
                                Permissions.Kind.RUNTIME -> launcher.launch(requirement.androidPermissions.toTypedArray())
                                Permissions.Kind.SPECIAL -> openBatterySettings(context)
                            }
                        },
                    ) {
                        Text(
                            if (requirement.kind == Permissions.Kind.RUNTIME) "Solicitar" else "Activar",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.accent,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (pending.isEmpty()) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else palette.accent.copy(alpha = 0.16f),
                modifier = Modifier.weight(1f),
                onClick = {
                    if (pending.isEmpty()) {
                        openAppSettings(context)
                    } else {
                        launcher.launch(pending)
                    }
                },
            ) {
                Row(
                    Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (pending.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else palette.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (pending.isEmpty()) "Revisar en el sistema" else "Solicitar todos",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (pending.isEmpty()) MaterialTheme.colorScheme.onSurface else palette.accent,
                    )
                }
            }
        }
        TextButton(onClick = { openAppSettings(context) }) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Abrir ajustes de Manu ReelDrop en Android")
        }
    }
}

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun openBatterySettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(direct) }
            .onFailure { runCatching { context.startActivity(fallback) } }
    }
}
