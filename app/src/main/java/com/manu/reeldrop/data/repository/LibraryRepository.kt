package com.manu.reeldrop.data.repository

import com.manu.reeldrop.data.remote.ReelDropApi
import com.manu.reeldrop.domain.LibraryItem

class LibraryRepository(private val api: ReelDropApi) {

    suspend fun list(query: String = ""): List<LibraryItem> =
        api.library(query).map { dto ->
            LibraryItem(
                name = dto.file,
                size = dto.size,
                modified = dto.mtime * 1000L,
                durationSeconds = dto.duration,
                width = dto.width,
                height = dto.height,
                thumbnailUrl = dto.thumb?.let { api.thumbnailUrl(dto.file) } ?: api.thumbnailUrl(dto.file),
                streamUrl = dto.url?.let { api.fileUrl(dto.file) } ?: api.fileUrl(dto.file),
                title = dto.title,
                author = dto.author,
            )
        }

    suspend fun delete(name: String) = api.deleteLibraryItem(name)

    suspend fun cleanup(): Pair<Int, Long> {
        val response = api.cleanup()
        return (response.deleted ?: 0) to (response.bytes ?: 0L)
    }

    fun fileUrl(name: String): String = api.fileUrl(name)

    fun thumbnailUrl(name: String): String = api.thumbnailUrl(name)
}
