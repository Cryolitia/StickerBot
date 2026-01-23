package io.github.cryolitia.stickerbot

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException


lateinit var stickerRoot: File
lateinit var cacheRoot: File

val defaultRootProjection = arrayOf(
    Root.COLUMN_ROOT_ID,
    Root.COLUMN_ICON,
    Root.COLUMN_TITLE,
    Root.COLUMN_FLAGS,
    Root.COLUMN_DOCUMENT_ID,
    Root.COLUMN_MIME_TYPES
)

val defaultDocumentProjection = arrayOf(
    Document.COLUMN_DOCUMENT_ID,
    Document.COLUMN_MIME_TYPE,
    Document.COLUMN_DISPLAY_NAME,
    Document.COLUMN_LAST_MODIFIED,
    Document.COLUMN_FLAGS
)


class StickerDocument : DocumentsProvider() {

    override fun onCreate(): Boolean {
        return try {
            stickerRoot = File(context!!.getExternalFilesDir(null), "Stickers")
            cacheRoot = File(context!!.externalCacheDir, "Stickers")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: defaultRootProjection)
        result.newRow().apply {
            add(Root.COLUMN_ROOT_ID, "root")
            add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            add(
                Root.COLUMN_TITLE,
                context!!.getString(R.string.app_name)
            )
            add(
                Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY
            )
            add(Root.COLUMN_DOCUMENT_ID, "root")
            add(Root.COLUMN_MIME_TYPES, "image/*\nvideo/*")
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: defaultDocumentProjection)
        includeFile(result, documentId, null)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: defaultDocumentProjection)
        if (parentDocumentId == "root" || parentDocumentId == "root_dir") {
            includeFile(result, parentDocumentId, null)
            return result
        }
        val parent = getFileForDocId(parentDocumentId)
        for (file in parent.listFiles()!!) {
            includeFile(result, null, file)
        }
        return result
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = getFileForDocId(documentId!!)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getDocumentType(documentId: String): String {
        if (documentId == "root") {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Root.MIME_TYPE_ITEM
            } else {
                super.getDocumentType(documentId)
            }
        } else if (documentId == "root_dir") {
            return Document.MIME_TYPE_DIR
        }
        val file = getFileForDocId(documentId)
        return getTypeForFile(file)
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        var file = getFileForDocId(documentId)
        if (file.isDirectory) {
            val array = file.listFiles { pathname -> pathname.isFile }
            val thumb = array?.find {
                it.name.startsWith("thumb", true)
            } ?: array?.get(0)
            if (thumb == null) {
                return null
            } else {
                file = thumb
            }
        }
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    /**
     * Get the document ID given a File.  The document id must be consistent across time.  Other
     * applications may save the ID and use it to reference documents later.
     *
     *
     * This implementation is specific to this demo.  It assumes only one root and is built
     * directly from the file structure.  However, it is possible for a document to be a child of
     * multiple directories (for example "android" and "images"), in which case the file must have
     * the same consistent, unique document ID in both cases.
     *
     * @param file the File whose document ID you want
     * @return the corresponding document ID
     */
    private fun getDocIdForFile(file: File): String {
        var path = file.absolutePath
        val isCache = path.contains("io.github.cryolitia.stickerbot/cache", true)
        // Start at first char of path under root
        val rootPath: String = if (isCache) cacheRoot.absolutePath else stickerRoot.absolutePath
        if (path == rootPath) {
            return if (isCache) "cache" else "stickers"
        }
        path = if (rootPath.endsWith("/")) {
            path.substring(rootPath.length)
        } else {
            path.substring(rootPath.length + 1)
        }
        return (if (isCache) "cache:" else "stickers:") + path
    }

    /**
     * Add a representation of a file to a cursor.
     *
     * @param result the cursor to modify
     * @param docId  the document ID representing the desired file (may be null if given file)
     * @param file   the File object representing the desired file (may be null if given docID)
     * @throws FileNotFoundException
     */
    @Throws(FileNotFoundException::class)
    private fun includeFile(result: MatrixCursor, docId: String?, file: File?) {

        if (docId == "root") {
            result.newRow().apply {
                add(Document.COLUMN_DOCUMENT_ID, "root_dir")
                add(Document.COLUMN_DISPLAY_NAME, "Root Dir")
                add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
            }
            return
        }

        if (docId == "root_dir") {
            result.newRow().apply {
                add(Document.COLUMN_DOCUMENT_ID, "stickers")
                add(Document.COLUMN_DISPLAY_NAME, "Stickers")
                add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                add(Document.COLUMN_LAST_MODIFIED, stickerRoot.lastModified())
                add(Document.COLUMN_FLAGS, Document.FLAG_DIR_PREFERS_GRID)
            }
            result.newRow().apply {
                add(Document.COLUMN_DOCUMENT_ID, "cache")
                add(Document.COLUMN_DISPLAY_NAME, "Cache")
                add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                add(Document.COLUMN_LAST_MODIFIED, cacheRoot.lastModified())
                add(Document.COLUMN_FLAGS, Document.FLAG_DIR_PREFERS_GRID)
            }
            return
        }

        var docId = docId
        var file = file
        if (docId == null) {
            docId = getDocIdForFile(file!!)
        } else {
            file = getFileForDocId(docId)
        }
        var flags = 0
        if (file.isDirectory) {
            flags = 0 or Document.FLAG_DIR_PREFERS_GRID or Document.FLAG_SUPPORTS_THUMBNAIL
        }
        val displayName = file.name
        val mimeType: String = getTypeForFile(file)
        if (mimeType.startsWith("image/")) {
            // Allow the image to be represented by a thumbnail rather than an icon
            flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
        }
        val row = result.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, docId)
        row.add(Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(Document.COLUMN_SIZE, file.length())
        row.add(Document.COLUMN_MIME_TYPE, mimeType)
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
        row.add(Document.COLUMN_FLAGS, flags)

        // Add a custom icon
        //row.add(Document.COLUMN_ICON, R.drawable.ic_launcher)
    }

    /**
     * Translate your custom URI scheme into a File object.
     *
     * @param docId the document ID representing the desired file
     * @return a File represented by the given document ID
     * @throws java.io.FileNotFoundException
     */
    @Throws(FileNotFoundException::class)
    private fun getFileForDocId(docId: String): File {
        val isCache = docId.startsWith("cache")
        var target: File = if (isCache) cacheRoot else stickerRoot
        if (docId == "cache" || docId == "stickers") {
            return target
        }
        val splitIndex = docId.indexOf(':', 1)
        return if (splitIndex < 0) {
            throw FileNotFoundException("Missing root for $docId")
        } else {
            val path = docId.substring(splitIndex + 1)
            target = File(target, path)
            if (!target.exists()) {
                throw FileNotFoundException("Missing file for $docId at $target")
            }
            target
        }
    }


    /**
     * Get a file's MIME type
     *
     * @param file the File object whose type we want
     * @return the MIME type of the file
     */
    private fun getTypeForFile(file: File): String {
        return if (file.isDirectory) {
            Document.MIME_TYPE_DIR
        } else {
            getTypeForExtension(file.extension)
        }
    }

    /**
     * Get the MIME data type of a document, given its extension.
     *
     * @param String the extension of the document
     * @return the MIME data type of a document
     */
    private fun getTypeForExtension(extension: String): String {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (mime != null) {
            return mime
        }
        return "application/octet-stream"
    }

}