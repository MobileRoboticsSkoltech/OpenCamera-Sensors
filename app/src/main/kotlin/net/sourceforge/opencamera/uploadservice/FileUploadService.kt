package net.sourceforge.opencamera.uploadservice

import net.gotev.uploadservice.protocols.multipart.MultipartUploadRequest
import net.sourceforge.opencamera.MainActivity
import java.io.File

class FileUploadService(private val mMainActivity: MainActivity) {
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
        MultipartUploadRequest(mMainActivity, serverUrl = serverUrl).setMethod("POST").apply {
            files.forEach { addFileToUpload(filePath = it.name, parameterName = "files") }
        }.startUpload()
    }

    /**
     * Find files in the save directory with name starting with the specified [tag].
     */
    private fun findFilesByTag(tag: String): List<File> {
        val storageUtils = mMainActivity.storageUtils
        val saveDir = if (storageUtils.isUsingSAF) storageUtils.saveLocationSAF else storageUtils.saveLocation
        return File(saveDir).listFiles()?.filter { it.name.startsWith("${tag}_") } ?: emptyList()
    }

}
