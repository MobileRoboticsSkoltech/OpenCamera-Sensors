package net.sourceforge.opencamera.uploadservice

import android.app.Application
import net.gotev.uploadservice.protocols.multipart.MultipartUploadRequest
import net.sourceforge.opencamera.MainActivity
import net.sourceforge.opencamera.StorageUtils
import java.io.File

class FileUploadService(private val mApplication: Application, private val mStorageUtils: StorageUtils) {
    constructor(mainActivity: MainActivity) : this(mainActivity.application, mainActivity.storageUtils)

    /**
     * Upload files with the specified [tag] onto the remote server at [serverUrl].
     */
    fun uploadByTag(tag: String, serverUrl: String) {
        uploadFiles(findFilesByTag(tag), serverUrl)
    }

    /**
     * Upload [files] from this device onto the remote server at [serverUrl].
     */
    private fun uploadFiles(files: List<File>, serverUrl: String) {
        MultipartUploadRequest(mApplication, serverUrl = serverUrl).setMethod("POST").apply {
            files.forEach { addFileToUpload(filePath = it.name, parameterName = "files") }
        }.startUpload()
    }

    /**
     * Find files in the save directory with name starting with the specified [tag].
     */
    private fun findFilesByTag(tag: String): List<File> {
        val saveDir = if (mStorageUtils.isUsingSAF) mStorageUtils.saveLocationSAF else mStorageUtils.saveLocation
        return File(saveDir).listFiles()?.filter { it.name.startsWith("${tag}_") } ?: emptyList()
    }
}
