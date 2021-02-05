package net.sourceforge.opencamera;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import net.sourceforge.opencamera.preview.ApplicationInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static net.sourceforge.opencamera.preview.ApplicationInterface.*;

/**
 * Provides additional functionality for capture information saving
 * (raw sensor info, frames)
 */
public class StorageUtilsWrapper extends StorageUtils {
    private static final String TAG = "StorageUtilsWrapper";

    StorageUtilsWrapper(Context context, MyApplicationInterface applicationInterface) {
        super(context, applicationInterface);
    }

    /**
     * Creates file with capture information -- sensor, frame timestamps, etc
     */
    public OutputStream createOutputCaptureInfo(int mediaType, String extension, String suffix, Date currentDate) throws IOException {
        VideoMethod videoMethod = createOutputVideoMethod();
        if (videoMethod == VideoMethod.MEDIASTORE) {
            Uri saveUri = createOutputCaptureInfoFileMediaStore(
                    mediaType, extension, suffix, currentDate
            );
            return getContext().getContentResolver().openOutputStream(saveUri);
        } else if (isUsingSAF()) {
            Uri saveUri = createOutputCaptureInfoFileSAF(
                    mediaType, suffix, extension, currentDate
            );
            File saveFile = getFileFromDocumentUriSAF(saveUri, false);
            broadcastFile(saveFile, false, false, true);
            return new FileOutputStream(saveFile);
        } else {
            File saveFile = createOutputCaptureInfoFile(
                    mediaType, suffix, extension, currentDate
            );
            if (MyDebug.LOG) {
                Log.d(TAG, "save to: " + saveFile.getAbsolutePath());
            }
            broadcastFile(saveFile, false, false, false);
            return new FileOutputStream(saveFile);
        }
    }

    /**
     * Creates ouput capture info file if MediaStore is used
     */
    public Uri createOutputCaptureInfoFileMediaStore(int mediaType, String extension, String suffix, Date currentDate) throws IOException {
        Uri folder = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        ContentValues contentValues = new ContentValues();
        String filename = createMediaFilename(mediaType, suffix, 0, "." + extension, currentDate);
        if( MyDebug.LOG )
            Log.d(TAG, "filename: " + filename);
        contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
        String mime_type = "text/csv";
        if( MyDebug.LOG )
            Log.d(TAG, "mime_type: " + mime_type);
        contentValues.put(MediaStore.Video.Media.MIME_TYPE, mime_type);

        Uri fileUri = getContext().getContentResolver().insert(folder, contentValues);
        if( MyDebug.LOG )
            Log.d(TAG, "uri: " + fileUri);
        if(fileUri == null) {
            throw new IOException();
        }

        return fileUri;
    }

    /**
     * Creates output capture info file if is using SAF
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public Uri createOutputCaptureInfoFileSAF(int type, String suffix, String extension, Date currentDate) throws IOException {
        // TODO: why only csv?
        String mimeType = "text/csv";
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(currentDate);
        // note that DocumentsContract.createDocument will automatically append to the filename if it already exists
        String filename = createMediaFilename(
                MEDIA_TYPE_RAW_SENSOR_INFO, suffix, 0, "." + extension, currentDate
        );
        return createOutputFileSAF(
                createDirIfNeededSAF(timeStamp),
                filename,
                mimeType
        );
    }

    /**
     * Creates output capture info file if not using SAF
     */
    public File createOutputCaptureInfoFile(int type, String suffix, String extension, Date currentDate)  throws IOException {
        return createOutputMediaFile(
                getRawSensorInfoFolder(currentDate),
                type,
                suffix,
                extension,
                currentDate
        );
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private DocumentFile createDirIfNeededSAF(String childName) {
        Uri treeUri = getTreeUriSAF();
        DocumentFile parentDir = DocumentFile.fromTreeUri(super.getContext(), treeUri);

        DocumentFile infoDir = parentDir.findFile(childName);
        if (infoDir == null) {
            infoDir = parentDir.createDirectory(childName);
        }

        return infoDir;
    }

    private File getImageFolderChild(String childName) {
        File imageFolder = getImageFolder();
        if (childName.length() > 0 && childName.lastIndexOf('/') == childName.length() - 1) {
            // ignore final '/' character
            childName = childName.substring(0, childName.length()-1);
        }
        File file = new File(imageFolder, childName);
        return file;
    }

    private File getRawSensorInfoFolder(Date currentVideoDate) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(currentVideoDate);
        return getImageFolderChild(timeStamp);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Uri createOutputFileSAF(DocumentFile infoDir, String filename, String mimeType) throws IOException {
        Uri fileUri = infoDir.createFile(mimeType, filename).getUri();
        if( MyDebug.LOG )
            Log.d(TAG, "fileUri: " + fileUri);
        if( fileUri == null )
            throw new IOException();
        return fileUri;
    }
}
