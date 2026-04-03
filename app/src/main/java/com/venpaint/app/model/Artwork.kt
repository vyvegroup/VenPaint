package com.venpaint.app.model

import java.io.File

data class Artwork(
    val id: Long = System.currentTimeMillis(),
    val name: String = "Untitled",
    val width: Int = 1080,
    val height: Int = 1920,
    val thumbnailPath: String? = null,
    val projectPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val folderId: Long? = null
) {
    val thumbnailFile: File? get() = thumbnailPath?.let { File(it) }
    val projectFile: File? get() = projectPath?.let { File(it) }
}
