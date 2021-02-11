package net.sourceforge.opencamera;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import net.sourceforge.opencamera.preview.ApplicationInterface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

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

    public OutputStream createOutputCaptureInfo(int mediaType, String extension, String suffix, Date currentDate) throws IOException {
        ApplicationInterface.VideoMethod method = createOutputVideoMethod();
        if (method == ApplicationInterface.VideoMethod.FILE) {
            return new FileOutputStream(createOutputCaptureInfoFile(mediaType, suffix, extension, currentDate));
        } else {
            Uri outputCaptureInfoUri = createOutputCaptureInfoUri(
                    mediaType, extension, suffix, currentDate
            );
            return getContext().getContentResolver().openOutputStream(outputCaptureInfoUri);
        }
    }

    /**
     * Creates file with capture information -- sensor, frame timestamps, etc
     */
    public Uri createOutputCaptureInfoUri(int mediaType, String extension, String suffix, Date currentDate) throws IOException {
        VideoMethod videoMethod = createOutputVideoMethod();
        if (videoMethod == VideoMethod.MEDIASTORE) {
            return createOutputCaptureInfoFileMediaStore(
                    mediaType, extension, suffix, currentDate
            );
        } else if (videoMethod == VideoMethod.SAF) {
            return createOutputCaptureInfoFileSAF(
                    mediaType, suffix, extension, currentDate
            );
        } else {
            throw new IllegalStateException("Cannot call createCaptureInfoUri when legacy file access is used");
        }
    }

    /**
     * Creates ouput capture info file if MediaStore is used
     */
    public Uri createOutputCaptureInfoFileMediaStore(int mediaType, String extension, String suffix, Date currentDate) {
        ContentValues values = new ContentValues();
        String filename = createMediaFilename(mediaType, suffix, 0, "." + extension, currentDate);
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        if (mediaType == MEDIA_TYPE_IMAGE || mediaType == MEDIA_TYPE_VIDEO_FRAME) {
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/" + extension);
        } else if (mediaType == MEDIA_TYPE_RAW_SENSOR_INFO) {
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/" + extension);
        } else {
            throw new IllegalArgumentException("Provided content type was not supported");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOCUMENTS +
                            File.separator +
                            getSaveLocation() +
                            File.separator +
                            getRawSensorInfoFolderName(currentDate)
            );
        }

        Uri uri = getContext().getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
        return uri;
    }

    /**
     * Creates output capture info file if is using SAF
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Uri createOutputCaptureInfoFileSAF(int type, String suffix, String extension, Date currentDate) throws IOException {
        String mimeType;
        if (type == MEDIA_TYPE_IMAGE || type == MEDIA_TYPE_VIDEO_FRAME) {
            mimeType = "image/" + extension;
        } else if (type == MEDIA_TYPE_RAW_SENSOR_INFO) {
            mimeType = "text/" + extension;
        } else {
            throw new IllegalArgumentException("Provided content type was not supported");
        }
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
        String name = getRawSensorInfoFolderName(currentVideoDate);
        return getImageFolderChild(name);
    }

    private String getRawSensorInfoFolderName(Date currentVideoDate) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(currentVideoDate);
        return timeStamp;
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
