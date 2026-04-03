package com.venpaint.app.io

import android.content.Context
import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.venpaint.app.model.Artwork
import com.venpaint.app.model.Folder
import com.venpaint.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ArtworkStorage(private val context: Context) {
    private val gson = Gson()
    private val baseDir: File get() = File(context.filesDir, "VenPaint")
    private val artworksDir: File get() = File(baseDir, "Artworks")
    private val thumbnailsDir: File get() = File(baseDir, "Thumbnails")
    private val foldersFile: File get() = File(baseDir, "folders.json")
    private val artworksIndexFile: File get() = File(baseDir, "artworks_index.json")

    init {
        artworksDir.mkdirs()
        thumbnailsDir.mkdirs()
    }

    suspend fun saveArtwork(artwork: Artwork, thumbnail: Bitmap?): Artwork = withContext(Dispatchers.IO) {
        val artworkDir = File(artworksDir, artwork.id.toString())
        artworkDir.mkdirs()
        val projectFile = File(artworkDir, "project.vpp")
        val base = artwork.copy(projectPath = projectFile.absolutePath)
        val saved = if (thumbnail != null) {
            val thumbFile = File(thumbnailsDir, "${artwork.id}.png")
            FileUtils.saveBitmap(thumbFile, thumbnail)
            base.copy(thumbnailPath = thumbFile.absolutePath)
        } else {
            base
        }
        val artworks = loadArtworksSync().toMutableList()
        val idx = artworks.indexOfFirst { it.id == saved.id }
        if (idx >= 0) artworks[idx] = saved else artworks.add(saved)
        saveArtworksIndex(artworks)
        saved
    }

    suspend fun deleteArtwork(artworkId: Long) = withContext(Dispatchers.IO) {
        val dir = File(artworksDir, artworkId.toString())
        dir.deleteRecursively()
        File(thumbnailsDir, "$artworkId.png").delete()
        val artworks = loadArtworksSync().filter { it.id != artworkId }
        saveArtworksIndex(artworks)
    }

    suspend fun loadArtworks(): List<Artwork> = withContext(Dispatchers.IO) {
        loadArtworksSync()
    }

    private fun loadArtworksSync(): List<Artwork> {
        if (!artworksIndexFile.exists()) return emptyList()
        return try {
            val json = artworksIndexFile.readText()
            val type = object : TypeToken<List<Artwork>>() {}.type
            gson.fromJson<List<Artwork>>(json, type).sortedByDescending { it.modifiedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun loadAllFolders(): List<Folder> = withContext(Dispatchers.IO) {
        if (!foldersFile.exists()) return@withContext emptyList()
        try {
            val json = foldersFile.readText()
            val type = object : TypeToken<List<Folder>>() {}.type
            gson.fromJson<List<Folder>>(json, type).sortedBy { it.order }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun loadFolders(folderType: Folder.FolderType): List<Folder> = withContext(Dispatchers.IO) {
        loadAllFoldersSync().filter { it.type == folderType }.sortedBy { it.order }
    }

    private fun loadAllFoldersSync(): List<Folder> {
        if (!foldersFile.exists()) return emptyList()
        return try {
            val json = foldersFile.readText()
            val type = object : TypeToken<List<Folder>>() {}.type
            gson.fromJson<List<Folder>>(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveFolder(folder: Folder) = withContext(Dispatchers.IO) {
        val folders = loadAllFoldersSync().toMutableList()
        val idx = folders.indexOfFirst { it.id == folder.id }
        if (idx >= 0) folders[idx] = folder else folders.add(folder)
        foldersFile.writeText(gson.toJson(folders))
    }

    suspend fun deleteFolder(folderId: Long) = withContext(Dispatchers.IO) {
        val folders = loadAllFoldersSync().filter { it.id != folderId }
        foldersFile.writeText(gson.toJson(folders))
    }

    private fun saveArtworksIndex(artworks: List<Artwork>) {
        artworksIndexFile.writeText(gson.toJson(artworks))
    }
}
