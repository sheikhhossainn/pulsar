package dev.pulsar.tools

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dev.pulsar.core.FileItem
import dev.pulsar.core.FilePinpointer
import dev.pulsar.core.PinpointResult
import java.io.File
import kotlin.math.max

/**
 * Tool 6: StoragePinpointerTool
 * Android Scoped Storage Media and Document Pinpointer.
 * Translates implicit voice commands ("আজকের ক্যামেরায় তোলা ছবিটা ওপেন কর")
 * into pinpointed MediaStore URIs or Scoped Storage paths.
 */
class StoragePinpointerTool(
    private val context: Context,
    private val pinpointer: FilePinpointer
) {

    fun scanMediaStoreFiles(): List<FileItem> {
        val files = mutableListOf<FileItem>()
        val nowMs = System.currentTimeMillis()

        // 1. Scan Images from MediaStore
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.SIZE,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
            )
            val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                }

                var count = 0
                while (cursor.moveToNext() && count < 50) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val mtimeSec = cursor.getLong(dateCol)
                    val size = cursor.getLong(sizeCol)
                    val relPath = cursor.getString(pathCol) ?: ""
                    val contentUri = ContentUris.withAppendedId(queryUri, id)

                    val ageMs = max(0L, nowMs - (mtimeSec * 1000L))
                    val ageMinutes = ageMs / (1000.0 * 60.0)
                    val ext = if (name.contains(".")) "." + name.substringAfterLast(".").lowercase() else ".jpg"

                    files.add(
                        FileItem(
                            path = contentUri.toString(),
                            relPath = "$relPath/$name",
                            filename = name,
                            ext = ext,
                            mtime = mtimeSec * 1000L,
                            ageMinutes = ageMinutes,
                            size = size,
                            searchBlob = "$name $relPath images camera".lowercase()
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {}

        return files
    }

    fun pinpoint(query: String): PinpointResult {
        // Refresh index from MediaStore
        val mediaFiles = scanMediaStoreFiles()
        if (mediaFiles.isNotEmpty()) {
            pinpointer.setFiles(mediaFiles)
        }
        return pinpointer.pinpointFile(query)
    }

    fun createViewIntent(fileItem: FileItem): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = if (fileItem.path.startsWith("content://")) {
            Uri.parse(fileItem.path)
        } else {
            Uri.fromFile(File(fileItem.path))
        }

        val mimeType = when (fileItem.ext) {
            ".jpg", ".jpeg" -> "image/jpeg"
            ".png" -> "image/png"
            ".pdf" -> "application/pdf"
            ".mp3" -> "audio/mpeg"
            ".m4a" -> "audio/mp4"
            else -> "*/*"
        }

        intent.setDataAndType(uri, mimeType)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }
}
