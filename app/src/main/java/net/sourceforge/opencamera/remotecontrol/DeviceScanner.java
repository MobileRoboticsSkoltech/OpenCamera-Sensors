package net.sourceforge.opencamera.remotecontrol;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.R;

import java.util.ArrayList;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class DeviceScanner extends ListActivity {
    private static final String TAG = "OC-BLEScanner";
    private LeDeviceListAdapter leDeviceListAdapter;
    private BluetoothAdapter bluetoothAdapter;
    private boolean is_scanning;
    private Handler bluetoothHandler;
    private SharedPreferences mSharedPreferences;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSIONS = 2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_select);
        bluetoothHandler = new Handler();

        if( !getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) ) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if( bluetoothAdapter == null ) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Button startScanningButton = findViewById(R.id.StartScanButton);
        startScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startScanning();
            }
        });

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        String preference_remote_device_name = PreferenceKeys.RemoteName;
        String remote_name = mSharedPreferences.getString(preference_remote_device_name, "none");
        if( MyDebug.LOG )
            Log.d(TAG, "preference_remote_device_name: " + remote_name);

        TextView currentRemote = findViewById(R.id.currentRemote);
        currentRemote.setText(getResources().getString(R.string.bluetooth_current_remote) + " " + remote_name);

    }

    private void startScanning() {

        if( MyDebug.LOG )
            Log.d(TAG, "Start scanning");

        if( !bluetoothAdapter.isEnabled() ) {
            // fire an intent to display a dialog asking the user to grant permission to enable Bluetooth
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        leDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(leDeviceListAdapter);

        // In real life most of bluetooth LE devices associated with location, so without this
        // permission the sample shows nothing in most cases
        // Also see https://stackoverflow.com/questions/33045581/location-needs-to-be-enabled-for-bluetooth-low-energy-scanning-on-android-6-0
        int permissionCoarse = Build.VERSION.SDK_INT >= 23 ?
                ContextCompat
                        .checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) :
                PackageManager.PERMISSION_GRANTED;

        if( permissionCoarse == PackageManager.PERMISSION_GRANTED ) {
            scanLeDevice(true);
        }
        else {
            askForLocationPermission();
        }
    }

    private void askForLocationPermission() {
        if( MyDebug.LOG )
            Log.d(TAG, "askForLocationPermission");
        // n.b., we only need ACCESS_COARSE_LOCATION, but it's simpler to request both to be consistent with Open Camera's
        // location permission requests in PermissionHandler. If we only request ACCESS_COARSE_LOCATION here, and later the
        // user enables something that needs ACCESS_FINE_LOCATION, Android ends up showing the "rationale" dialog - and once
        // that's dismissed, the permission seems to be granted without showing the permission request dialog (so it works,
        // but is confusing for the user)
        // Also note that if we did want to only request ACCESS_COARSE_LOCATION here, we'd need to declare that permission
        // explicitly in the AndroidManifest.xml, otherwise the dialog to request permission is never shown (and the permission
        // is denied automatically).
        if( ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION) ) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestLocationPermissionRationale();
        }
        else {
            // Can go ahead and request the permission
            if( MyDebug.LOG )
                Log.d(TAG, "requesting location permissions...");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION_PERMISSIONS);
        }
    }

    private void showRequestLocationPermissionRationale() {
        if( MyDebug.LOG )
            Log.d(TAG, "showRequestLocationPermissionRationale");
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M ) {
            if( MyDebug.LOG )
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        String [] permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        int message_id = R.string.permission_rationale_location;

        final String [] permissions_f = permissions;
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_rationale_title)
                .setMessage(message_id)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "requesting permission...");
                        ActivityCompat.requestPermissions(DeviceScanner.this, permissions_f, REQUEST_LOCATION_PERMISSIONS);
                    }
                }).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if( MyDebug.LOG )
            Log.d(TAG, "onRequestPermissionsResult: requestCode " + requestCode);
        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSIONS: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "location permission granted");
                    scanLeDevice(true);
                }
                else {
                    if( MyDebug.LOG )
                        Log.d(TAG, "location permission denied");
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if( MyDebug.LOG )
            Log.d(TAG, "onActivityResult");
        // user decided to cancel the enabling of Bluetooth, so exit
        if( requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED ) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        if( MyDebug.LOG )
            Log.d(TAG, "pause...");
        super.onPause();
        if( is_scanning ) {
            scanLeDevice(false);
            leDeviceListAdapter.clear();
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = leDeviceListAdapter.getDevice(position);
        if( device == null )
            return;
        if( MyDebug.LOG ) {
            Log.d(TAG, "onListItemClick");
            Log.d(TAG, device.getAddress());
        }
        String preference_remote_device_name = PreferenceKeys.RemoteName;
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(preference_remote_device_name, device.getAddress());
        editor.apply();
        scanLeDevice(false);
        finish();
    }

    private void scanLeDevice(final boolean enable) {
        if( MyDebug.LOG )
            Log.d(TAG, "scanLeDevice: " + enable);
        if( enable ) {
            // stop scanning after certain time
            bluetoothHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    is_scanning = false;
                    bluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, 10000);

            is_scanning = true;
            bluetoothAdapter.startLeScan(mLeScanCallback);
        }
        else {
            is_scanning = false;
            bluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    private class LeDeviceListAdapter extends BaseAdapter {
        private final ArrayList<BluetoothDevice> mLeDevices;
        private final LayoutInflater mInflator;

        LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = DeviceScanner.this.getLayoutInflater();
        }

        void addDevice(BluetoothDevice device) {
            if( !mLeDevices.contains(device) ) {
                mLeDevices.add(device);
            }
        }

        BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            if( view == null ) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = view.findViewById(R.id.device_address);
                viewHolder.deviceName = view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            }
            else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if( deviceName != null && deviceName.length() > 0 )
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    private final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    leDeviceListAdapter.addDevice(device);
                    leDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}