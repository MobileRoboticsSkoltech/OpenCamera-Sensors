package net.sourceforge.opencamera;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

/** Code for options for saving and restoring settings.
 */
public class SettingsManager {
    private static final String TAG = "SettingsManager";

    private final MainActivity main_activity;

    SettingsManager(MainActivity main_activity) {
        this.main_activity = main_activity;
    }

    private final static String doc_tag = "open_camera_prefs";
    private final static String boolean_tag = "boolean";
    private final static String float_tag = "float";
    private final static String int_tag = "int";
    private final static String long_tag = "long";
    private final static String string_tag = "string";

    public boolean loadSettings(String file) {
        if( MyDebug.LOG )
            Log.d(TAG, "loadSettings: " + file);
        InputStream inputStream;
        try {
            inputStream = new FileInputStream(file);
        }
        catch(FileNotFoundException e) {
            Log.e(TAG, "failed to load: " + file);
            e.printStackTrace();
            main_activity.getPreview().showToast(null, R.string.restore_settings_failed);
            return false;
        }
        return loadSettings(inputStream);
    }

    public boolean loadSettings(Uri uri) {
        if( MyDebug.LOG )
            Log.d(TAG, "loadSettings: " + uri);
        InputStream inputStream;
        try {
            inputStream = main_activity.getContentResolver().openInputStream(uri);
        }
        catch(FileNotFoundException e) {
            Log.e(TAG, "failed to load: " + uri);
            e.printStackTrace();
            main_activity.getPreview().showToast(null, R.string.restore_settings_failed);
            return false;
        }
        return loadSettings(inputStream);
    }

