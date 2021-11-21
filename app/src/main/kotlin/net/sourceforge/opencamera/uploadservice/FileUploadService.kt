package net.sourceforge.opencamera.uploadservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import net.gotev.uploadservice.protocols.multipart.MultipartUploadRequest
import net.sourceforge.opencamera.MainActivity
import java.io.File

class FileUploadService(private val mMainActivity: MainActivity) {

    /**
     * Create channel that notifies the user of file upload in progress.
     */
    fun createUploadServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager =
                mMainActivity.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel("UploadChannel", "Open Camera File Upload", NotificationManager.IMPORTANCE_LOW)
            channel.setDescription("Notification channel for uploading images in the background")
            manager.createNotificationChannel(channel)
        }
    }

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
