package com.manu.reeldrop.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Single place where the app knows which Android permissions it needs and why.
 * The Settings screen renders this list and can request everything at once.
 */
object Permissions {

    enum class Kind { RUNTIME, SPECIAL }

    data class Requirement(
        val id: String,
        val label: String,
        val description: String,
        val kind: Kind,
        val androidPermissions: List<String> = emptyList(),
        val required: Boolean = true,
    )

    /** Permissions that appear in the "Permisos" section, already filtered by Android version. */
    fun requirements(context: Context): List<Requirement> {
        val list = mutableListOf<Requirement>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Requirement(
                id = "notifications",
                label = "Notificaciones",
                description = "Progreso de la descarga y aviso al terminar o fallar.",
                kind = Kind.RUNTIME,
                androidPermissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
            )
        }

        val media = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        }
        list += Requirement(
            id = "media",
            label = "Videos y audio",
            description = "Leer los archivos guardados y mostrarlos en la biblioteca del teléfono.",
            kind = Kind.RUNTIME,
            androidPermissions = media,
            required = false,
        )

        list += Requirement(
            id = "battery",
            label = "Descargas en segundo plano",
            description = "Evita que Android suspenda el servicio mientras se descarga un video largo.",
            kind = Kind.SPECIAL,
            required = false,
        )

        return list
    }

    fun isGranted(context: Context, requirement: Requirement): Boolean = when (requirement.kind) {
        Kind.RUNTIME -> requirement.androidPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        Kind.SPECIAL -> !isBatteryOptimized(context)
    }

    /** Runtime permissions that still need to be requested. */
    fun missingRuntimePermissions(context: Context): Array<String> =
        requirements(context)
            .filter { it.kind == Kind.RUNTIME }
            .filterNot { isGranted(context, it) }
            .flatMap { it.androidPermissions }
            .distinct()
            .toTypedArray()

    fun grantedCount(context: Context): Pair<Int, Int> {
        val all = requirements(context)
        return all.count { isGranted(context, it) } to all.size
    }

    fun isBatteryOptimized(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return !power.isIgnoringBatteryOptimizations(context.packageName)
    }
}
