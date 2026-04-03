package com.venpaint.app.model

data class Folder(
    val id: Long = System.currentTimeMillis(),
    val name: String = "New Folder",
    val parentId: Long? = null,
    val type: FolderType = FolderType.ARTWORK,
    val createdAt: Long = System.currentTimeMillis(),
    val order: Int = 0
) {
    enum class FolderType {
        ARTWORK, BRUSH, FX
    }
}
