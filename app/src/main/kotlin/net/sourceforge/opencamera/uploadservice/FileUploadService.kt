package net.sourceforge.opencamera.uploadservice

import android.app.Application
import android.util.Log
import net.gotev.uploadservice.protocols.multipart.MultipartUploadRequest
import net.sourceforge.opencamera.MainActivity
import net.sourceforge.opencamera.StorageUtils
import java.io.File

class FileUploadService(private val mApplication: Application, private val mStorageUtils: StorageUtils) {
    constructor(mainActivity: MainActivity) : this(mainActivity.application, mainActivity.storageUtils)
    private val TAG = "FileUploadService"

    /**
     * Upload files with the specified [tag] onto the remote server at [serverUrl].
     */
    fun uploadByTag(tag: String, serverUrl: String) {
        Log.d(TAG, "Upload by tag")
        uploadFiles(findFilesByTag(tag), serverUrl)
    }

    /**
     * Upload [files] from this device onto the remote server at [serverUrl].
     */
    private fun uploadFiles(files: List<File>, serverUrl: String) {
        Log.d(TAG, "Start upload to server $serverUrl")
        if (files.isEmpty()) throw IllegalArgumentException()
        MultipartUploadRequest(mApplication, serverUrl = serverUrl).setMethod("POST").apply {
            files.forEach { addFileToUpload(filePath = it.path, parameterName = "files") }
        }.startUpload()
    }

    /**
     * Find files in the save directory with name starting with the specified [tag].
     */
    private fun findFilesByTag(tag: String): List<File> {
        Log.d(TAG, "Searching file by tag $tag")
        val saveDir = mStorageUtils.imageFolderPath
        val content = File(saveDir).listFiles()?.toList() ?: emptyList()
        return if (tag == "") content else
            content.filter { it.name.startsWith("${tag}_") }
    }
}