    /** Loads all settings from the supplied inputStream. If successful, Open Camera will restart.
     *  The supplied inputStream will be closed.
     * @return Whether the operation was succesful.
     */
    private boolean loadSettings(InputStream inputStream) {
        if( MyDebug.LOG )
            Log.d(TAG, "loadSettings: " + inputStream);
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(inputStream, null);
            parser.nextTag();

            parser.require(XmlPullParser.START_TAG, null, doc_tag);
            /*if( true )
            	throw new IOException(); // test*/

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.clear();

            while( parser.next() != XmlPullParser.END_TAG ) {
                if( parser.getEventType() != XmlPullParser.START_TAG)  {
                    continue;
                }
                String name = parser.getName();
                String key = parser.getAttributeValue(null, "key");
                if( MyDebug.LOG ) {
                    Log.d(TAG, "name: " + name);
                    Log.d(TAG, "    key: " + key);
                    Log.d(TAG, "    value: " + parser.getAttributeValue(null, "value"));
                }

                switch( name ) {
                    case boolean_tag:
                        editor.putBoolean(key, Boolean.valueOf(parser.getAttributeValue(null, "value")));
                        break;
                    case float_tag:
                        editor.putFloat(key, Float.valueOf(parser.getAttributeValue(null, "value")));
                        break;
                    case int_tag:
                        editor.putInt(key, Integer.parseInt(parser.getAttributeValue(null, "value")));
                        break;
                    case long_tag:
                        editor.putLong(key, Long.parseLong(parser.getAttributeValue(null, "value")));
                        break;
                    case string_tag:
                        editor.putString(key, parser.getAttributeValue(null, "value"));
                        break;
                    default:
                        break;
                }

                skipXml(parser);
            }

            // even though we're restoring from settings, we don't want the first time or what's new dialog showing up again!
            // important to do this after reading from xml, so that the keys aren't overwritten
            editor.putBoolean(PreferenceKeys.FirstTimePreferenceKey, true);
            try {
                PackageInfo pInfo = main_activity.getPackageManager().getPackageInfo(main_activity.getPackageName(), 0);
                int version_code = pInfo.versionCode;
                editor.putInt(PreferenceKeys.LatestVersionPreferenceKey, version_code);
            }
            catch(PackageManager.NameNotFoundException e) {
                if (MyDebug.LOG)
                    Log.d(TAG, "NameNotFoundException exception trying to get version number");
                e.printStackTrace();
            }

            editor.apply();
            if( !main_activity.is_test ) {
                // restarting seems to cause problems for test code (e.g., see testSettingsSaveLoad - even if that test is fine, it risks affecting subsequent tests)
                main_activity.restartOpenCamera();
            }
            return true;
        }
        catch(Exception e) {
            e.printStackTrace();
            main_activity.getPreview().showToast(null, R.string.restore_settings_failed);
            return false;
        }
        finally {
            try {
                inputStream.close();
            }
            catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void skipXml(XmlPullParser parser) throws XmlPullParserException, IOException {
        if( parser.getEventType() != XmlPullParser.START_TAG ) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    public void saveSettings(String filename) {
        if( MyDebug.LOG )
            Log.d(TAG, "saveSettings: " + filename);
        OutputStream outputStream = null;
        try {
            StorageUtils storageUtils = main_activity.getStorageUtils();
            /*OutputStream outputStream;
            Uri uri = null;
            File file = null;
            if( storageUtils.isUsingSAF() ) {
                uri = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_PREFS, "", "xml", new Date());
                outputStream = main_activity.getContentResolver().openOutputStream(uri);
            }
            else {
                file = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_PREFS, "", "xml", new Date());
                main_activity.test_save_settings_file = file.getAbsolutePath();
                outputStream = new FileOutputStream(file);
            }*/
            File settings_folder = storageUtils.getSettingsFolder();
            // in theory the folder should have been created when choosing a name, but just in case...
            storageUtils.createFolderIfRequired(settings_folder);
            File file = new File(settings_folder.getPath() + File.separator + filename);
            main_activity.test_save_settings_file = file.getAbsolutePath();
            outputStream = new FileOutputStream(file);

            XmlSerializer xmlSerializer = Xml.newSerializer();

            StringWriter writer = new StringWriter();
            xmlSerializer.setOutput(writer);
            xmlSerializer.startDocument("UTF-8", true);
            xmlSerializer.startTag(null, doc_tag);

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
            Set set = sharedPreferences.getAll().entrySet();
            for( Object aSet : set) {
                Map.Entry entry = (Map.Entry) aSet;
                String key = (String)entry.getKey();
                Object value = entry.getValue();
                if( key != null ) {
                    String tag_type = null;
                    if( value instanceof Boolean ) {
                        tag_type = boolean_tag;
                    }
                    else if( value instanceof Float ) {
                        tag_type = float_tag;
                    }
                    else if( value instanceof Integer ) {
                        tag_type = int_tag;
                    }
                    else if( value instanceof Long ) {
                        tag_type = long_tag;
                    }
                    else if( value instanceof String ) {
                        tag_type = string_tag;
                    }
                    else {
                        Log.e(TAG, "unknown value type: " + value);
                    }

                    if( tag_type != null ) {
                        xmlSerializer.startTag(null, tag_type);
                        xmlSerializer.attribute(null, "key", key);
                        if( value != null ) {
                            xmlSerializer.attribute(null, "value", value.toString());
                        }
                        xmlSerializer.endTag(null, tag_type);
                    }
                }
            }
            xmlSerializer.endTag(null, doc_tag);
            xmlSerializer.endDocument();
            xmlSerializer.flush();
            String dataWrite = writer.toString();
            /*if( true )
            	throw new IOException(); // test*/
            outputStream.write(dataWrite.getBytes(Charset.forName("UTF-8")));

            main_activity.getPreview().showToast(null, R.string.saved_settings);
            /*if( uri != null ) {
                storageUtils.broadcastUri(uri, false, false, false);
            }
            else*/ {
                storageUtils.broadcastFile(file, false, false, false);
            }
        }
        catch(IOException e) {
            e.printStackTrace();
            main_activity.getPreview().showToast(null, R.string.save_settings_failed);
        }
        finally {
            if( outputStream != null ) {
                try {
                    outputStream.close();
                }
                catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
