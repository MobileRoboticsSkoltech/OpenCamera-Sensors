package net.sourceforge.opencamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
//import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
//import android.location.Location;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video.VideoColumns;
import android.provider.OpenableColumns;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import android.system.Os;
import android.system.StructStatVfs;
import android.util.Log;

/** Provides access to the filesystem. Supports both standard and Storage
 *  Access Framework.
 */
public class StorageUtils {
    private static final String TAG = "StorageUtils";

    static final int MEDIA_TYPE_IMAGE = 1;
    static final int MEDIA_TYPE_VIDEO = 2;
    static final int MEDIA_TYPE_PREFS = 3;
    static final int MEDIA_TYPE_GYRO_INFO = 4;

    private final Context context;
    private final MyApplicationInterface applicationInterface;
    private Uri last_media_scanned;

    private final static String RELATIVE_FOLDER_BASE = Environment.DIRECTORY_DCIM;

    // for testing:
    public volatile boolean failed_to_scan;

    StorageUtils(Context context, MyApplicationInterface applicationInterface) {
        this.context = context;
        this.applicationInterface = applicationInterface;
    }

    Uri getLastMediaScanned() {
        return last_media_scanned;
    }

    void clearLastMediaScanned() {
        last_media_scanned = null;
    }

    void setLastMediaScanned(Uri uri) {
        last_media_scanned = uri;
        if( MyDebug.LOG )
            Log.d(TAG, "set last_media_scanned to " + last_media_scanned);
    }

