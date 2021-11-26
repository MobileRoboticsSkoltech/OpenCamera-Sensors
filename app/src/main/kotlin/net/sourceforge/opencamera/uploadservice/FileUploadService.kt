package net.sourceforge.opencamera.uploadservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import net.gotev.uploadservice.UploadServiceConfig.initialize
import net.gotev.uploadservice.protocols.multipart.MultipartUploadRequest
import net.sourceforge.opencamera.BuildConfig
import net.sourceforge.opencamera.MainActivity
import java.io.File

class FileUploadService(private val mMainActivity: MainActivity) {

    private val notificationChannelID = "UploadChannel"

    /**
     * Create channel that notifies the user of file upload in progress.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    fun createNotificationChannel() {
        val manager =
            mMainActivity.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(notificationChannelID, "Open Camera File Upload", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Notification channel for uploading images in the background"
        manager.createNotificationChannel(channel)
    }

    /**
     * Initializes Upload Service with upload notification channel.
     */
    fun initializeNotificationConfig() {
        initialize(mMainActivity.application, notificationChannelID, BuildConfig.DEBUG)
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