    /** Sends the intents to announce the new file to other Android applications. E.g., cloud storage applications like
     *  OwnCloud use this to listen for new photos/videos to automatically upload.
     *  Note that on Android 7 onwards, these broadcasts are deprecated and won't have any effect - see:
     *  https://developer.android.com/reference/android/hardware/Camera.html#ACTION_NEW_PICTURE
     *  Listeners like OwnCloud should instead be using
     *  https://developer.android.com/reference/android/app/job/JobInfo.Builder.html#addTriggerContentUri(android.app.job.JobInfo.TriggerContentUri)
     *  See https://github.com/owncloud/android/issues/1675 for OwnCloud's discussion on this.
     */
    void announceUri(Uri uri, boolean is_new_picture, boolean is_new_video) {
        if( MyDebug.LOG )
            Log.d(TAG, "announceUri: " + uri);
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
            if( MyDebug.LOG )
                Log.d(TAG, "broadcasts deprecated on Android 7 onwards, so don't send them");
            // see note above; the intents won't be delivered, so might as well save the trouble of trying to send them
        }
        else if( is_new_picture ) {
            // note, we reference the string directly rather than via Camera.ACTION_NEW_PICTURE, as the latter class is now deprecated - but we still need to broadcast the string for other apps
            context.sendBroadcast(new Intent( "android.hardware.action.NEW_PICTURE" , uri));
            // for compatibility with some apps - apparently this is what used to be broadcast on Android?
            context.sendBroadcast(new Intent("com.android.camera.NEW_PICTURE", uri));

            if( MyDebug.LOG ) // this code only used for debugging/logging
            {
                @SuppressLint("InlinedApi") // complains this constant only available on API 29 (even though it was available on older versions, but looks like it was moved?)
                String[] CONTENT_PROJECTION = { Images.Media.DATA, Images.Media.DISPLAY_NAME, Images.Media.MIME_TYPE, Images.Media.SIZE, Images.Media.DATE_TAKEN, Images.Media.DATE_ADDED };
                Cursor c = context.getContentResolver().query(uri, CONTENT_PROJECTION, null, null, null);
                if( c == null ) {
                    if( MyDebug.LOG )
                        Log.e(TAG, "Couldn't resolve given uri [1]: " + uri);
                }
                else if( !c.moveToFirst() ) {
                    if( MyDebug.LOG )
                        Log.e(TAG, "Couldn't resolve given uri [2]: " + uri);
                }
                else {
                    String file_path = c.getString(c.getColumnIndex(Images.Media.DATA));
                    String file_name = c.getString(c.getColumnIndex(Images.Media.DISPLAY_NAME));
                    String mime_type = c.getString(c.getColumnIndex(Images.Media.MIME_TYPE));
                    @SuppressLint("InlinedApi") // complains this constant only available on API 29 (even though it was available on older versions, but looks like it was moved?)
                    long date_taken = c.getLong(c.getColumnIndex(Images.Media.DATE_TAKEN));
                    long date_added = c.getLong(c.getColumnIndex(Images.Media.DATE_ADDED));
                    Log.d(TAG, "file_path: " + file_path);
                    Log.d(TAG, "file_name: " + file_name);
                    Log.d(TAG, "mime_type: " + mime_type);
                    Log.d(TAG, "date_taken: " + date_taken);
                    Log.d(TAG, "date_added: " + date_added);
                    c.close();
                }
            }
 			/*{
 				// hack: problem on Camera2 API (at least on Nexus 6) that if geotagging is enabled, then the resultant image has incorrect Exif TAG_GPS_DATESTAMP (GPSDateStamp) set (tends to be around 2038 - possibly a driver bug of casting long to int?)
 				// whilst we don't yet correct for that bug, the more immediate problem is that it also messes up the DATE_TAKEN field in the media store, which messes up Gallery apps
 				// so for now, we correct it based on the DATE_ADDED value.
    	        String[] CONTENT_PROJECTION = { Images.Media.DATE_ADDED }; 
    	        Cursor c = context.getContentResolver().query(uri, CONTENT_PROJECTION, null, null, null); 
    	        if( c == null ) { 
		 			if( MyDebug.LOG )
		 				Log.e(TAG, "Couldn't resolve given uri [1]: " + uri); 
    	        }
    	        else if( !c.moveToFirst() ) { 
		 			if( MyDebug.LOG )
		 				Log.e(TAG, "Couldn't resolve given uri [2]: " + uri); 
    	        }
    	        else {
        	        long date_added = c.getLong(c.getColumnIndex(Images.Media.DATE_ADDED)); 
		 			if( MyDebug.LOG )
		 				Log.e(TAG, "replace date_taken with date_added: " + date_added); 
					ContentValues values = new ContentValues(); 
					values.put(Images.Media.DATE_TAKEN, date_added*1000); 
					context.getContentResolver().update(uri, values, null, null);
        	        c.close(); 
    	        }
 			}*/
        }
        else if( is_new_video ) {
            context.sendBroadcast(new Intent("android.hardware.action.NEW_VIDEO", uri));

    		/*String[] CONTENT_PROJECTION = { Video.Media.DURATION }; 
	        Cursor c = context.getContentResolver().query(uri, CONTENT_PROJECTION, null, null, null); 
	        if( c == null ) { 
	 			if( MyDebug.LOG )
	 				Log.e(TAG, "Couldn't resolve given uri [1]: " + uri); 
	        }
	        else if( !c.moveToFirst() ) { 
	 			if( MyDebug.LOG )
	 				Log.e(TAG, "Couldn't resolve given uri [2]: " + uri); 
	        }
	        else {
    	        long duration = c.getLong(c.getColumnIndex(Video.Media.DURATION)); 
	 			if( MyDebug.LOG )
	 				Log.e(TAG, "replace duration: " + duration); 
				ContentValues values = new ContentValues(); 
				values.put(Video.Media.DURATION, 1000); 
				context.getContentResolver().update(uri, values, null, null);
    	        c.close(); 
	        }*/
        }
    }
	
	/*public Uri broadcastFileRaw(File file, Date current_date, Location location) {
		if( MyDebug.LOG )
			Log.d(TAG, "broadcastFileRaw: " + file.getAbsolutePath());
        ContentValues values = new ContentValues(); 
        values.put(ImageColumns.TITLE, file.getName().substring(0, file.getName().lastIndexOf(".")));
        values.put(ImageColumns.DISPLAY_NAME, file.getName());
        values.put(ImageColumns.DATE_TAKEN, current_date.getTime()); 
        values.put(ImageColumns.MIME_TYPE, "image/dng");
        //values.put(ImageColumns.MIME_TYPE, "image/jpeg");
        if( location != null ) {
            values.put(ImageColumns.LATITUDE, location.getLatitude());
            values.put(ImageColumns.LONGITUDE, location.getLongitude());
        }
        // leave ORIENTATION for now - this doesn't seem to get inserted for JPEGs anyway (via MediaScannerConnection.scanFile())
        values.put(ImageColumns.DATA, file.getAbsolutePath());
        //values.put(ImageColumns.DATA, "/storage/emulated/0/DCIM/OpenCamera/blah.dng");
        Uri uri = null;
        try {
    		uri = context.getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, values); 
 			if( MyDebug.LOG )
 				Log.d(TAG, "inserted media uri: " + uri);
    		context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
        }
        catch (Throwable th) { 
	        // This can happen when the external volume is already mounted, but 
	        // MediaScanner has not notify MediaProvider to add that volume. 
	        // The picture is still safe and MediaScanner will find it and 
	        // insert it into MediaProvider. The only problem is that the user 
	        // cannot click the thumbnail to review the picture. 
	        Log.e(TAG, "Failed to write MediaStore" + th); 
	    }
        return uri;
	}*/

    /** Sends a "broadcast" for the new file. This is necessary so that Android recognises the new file without needing a reboot:
     *  - So that they show up when connected to a PC using MTP.
     *  - For JPEGs, so that they show up in gallery applications.
     *  - This also calls announceUri() on the resultant Uri for the new file.
     *  - Note this should also be called after deleting a file.
     *  - Note that for DNG files, MediaScannerConnection.scanFile() doesn't result in the files being shown in gallery applications.
     *    This may well be intentional, since most gallery applications won't read DNG files anyway. But it's still important to
     *    call this function for DNGs, so that they show up on MTP.
     */
    public void broadcastFile(final File file, final boolean is_new_picture, final boolean is_new_video, final boolean set_last_scanned) {
        if( MyDebug.LOG )
            Log.d(TAG, "broadcastFile: " + file.getAbsolutePath());
        // note that the new method means that the new folder shows up as a file when connected to a PC via MTP (at least tested on Windows 8)
        if( file.isDirectory() ) {
            //this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(file)));
            // ACTION_MEDIA_MOUNTED no longer allowed on Android 4.4! Gives: SecurityException: Permission Denial: not allowed to send broadcast android.intent.action.MEDIA_MOUNTED
            // note that we don't actually need to broadcast anything, the folder and contents appear straight away (both in Gallery on device, and on a PC when connecting via MTP)
            // also note that we definitely don't want to broadcast ACTION_MEDIA_SCANNER_SCAN_FILE or use scanFile() for folders, as this means the folder shows up as a file on a PC via MTP (and isn't fixed by rebooting!)
        }
        else {
            // both of these work fine, but using MediaScannerConnection.scanFile() seems to be preferred over sending an intent
            //context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
            failed_to_scan = true; // set to true until scanned okay
            if( MyDebug.LOG )
                Log.d(TAG, "failed_to_scan set to true");
            MediaScannerConnection.scanFile(context, new String[] { file.getAbsolutePath() }, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            failed_to_scan = false;
                            if( MyDebug.LOG ) {
                                Log.d(TAG, "Scanned " + path + ":");
                                Log.d(TAG, "-> uri=" + uri);
                            }
                            if( set_last_scanned ) {
                                setLastMediaScanned(uri);
                            }
                            announceUri(uri, is_new_picture, is_new_video);
                            applicationInterface.scannedFile(file, uri);

                            // If called from video intent, if not using scoped-storage, we'll have saved using File API (even if user preference is SAF), see
                            // MyApplicationInterface.createOutputVideoMethod().
                            // It seems caller apps seem to prefer the content:// Uri rather than one based on a File
                            // update for Android 7: seems that passing file uris is now restricted anyway, see https://code.google.com/p/android/issues/detail?id=203555
                            // So we pass the uri back to the caller here.
                            Activity activity = (Activity)context;
                            String action = activity.getIntent().getAction();
                            if( !MainActivity.useScopedStorage() && MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
                                applicationInterface.finishVideoIntent(uri);
                            }
                        }
                    }
            );
        }
    }

    /** Wrapper for broadcastFile, when we only have a Uri (e.g., for SAF)
     */
    public void broadcastUri(final Uri uri, final boolean is_new_picture, final boolean is_new_video, final boolean set_last_scanned, final boolean image_capture_intent) {
        if( MyDebug.LOG )
            Log.d(TAG, "broadcastUri: " + uri);
        /* We still need to broadcastFile for SAF for two reasons:
            1. To call storageUtils.announceUri() to broadcast NEW_PICTURE etc.
               Whilst in theory we could do this directly, it seems external apps that use such broadcasts typically
               won't know what to do with a SAF based Uri (e.g, Owncloud crashes!) so better to broadcast the Uri
               corresponding to the real file, if it exists.
            2. Whilst the new file seems to be known by external apps such as Gallery without having to call media
               scanner, I've had reports this doesn't happen when saving to external SD cards. So better to explicitly
               scan.
        */
        File real_file = getFileFromDocumentUriSAF(uri, false);
        if( MyDebug.LOG )
            Log.d(TAG, "real_file: " + real_file);
        if( real_file != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "broadcast file");
            //Uri media_uri = broadcastFileRaw(real_file, current_date, location);
            //announceUri(media_uri, is_new_picture, is_new_video);
            broadcastFile(real_file, is_new_picture, is_new_video, set_last_scanned);
        }
        else if( !image_capture_intent ) {
            if( MyDebug.LOG )
                Log.d(TAG, "announce SAF uri");
            // shouldn't do this for an image capture intent - e.g., causes crash when calling from Google Keep
            announceUri(uri, is_new_picture, is_new_video);
        }
    }

    public boolean isUsingSAF() {
        // check Android version just to be safe
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            if( sharedPreferences.getBoolean(PreferenceKeys.UsingSAFPreferenceKey, false) ) {
                return true;
            }
        }
        return false;
    }

    // only valid if !isUsingSAF()
    String getSaveLocation() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getString(PreferenceKeys.SaveLocationPreferenceKey, "OpenCamera");
    }

    // only valid if isUsingSAF()
    String getSaveLocationSAF() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getString(PreferenceKeys.SaveLocationSAFPreferenceKey, "");
    }

    // only valid if isUsingSAF()
    public Uri getTreeUriSAF() {
        String folder_name = getSaveLocationSAF();
        return Uri.parse(folder_name);
    }

    File getSettingsFolder() {
        return new File(context.getExternalFilesDir(null), "backups");
    }

    /** Valid whether or not isUsingSAF().
     *  Returns the absolute path (in File format) of the image save folder.
     *  Only use this for needing e.g. human-readable strings for UI.
     *  This should not be used to create a File - instead, use getImageFolder().
     *  Note that if isUsingSAF(), this may return null - it can't be assumed that there is a
     *  File corresponding to the SAF Uri.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public String getImageFolderPath() {
        File file = getImageFolder();
        return file == null ? null : file.getAbsolutePath();
    }

    /** Valid whether or not isUsingSAF().
     *  But note that if isUsingSAF(), this may return null - it can't be assumed that there is a
     *  File corresponding to the SAF Uri.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    File getImageFolder() {
        File file;
        if( isUsingSAF() ) {
            Uri uri = getTreeUriSAF();
    		/*if( MyDebug.LOG )
    			Log.d(TAG, "uri: " + uri);*/
            file = getFileFromDocumentUriSAF(uri, true);
        }
        else {
            String folder_name = getSaveLocation();
            file = getImageFolder(folder_name);
        }
        return file;
    }

    // only valid if !isUsingSAF()
    // returns a form for use with RELATIVE_PATH (scoped storage)
    String getSaveRelativeFolder() {
        String folder_name = getSaveLocation();
        return getSaveRelativeFolder(folder_name);
    }

    // only valid if !isUsingSAF()
    // returns a form for use with RELATIVE_PATH (scoped storage)
    private static String getSaveRelativeFolder(String folder_name) {
        if( folder_name.length() > 0 && folder_name.lastIndexOf('/') == folder_name.length()-1 ) {
            // ignore final '/' character
            folder_name = folder_name.substring(0, folder_name.length()-1);
        }
        return RELATIVE_FOLDER_BASE + File.separator + folder_name;
    }

    public static File getBaseFolder() {
        final File base_folder = Environment.getExternalStoragePublicDirectory(RELATIVE_FOLDER_BASE);
        return base_folder;
    }

    /** Whether the save photo/video location is in a form that represents a full path, or a
     *  sub-folder in DCIM/.
     */
    static boolean saveFolderIsFull(String folder_name) {
        return folder_name.startsWith("/");
    }

    // only valid if !isUsingSAF()
    private static File getImageFolder(String folder_name) {
        File file;
        if( folder_name.length() > 0 && folder_name.lastIndexOf('/') == folder_name.length()-1 ) {
            // ignore final '/' character
            folder_name = folder_name.substring(0, folder_name.length()-1);
        }
        if( saveFolderIsFull(folder_name) ) {
            file = new File(folder_name);
        }
        else {
            file = new File(getBaseFolder(), folder_name);
        }
        return file;
    }

    /** Only valid if isUsingSAF()
     *  Returns the absolute path (in File format) of the SAF folder.
     *  Only use this for needing e.g. human-readable strings for UI.
     *  This should not be used to create a File - instead, use getFileFromDocumentUriSAF().
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public String getFilePathFromDocumentUriSAF(Uri uri, boolean is_folder) {
        File file = getFileFromDocumentUriSAF(uri, is_folder);
        return file == null ? null : file.getAbsolutePath();
    }

    /** Only valid if isUsingSAF()
     *  This function should only be used as a last resort - we shouldn't generally assume that a Uri represents an actual File, or that
     *  the File can be obtained anyway.
     *  However this is needed for a workaround to the fact that deleting a document file doesn't remove it from MediaStore.
     *  See:
            http://stackoverflow.com/questions/21605493/storage-access-framework-does-not-update-mediascanner-mtp
            http://stackoverflow.com/questions/20067508/get-real-path-from-uri-android-kitkat-new-storage-access-framework/
        Note that when using Android Q's scoped storage, the returned File will be inaccessible. However we still sometimes call this,
        e.g., to scan with mediascanner or get a human readable string for the path.
        Also note that this will return null for media store Uris with Android Q's scoped storage: https://developer.android.com/preview/privacy/scoped-storage
        "The DATA column is redacted for each file in the media store."
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public File getFileFromDocumentUriSAF(Uri uri, boolean is_folder) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "getFileFromDocumentUriSAF: " + uri);
            Log.d(TAG, "is_folder?: " + is_folder);
        }
        String authority = uri.getAuthority();
        if( MyDebug.LOG ) {
            Log.d(TAG, "authority: " + authority);
            Log.d(TAG, "scheme: " + uri.getScheme());
            Log.d(TAG, "fragment: " + uri.getFragment());
            Log.d(TAG, "path: " + uri.getPath());
            Log.d(TAG, "last path segment: " + uri.getLastPathSegment());
        }
        File file = null;
        if( "com.android.externalstorage.documents".equals(authority) ) {
            final String id = is_folder ? DocumentsContract.getTreeDocumentId(uri) : DocumentsContract.getDocumentId(uri);
            if( MyDebug.LOG )
                Log.d(TAG, "id: " + id);
            String [] split = id.split(":");
            if( split.length >= 1 ) {
                String type = split[0];
                String path = split.length >= 2 ? split[1] : "";
				/*if( MyDebug.LOG ) {
					Log.d(TAG, "type: " + type);
					Log.d(TAG, "path: " + path);
				}*/
                File [] storagePoints = new File("/storage").listFiles();

                if( "primary".equalsIgnoreCase(type) ) {
                    final File externalStorage = Environment.getExternalStorageDirectory();
                    file = new File(externalStorage, path);
                }
                for(int i=0;storagePoints != null && i<storagePoints.length && file==null;i++) {
                    File externalFile = new File(storagePoints[i], path);
                    if( externalFile.exists() ) {
                        file = externalFile;
                    }
                }
                if( file == null ) {
                    // just in case?
                    file = new File(path);
                }
            }
        }
        else if( "com.android.providers.downloads.documents".equals(authority) ) {
            if( !is_folder ) {
                final String id = DocumentsContract.getDocumentId(uri);
                if( id.startsWith("raw:") ) {
                    // unclear if this is needed for Open Camera, but on Vibrance HDR
                    // on some devices (at least on a Chromebook), I've had reports of id being of the form
                    // "raw:/storage/emulated/0/Download/..."
                    String filename = id.replaceFirst("raw:", "");
                    file = new File(filename);
                }
                else {
                    try {
                        final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));

                        String filename = getDataColumn(contentUri, null, null);
                        if( filename != null )
                            file = new File(filename);
                    }
                    catch(NumberFormatException e) {
                        // have had crashes from Google Play from Long.parseLong(id)
                        Log.e(TAG,"failed to parse id: " + id);
                        e.printStackTrace();
                    }
                }
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "downloads uri not supported for folders");
                // This codepath can be reproduced by enabling SAF and selecting Downloads.
                // DocumentsContract.getDocumentId() throws IllegalArgumentException for
                // this (content://com.android.providers.downloads.documents/tree/downloads).
                // If we use DocumentsContract.getTreeDocumentId() for folders, it returns
                // "downloads" - not clear how to parse this!
            }
        }
        else if( "com.android.providers.media.documents".equals(authority) ) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];

            Uri contentUri = null;
            switch (type) {
                case "image":
                    contentUri = Images.Media.EXTERNAL_CONTENT_URI;
                    break;
                case "video":
                    contentUri = Video.Media.EXTERNAL_CONTENT_URI;
                    break;
                case "audio":
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    break;
            }

            final String selection = "_id=?";
            final String[] selectionArgs = new String[] {
                    split[1]
            };

            String filename = getDataColumn(contentUri, selection, selectionArgs);
            if( filename != null )
                file = new File(filename);
        }

        if( MyDebug.LOG ) {
            if( file != null )
                Log.d(TAG, "file: " + file.getAbsolutePath());
            else
                Log.d(TAG, "failed to find file");
        }
        return file;
    }

    private String getDataColumn(Uri uri, String selection, String [] selectionArgs) {
        final String column = MediaStore.Images.ImageColumns.DATA;
        final String[] projection = {
                column
        };

        Cursor cursor = null;
        try {
            cursor = this.context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        }
        catch(IllegalArgumentException e) {
            e.printStackTrace();
        }
        catch(SecurityException e) {
            // have received crashes from Google Play for this
            e.printStackTrace();
        }
        finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /** Returns the filename (but not full path) for a Uri.
     * See https://developer.android.com/guide/topics/providers/document-provider.html and
     * http://stackoverflow.com/questions/5568874/how-to-extract-the-file-name-from-uri-returned-from-intent-action-get-content .
     */
    public String getFileName(Uri uri) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "getFileName: " + uri);
            Log.d(TAG, "uri has path: " + uri.getPath());
        }
        String result = null;
        if( uri.getScheme() != null && uri.getScheme().equals("content") ) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if( cursor != null && cursor.moveToFirst() ) {
                    final int column_index = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
                    result = cursor.getString(column_index);
                    if( MyDebug.LOG )
                        Log.d(TAG, "found name from database: " + result);
                }
            }
            catch(Exception e) {
                if( MyDebug.LOG )
                    Log.e(TAG, "Exception trying to find filename");
                e.printStackTrace();
            }
            finally {
                if (cursor != null)
                    cursor.close();
            }
        }
        if( result == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "resort to checking the uri's path");
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if( cut != -1 ) {
                result = result.substring(cut + 1);
                if( MyDebug.LOG )
                    Log.d(TAG, "found name from path: " + result);
            }
        }
        return result;
    }

    String createMediaFilename(int type, String suffix, int count, String extension, Date current_date) {
        String index = "";
        if( count > 0 ) {
            index = "_" + count; // try to find a unique filename
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean useZuluTime = sharedPreferences.getString(PreferenceKeys.SaveZuluTimePreferenceKey, "local").equals("zulu");
        String timeStamp;
        if( useZuluTime ) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmmss'Z'", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            timeStamp = fmt.format(current_date);
        }
        else {
            timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(current_date);
        }
        String mediaFilename;
        switch (type) {
            case MEDIA_TYPE_GYRO_INFO: // gyro info files have same name as the photo (but different extension)
            case MEDIA_TYPE_IMAGE: {
                String prefix = sharedPreferences.getString(PreferenceKeys.SavePhotoPrefixPreferenceKey, "IMG_");
                mediaFilename = prefix + timeStamp + suffix + index + extension;
                break;
            }
            case MEDIA_TYPE_VIDEO: {
                String prefix = sharedPreferences.getString(PreferenceKeys.SaveVideoPrefixPreferenceKey, "VID_");
                mediaFilename = prefix + timeStamp + suffix + index + extension;
                break;
            }
            case MEDIA_TYPE_PREFS: {
                // good to use a prefix that sorts before IMG_ and VID_: annoyingly when using SAF, it doesn't seem possible to
                // only show the xml files, and it always defaults to sorting alphabetically...
                String prefix = "BACKUP_OC_";
                mediaFilename = prefix + timeStamp + suffix + index + extension;
                break;
            }
            default:
                // throw exception as this is a programming error
                if (MyDebug.LOG)
                    Log.e(TAG, "unknown type: " + type);
                throw new RuntimeException();
        }
        return mediaFilename;
    }

    // only valid if !isUsingSAF()
    File createOutputMediaFile(int type, String suffix, String extension, Date current_date) throws IOException {
        File mediaStorageDir = getImageFolder();
        return createOutputMediaFile(mediaStorageDir, type, suffix, extension, current_date);
    }

    /** Create the folder if it does not exist.
     */
    void createFolderIfRequired(File folder) throws IOException {
        if( !folder.exists() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "create directory: " + folder);
            if( !folder.mkdirs() ) {
                Log.e(TAG, "failed to create directory");
                throw new IOException();
            }
            broadcastFile(folder, false, false, false);
        }
    }

    // only valid if !isUsingSAF()
    @SuppressLint("SimpleDateFormat")
    File createOutputMediaFile(File mediaStorageDir, int type, String suffix, String extension, Date current_date) throws IOException {
        createFolderIfRequired(mediaStorageDir);

        // Create a media file name
        File mediaFile = null;
        for(int count=0;count<100;count++) {
        	/*final boolean use_burst_folder = true;
        	if( use_burst_folder ) {
				String burstFolderName = createMediaFilename(type, "", count, "", current_date);
				File burstFolder = new File(mediaStorageDir.getPath() + File.separator + burstFolderName);
				if( !burstFolder.exists() ) {
					if( !burstFolder.mkdirs() ) {
						if( MyDebug.LOG )
							Log.e(TAG, "failed to create burst sub-directory");
						throw new IOException();
					}
					broadcastFile(burstFolder, false, false, false);
				}

				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
				String prefix = sharedPreferences.getString(PreferenceKeys.getSavePhotoPrefixPreferenceKey(), "IMG_");
				//String mediaFilename = prefix + suffix + "." + extension;
				String suffix_alt = suffix.substring(1);
				String mediaFilename = suffix_alt + prefix + suffix_alt + "BURST" + "." + extension;
				mediaFile = new File(burstFolder.getPath() + File.separator + mediaFilename);
			}
			else*/ {
                String mediaFilename = createMediaFilename(type, suffix, count, "." + extension, current_date);
                mediaFile = new File(mediaStorageDir.getPath() + File.separator + mediaFilename);
            }
            if( !mediaFile.exists() ) {
                break;
            }
        }

        if( MyDebug.LOG ) {
            Log.d(TAG, "getOutputMediaFile returns: " + mediaFile);
        }
        if( mediaFile == null )
            throw new IOException();
        return mediaFile;
    }

    // only valid if isUsingSAF()
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    Uri createOutputFileSAF(String filename, String mimeType) throws IOException {
        try {
            Uri treeUri = getTreeUriSAF();
            if( MyDebug.LOG )
                Log.d(TAG, "treeUri: " + treeUri);
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
            if( MyDebug.LOG )
                Log.d(TAG, "docUri: " + docUri);
            // note that DocumentsContract.createDocument will automatically append to the filename if it already exists
            Uri fileUri = DocumentsContract.createDocument(context.getContentResolver(), docUri, mimeType, filename);
            if( MyDebug.LOG )
                Log.d(TAG, "returned fileUri: " + fileUri);
			/*if( true )
				throw new SecurityException(); // test*/
            if( fileUri == null )
                throw new IOException();
            return fileUri;
        }
        catch(IllegalArgumentException e) {
            // DocumentsContract.getTreeDocumentId throws this if URI is invalid
            if( MyDebug.LOG )
                Log.e(TAG, "createOutputMediaFileSAF failed with IllegalArgumentException");
            e.printStackTrace();
            throw new IOException();
        }
        catch(IllegalStateException e) {
            // Have reports of this from Google Play for DocumentsContract.createDocument - better to fail gracefully and tell user rather than crash!
            if( MyDebug.LOG )
                Log.e(TAG, "createOutputMediaFileSAF failed with IllegalStateException");
            e.printStackTrace();
            throw new IOException();
        }
        catch(SecurityException e) {
            // Have reports of this from Google Play - better to fail gracefully and tell user rather than crash!
            if( MyDebug.LOG )
                Log.e(TAG, "createOutputMediaFileSAF failed with SecurityException");
            e.printStackTrace();
            throw new IOException();
        }
    }

    /** Return the mime type corresponding to the supplied extension. Supports images only, not video.
     */
    String getImageMimeType(String extension) {
        String mimeType;
        switch (extension) {
            case "dng":
                mimeType = "image/dng";
                //mimeType = "image/x-adobe-dng";
                break;
            case "webp":
                mimeType = "image/webp";
                break;
            case "png":
                mimeType = "image/png";
                break;
            default:
                mimeType = "image/jpeg";
                break;
        }
        return mimeType;
    }

    /** Return the mime type corresponding to the supplied extension. Supports video only, not images.
     */
    String getVideoMimeType(String extension) {
        String mimeType;
        switch( extension ) {
            case "3gp":
                mimeType = "video/3gpp";
                break;
            case "webm":
                mimeType = "video/webm";
                break;
            default:
                mimeType = "video/mp4";
                break;
        }
        return mimeType;
    }

    // only valid if isUsingSAF()
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    Uri createOutputMediaFileSAF(int type, String suffix, String extension, Date current_date) throws IOException {
        String mimeType;
        switch (type) {
            case MEDIA_TYPE_IMAGE:
                mimeType = getImageMimeType(extension);
                break;
            case MEDIA_TYPE_VIDEO:
                mimeType = getVideoMimeType(extension);
                break;
            case MEDIA_TYPE_PREFS:
            case MEDIA_TYPE_GYRO_INFO:
                mimeType = "text/xml";
                break;
            default:
                // throw exception as this is a programming error
                if (MyDebug.LOG)
                    Log.e(TAG, "unknown type: " + type);
                throw new RuntimeException();
        }
        // note that DocumentsContract.createDocument will automatically append to the filename if it already exists
        String mediaFilename = createMediaFilename(type, suffix, 0, "." + extension, current_date);
        return createOutputFileSAF(mediaFilename, mimeType);
    }

    static class Media {
        final boolean mediastore; // whether uri is from mediastore
        final long id; // for mediastore==true only
        final boolean video;
        final Uri uri;
        final long date;
        final int orientation; // for mediastore==true, video==false only
        final String filename; // this should correspond to DISPLAY_NAME (so available with scoped storage) - so this includes file extension, but not full path

        Media(boolean mediastore, long id, boolean video, Uri uri, long date, int orientation, String filename) {
            this.mediastore = mediastore;
            this.id = id;
            this.video = video;
            this.uri = uri;
            this.date = date;
            this.orientation = orientation;
            this.filename = filename;
        }

        /** Returns a mediastore uri. If this Media object was not created by a mediastore uri, then
         *  this will try to convert using MediaStore.getMediaUri(), but if this fails the function
         *  will return null.
         */
        Uri getMediaStoreUri(Context context) {
            if( this.mediastore )
                return this.uri;
            else {
                try {
                    // should only have allowed mediastore==null when using scoped storage
                    if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ) {
                        return MediaStore.getMediaUri(context, this.uri);
                    }
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        }
    }

    private static boolean filenameIsRaw(String filename) {
        return filename.toLowerCase(Locale.US).endsWith(".dng");
    }

    private static String filenameWithoutExtension(String filename) {
        String filename_without_ext = filename.toLowerCase(Locale.US);
        if( filename_without_ext.indexOf(".") > 0 )
            filename_without_ext = filename_without_ext.substring(0, filename_without_ext.lastIndexOf("."));
        return filename_without_ext;
    }

    private enum UriType {
        MEDIASTORE_IMAGES,
        MEDIASTORE_VIDEOS
    }

    @SuppressLint("InlinedApi") // complains MediaColumns constants only available on API 29 (even though it was available on older versions, but looks like it was moved?); for some reason doesn't allow putting this at the actual comments?!
    private Media getLatestMediaCore(Uri baseUri, String bucket_id, UriType uri_type) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "getLatestMediaCore");
            Log.d(TAG, "baseUri: " + baseUri);
            Log.d(TAG, "bucket_id: " + bucket_id);
            Log.d(TAG, "uri_type: " + uri_type);
        }
        Media media = null;

        final int column_id_c = 0;
        final int column_date_taken_c = 1;
        /*final int column_data_c = 2; // full path and filename, including extension
        final int column_name_c = 3; // filename (without path), including extension
        final int column_orientation_c = 4; // for images only*/
        final int column_name_c = 2; // filename (without path), including extension
        final int column_orientation_c = 3; // for mediastore images only
        String [] projection;
        switch( uri_type ) {
            case MEDIASTORE_IMAGES:
                projection = new String[] {ImageColumns._ID, ImageColumns.DATE_TAKEN, ImageColumns.DISPLAY_NAME, ImageColumns.ORIENTATION};
                break;
            case MEDIASTORE_VIDEOS:
                projection = new String[] {VideoColumns._ID, VideoColumns.DATE_TAKEN, VideoColumns.DISPLAY_NAME};
                break;
            default:
                throw new RuntimeException("unknown uri_type: " + uri_type);
        }
        // for images, we need to search for JPEG/etc and RAW, to support RAW only mode (even if we're not currently in that mode, it may be that previously the user did take photos in RAW only mode)
        // if updating this code for supported mime types, remember to also update getLatestMediaSAF()
        /*String selection = video ? "" : ImageColumns.MIME_TYPE + "='image/jpeg' OR " +
                ImageColumns.MIME_TYPE + "='image/webp' OR " +
                ImageColumns.MIME_TYPE + "='image/png' OR " +
                ImageColumns.MIME_TYPE + "='image/x-adobe-dng'";*/
        String selection = "";
        switch( uri_type ) {
            case MEDIASTORE_IMAGES:
            {
                if( bucket_id != null )
                    selection = ImageColumns.BUCKET_ID + " = " + bucket_id;
                boolean and = selection.length() > 0;
                if( and )
                    selection += " AND ( ";
                selection += ImageColumns.MIME_TYPE + "='image/jpeg' OR " +
                        ImageColumns.MIME_TYPE + "='image/webp' OR " +
                        ImageColumns.MIME_TYPE + "='image/png' OR " +
                        ImageColumns.MIME_TYPE + "='image/x-adobe-dng'";
                if( and )
                    selection += " )";
                break;
            }
            case MEDIASTORE_VIDEOS:
                if( bucket_id != null )
                    selection = VideoColumns.BUCKET_ID + " = " + bucket_id;
                break;
            default:
                throw new RuntimeException("unknown uri_type: " + uri_type);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "selection: " + selection);
        String order;
        switch( uri_type ) {
            case MEDIASTORE_IMAGES:
                order = ImageColumns.DATE_TAKEN + " DESC," + ImageColumns._ID + " DESC";
                break;
            case MEDIASTORE_VIDEOS:
                //noinspection DuplicateBranchesInSwitch
                order = VideoColumns.DATE_TAKEN + " DESC," + VideoColumns._ID + " DESC";
                break;
            default:
                throw new RuntimeException("unknown uri_type: " + uri_type);
        }
        Cursor cursor = null;

        // we know we only want the most recent image - however we may need to scan forward if we find a RAW, to see if there's
        // an equivalent non-RAW image
        // request 3, just in case
        Uri queryUri = baseUri.buildUpon().appendQueryParameter("limit", "3").build();
        if( MyDebug.LOG )
            Log.d(TAG, "queryUri: " + queryUri);

        try {
            cursor = context.getContentResolver().query(queryUri, projection, selection, null, order);
            if( cursor != null && cursor.moveToFirst() ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "found: " + cursor.getCount());

                // now sorted in order of date - so just pick the most recent one

                /*
                // now sorted in order of date - scan to most recent one in the Open Camera save folder
                boolean found = false;
                //File save_folder = getImageFolder(); // may be null if using SAF
                String save_folder_string = save_folder == null ? null : save_folder.getAbsolutePath() + File.separator;
                if( MyDebug.LOG )
                    Log.d(TAG, "save_folder_string: " + save_folder_string);
                do {
                    String path = cursor.getString(column_data_c);
                    if( MyDebug.LOG )
                        Log.d(TAG, "path: " + path);
                    // path may be null on Android 4.4!: http://stackoverflow.com/questions/3401579/get-filename-and-path-from-uri-from-mediastore
                    if( save_folder_string == null || (path != null && path.contains(save_folder_string) ) ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "found most recent in Open Camera folder");
                        // we filter files with dates in future, in case there exists an image in the folder with incorrect datestamp set to the future
                        // we allow up to 2 days in future, to avoid risk of issues to do with timezone etc
                        long date = cursor.getLong(column_date_taken_c);
                        long current_time = System.currentTimeMillis();
                        if( date > current_time + 172800000 ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "skip date in the future!");
                        }
                        else {
                            found = true;
                            break;
                        }
                    }
                }
                while( cursor.moveToNext() );

                if( !found ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "can't find suitable in Open Camera folder, so just go with most recent");
                    cursor.moveToFirst();
                }
                */

                {
                    // make sure we prefer JPEG/etc (non RAW) if there's a JPEG/etc version of this image
                    // this is because we want to support RAW only and JPEG+RAW modes
                    String filename = cursor.getString(column_name_c);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "filename: " + filename);
                    }
                    // in theory now that we use DISPLAY_NAME instead of DATA (for path), this should always be non-null, but check just in case
                    if( filename != null && filenameIsRaw(filename) ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "try to find a non-RAW version of the DNG");
                        int dng_pos = cursor.getPosition();
                        boolean found_non_raw = false;
                        String filename_without_ext = filenameWithoutExtension(filename);
                        if( MyDebug.LOG )
                            Log.d(TAG, "filename_without_ext: " + filename_without_ext);
                        while( cursor.moveToNext() ) {
                            String next_filename = cursor.getString(column_name_c);
                            if( MyDebug.LOG )
                                Log.d(TAG, "next_filename: " + next_filename);
                            if( next_filename == null ) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "done scanning, couldn't find filename");
                                break;
                            }
                            String next_filename_without_ext = filenameWithoutExtension(next_filename);
                            if( MyDebug.LOG )
                                Log.d(TAG, "next_filename_without_ext: " + next_filename_without_ext);
                            if( !filename_without_ext.equals(next_filename_without_ext) ) {
                                // no point scanning any further as sorted by date - and we don't want to read through the entire set!
                                if( MyDebug.LOG )
                                    Log.d(TAG, "done scanning");
                                break;
                            }
                            // so we've found another file with matching filename - is it a JPEG/etc?
                            // we've already restricted the query to the image types we're interested in, so
                            // only need to check that it isn't another DNG (which would be strange, as it
                            // would mean a duplicate filename, but check just in case!)
                            if( filenameIsRaw(next_filename) ) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "found another dng!");
                            }
                            else {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "found equivalent non-dng");
                                found_non_raw = true;
                                break;
                            }
                        }
                        if( !found_non_raw ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "can't find equivalent jpeg/etc");
                            cursor.moveToPosition(dng_pos);
                        }
                    }
                }

                long id = cursor.getLong(column_id_c);
                long date = cursor.getLong(column_date_taken_c);
                int orientation = (uri_type == UriType.MEDIASTORE_IMAGES) ? cursor.getInt(column_orientation_c) : 0;
                Uri uri = ContentUris.withAppendedId(baseUri, id);
                String filename = cursor.getString(column_name_c);
                if( MyDebug.LOG )
                    Log.d(TAG, "found most recent uri for " + uri_type + ": " + uri);

                boolean video;
                switch( uri_type ) {
                    case MEDIASTORE_IMAGES:
                        video = false;
                        break;
                    case MEDIASTORE_VIDEOS:
                        video = true;
                        break;
                    default:
                        throw new RuntimeException("unknown uri_type: " + uri_type);
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "video: " + video);

                media = new Media(true, id, video, uri, date, orientation, filename);

                if( MyDebug.LOG ) {
                    // debug
                    if( cursor.moveToFirst() ) {
                        do {
                            long this_id = cursor.getLong(column_id_c);
                            long this_date = cursor.getLong(column_date_taken_c);
                            Uri this_uri = ContentUris.withAppendedId(baseUri, this_id);
                            String this_filename = cursor.getString(column_name_c);
                            Log.d(TAG, "Date: " + this_date + " ID: " + this_id + " Name: " + this_filename + " Uri: " + this_uri);
                        }
                        while( cursor.moveToNext() );
                    }
                }
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "mediastore returned no media");
            }
        }
        catch(Exception e) {
            // have had exceptions such as SQLiteException, NullPointerException reported on Google Play from within getContentResolver().query() call
            if( MyDebug.LOG )
                Log.e(TAG, "Exception trying to find latest media");
            e.printStackTrace();
        }
        finally {
            if( cursor != null ) {
                cursor.close();
            }
        }

        if( MyDebug.LOG )
            Log.d(TAG, "return latest media: " + media);
        return media;
    }

    /** Used when using Storage Access Framework AND scoped storage.
     *  This is because with scoped storage, we don't request READ_EXTERNAL_STORAGE (as
     *  recommended). It's meant to be the case that applications should still be able to see files
     *  that they own - but whilst this is true when images are saved using mediastore API, this is
     *  NOT true when saving with Storage Access Framework - they don't show up in mediastore
     *  queries (even though they've definitely been added to the mediastore). So instead we read
     *  using the SAF uri, and if we need the media uri (e.g., to pass to Gallery application), use
     *  Media.getMediaStoreUri(). What a mess!
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private Media getLatestMediaSAF(Uri treeUri) {
        if (MyDebug.LOG)
            Log.d(TAG, "getLatestMedia: " + treeUri);

        Media media = null;

        Uri baseUri;
        try {
            String parentDocUri = DocumentsContract.getTreeDocumentId(treeUri);
            baseUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocUri);
        }
        catch(Exception e) {
            // DocumentsContract.getTreeDocumentId throws IllegalArgumentException if the uri is
            // invalid. Unclear if this can happen in practice - this happens in test
            // testSaveFolderHistorySAF() but only because we test a dummy invalid SAF uri. But
            // seems no harm catching it in case this can happen (e.g., especially if restoring
            // backed up preferences from a different device?) Better to just show nothing in the
            // thumbnail, rather than crashing!
            // N.B., we catch Exception is otherwise compiler complains IllegalArgumentException
            // isn't ever thrown - even though it is!?
            Log.e(TAG, "Exception using treeUri: " + treeUri);
            return media;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "baseUri: " + baseUri);

        final int column_id_c = 0;
        final int column_date_c = 1;
        final int column_name_c = 2; // filename (without path), including extension
        final int column_mime_c = 3;
        String [] projection = new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};

        // Note, it appears that when querying DocumentsContract, basic query functionality like selection, ordering, are ignored(!).
        // See: https://stackoverflow.com/questions/52770188/how-to-filter-the-results-of-a-query-with-buildchilddocumentsuriusingtree
        // https://stackoverflow.com/questions/56263620/contentresolver-query-on-documentcontract-lists-all-files-disregarding-selection
        // So, we have to do it ourselves.

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(baseUri, projection, null, null, null);
            if( cursor != null && cursor.moveToFirst() ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "found: " + cursor.getCount());

                Uri latest_uri = null;
                long latest_date = 0;
                String latest_filename = null;
                boolean latest_is_video = false;

                // as well as scanning for the most recent image, we also keep track of the most recent non-RAW image,
                // in case we want to prefer that when the most recent
                Uri nonraw_latest_uri = null;
                long nonraw_latest_date = 0;
                String nonraw_latest_filename = null;

                do {
                    long this_date = cursor.getLong(column_date_c);

                    String doc_id = cursor.getString(column_id_c);
                    Uri this_uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, doc_id);
                    String this_mime_type = cursor.getString(column_mime_c);

                    // if updating this code for allowed mime types, also update corresponding code in getLatestMediaCore()
                    boolean is_allowed;
                    boolean this_is_video;
                    switch( this_mime_type ) {
                        case "image/jpeg":
                        case "image/webp":
                        case "image/png":
                        case "image/x-adobe-dng":
                            is_allowed = true;
                            this_is_video = false;
                            break;
                        case "video/3gpp":
                        case "video/webm":
                        case "video/mp4":
                            // n.b., perhaps we should just allow video/*, but we should still disallow .SRT files!
                            is_allowed = true;
                            this_is_video = true;
                            break;
                        default:
                            // skip unwanted file format
                            is_allowed = false;
                            this_is_video = false;
                            break;
                    }
                    if( !is_allowed ) {
                        continue;
                    }

                    String this_filename = cursor.getString(column_name_c);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "Date: " + this_date + " doc_id: " + doc_id + " Name: " + this_filename + " Uri: " + this_uri);
                    }

                    if( latest_uri == null || this_date > latest_date ) {
                        latest_uri = this_uri;
                        latest_date = this_date;
                        latest_filename = this_filename;
                        latest_is_video = this_is_video;
                    }
                    if( !this_is_video && !filenameIsRaw(this_filename) ) {
                        if( nonraw_latest_uri == null || this_date > nonraw_latest_date ) {
                            nonraw_latest_uri = this_uri;
                            nonraw_latest_date = this_date;
                            nonraw_latest_filename = this_filename;
                        }
                    }
                }
                while( cursor.moveToNext() );

                if( latest_uri == null ) {
                    if( MyDebug.LOG )
                        Log.e(TAG, "couldn't find latest uri");
                }
                else {
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "latest_uri: " + latest_uri);
                        Log.d(TAG, "nonraw_latest_uri: " + nonraw_latest_uri);
                    }

                    if( !latest_is_video && filenameIsRaw(latest_filename) && nonraw_latest_uri != null ) {
                        // prefer non-RAW to RAW? check filenames without extensions match
                        String filename_without_ext = filenameWithoutExtension(latest_filename);
                        String next_filename_without_ext = filenameWithoutExtension(nonraw_latest_filename);
                        if( MyDebug.LOG ) {
                            Log.d(TAG, "filename_without_ext: " + filename_without_ext);
                            Log.d(TAG, "next_filename_without_ext: " + next_filename_without_ext);
                        }
                        if( filename_without_ext.equals(next_filename_without_ext) ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "prefer non-RAW to RAW");
                            latest_uri = nonraw_latest_uri;
                            latest_date = nonraw_latest_date;
                            latest_filename = nonraw_latest_filename;
                            // video is unchanged
                        }
                    }

                    media = new Media(false,0, latest_is_video, latest_uri, latest_date, 0, latest_filename);
                }

                /*if( MyDebug.LOG ) {
                    // debug
                    if( cursor.moveToFirst() ) {
                        do {
                            long this_id = cursor.getLong(column_id_c);
                            long this_date = cursor.getLong(column_date_taken_c);
                            Uri this_uri = ContentUris.withAppendedId(baseUri, this_id);
                            String this_filename = cursor.getString(column_name_c);
                            Log.d(TAG, "Date: " + this_date + " ID: " + this_id + " Name: " + this_filename + " Uri: " + this_uri);
                        }
                        while( cursor.moveToNext() );
                    }
                }*/
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "mediastore returned no media");
            }
        }
        catch(Exception e) {
            if( MyDebug.LOG )
                Log.e(TAG, "Exception trying to find latest media");
            e.printStackTrace();
        }
        finally {
            if( cursor != null ) {
                cursor.close();
            }
        }

        if( MyDebug.LOG )
            Log.d(TAG, "return latest media: " + media);
        return media;
    }

    private Media getLatestMedia(UriType uri_type) {
        if( MyDebug.LOG )
            Log.d(TAG, "getLatestMedia: " + uri_type);
        if( !MainActivity.useScopedStorage() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ) {
            // needed for Android 6, in case users deny storage permission, otherwise we get java.lang.SecurityException from ContentResolver.query()
            // see https://developer.android.com/training/permissions/requesting.html
            // we now request storage permission before opening the camera, but keep this here just in case
            // we restrict check to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
            // update for scoped storage: here we should no longer need READ_EXTERNAL_STORAGE (which we won't have), instead we'll only be able to see
            // media created by Open Camera, which is fine
            if( MyDebug.LOG )
                Log.e(TAG, "don't have READ_EXTERNAL_STORAGE permission");
            return null;
        }

        String save_folder = getImageFolderPath(); // may be null if using SAF
        if( MyDebug.LOG )
            Log.d(TAG, "save_folder: " + save_folder);
        String bucket_id = null;
        if( save_folder != null ) {
            bucket_id = String.valueOf(save_folder.toLowerCase().hashCode());
        }
        if( MyDebug.LOG )
            Log.d(TAG, "bucket_id: " + bucket_id);

        Uri baseUri;
        switch( uri_type ) {
            case MEDIASTORE_IMAGES:
                baseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                break;
            case MEDIASTORE_VIDEOS:
                baseUri = Video.Media.EXTERNAL_CONTENT_URI;
                break;
            default:
                throw new RuntimeException("unknown uri_type: " + uri_type);
        }

        if( MyDebug.LOG )
            Log.d(TAG, "baseUri: " + baseUri);
        Media media = getLatestMediaCore(baseUri, bucket_id, uri_type);
        if( media == null && bucket_id != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "fall back to checking any folder");
            media = getLatestMediaCore(baseUri, null, uri_type);
        }

        return media;
    }

    Media getLatestMedia() {
        if( MainActivity.useScopedStorage() && this.isUsingSAF() ) {
            Uri treeUri = this.getTreeUriSAF();
            return getLatestMediaSAF(treeUri);
        }

        Media image_media = getLatestMedia(UriType.MEDIASTORE_IMAGES);
        Media video_media = getLatestMedia(UriType.MEDIASTORE_VIDEOS);
        Media media = null;
        if( image_media != null && video_media == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "only found images");
            media = image_media;
        }
        else if( image_media == null && video_media != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "only found videos");
            media = video_media;
        }
        else if( image_media != null && video_media != null ) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "found images and videos");
                Log.d(TAG, "latest image date: " + image_media.date);
                Log.d(TAG, "latest video date: " + video_media.date);
            }
            if( image_media.date >= video_media.date ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "latest image is newer");
                media = image_media;
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "latest video is newer");
                media = video_media;
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "return latest media: " + media);
        return media;
    }

    // only valid if isUsingSAF()
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private long freeMemorySAF() {
        Uri treeUri = applicationInterface.getStorageUtils().getTreeUriSAF();
        ParcelFileDescriptor pfd = null;
        if( MyDebug.LOG )
            Log.d(TAG, "treeUri: " + treeUri);
        try {
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
            if( MyDebug.LOG )
                Log.d(TAG, "docUri: " + docUri);
            pfd = context.getContentResolver().openFileDescriptor(docUri, "r");
            if( pfd == null ) { // just in case
                Log.e(TAG, "pfd is null!");
                throw new FileNotFoundException();
            }
            if( MyDebug.LOG )
                Log.d(TAG, "read direct from SAF uri");
            StructStatVfs statFs = Os.fstatvfs(pfd.getFileDescriptor());
            long blocks = statFs.f_bavail;
            long size = statFs.f_bsize;
            return (blocks*size) / 1048576;
        }
        catch(IllegalArgumentException e) {
            // IllegalArgumentException can be thrown by DocumentsContract.getTreeDocumentId or getContentResolver().openFileDescriptor
            e.printStackTrace();
        }
        catch(FileNotFoundException e) {
            e.printStackTrace();
        }
        catch(Exception e) {
            // We actually just want to catch ErrnoException here, but that isn't available pre-Android 5, and trying to catch ErrnoException
            // means we crash on pre-Android 5 with java.lang.VerifyError when trying to create the StorageUtils class!
            // One solution might be to move this method to a separate class that's only created on Android 5+, but this is a quick fix for
            // now.
            e.printStackTrace();
        }
        finally {
            try {
                if( pfd != null )
                    pfd.close();
            }
            catch(IOException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    /** Return free memory in MB, or -1 if this was unable to be found.
     */
    public long freeMemory() { // return free memory in MB
        if( MyDebug.LOG )
            Log.d(TAG, "freeMemory");
        if( applicationInterface.getStorageUtils().isUsingSAF() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
            // if we fail for SAF, don't fall back to the methods below, as this may be incorrect (especially for external SD card)
            return freeMemorySAF();
        }
        // n.b., StatFs still seems to work with Android 10's scoped storage... (and there doesn't seem to be an official non-File based equivalent)
        try {
            File folder = getImageFolder();
            if( folder == null ) {
                throw new IllegalArgumentException(); // so that we fall onto the backup
            }
            StatFs statFs = new StatFs(folder.getAbsolutePath());
            long blocks, size;
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 ) {
                blocks = statFs.getAvailableBlocksLong();
                size = statFs.getBlockSizeLong();
            }
            else {
                // cast to long to avoid overflow!
                //noinspection deprecation
                blocks = statFs.getAvailableBlocks();
                //noinspection deprecation
                size = statFs.getBlockSize();
            }
            return (blocks*size) / 1048576;
        }
        catch(IllegalArgumentException e) {
            // this can happen if folder doesn't exist, or don't have read access
            // if the save folder is a subfolder of DCIM, we can just use that instead
            try {
                if( !isUsingSAF() ) {
                    // getSaveLocation() only valid if !isUsingSAF()
                    String folder_name = getSaveLocation();
                    if( !saveFolderIsFull(folder_name) ) {
                        File folder = getBaseFolder();
                        StatFs statFs = new StatFs(folder.getAbsolutePath());
                        long blocks, size;
                        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 ) {
                            blocks = statFs.getAvailableBlocksLong();
                            size = statFs.getBlockSizeLong();
                        }
                        else {
                            // cast to long to avoid overflow!
                            //noinspection deprecation
                            blocks = statFs.getAvailableBlocks();
                            //noinspection deprecation
                            size = statFs.getBlockSize();
                        }
                        return (blocks*size) / 1048576;
                    }
                }
            }
            catch(IllegalArgumentException e2) {
                // just in case
            }
        }
        return -1;
    }
}
