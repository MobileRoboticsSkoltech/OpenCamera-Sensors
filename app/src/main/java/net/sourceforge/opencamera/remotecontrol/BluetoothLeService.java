package net.sourceforge.opencamera.remotecontrol;

import net.sourceforge.opencamera.MyDebug;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private String mRemoteDeviceType;
    private final HashMap<String, BluetoothGattCharacteristic> subscribedCharacteristics = new HashMap<>();
    private final List<BluetoothGattCharacteristic> charsToSubscribeTo = new ArrayList<>();

    private double currentTemp = -1;
    private double currentDepth = -1;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "net.sourceforge.opencamera.Remotecontrol.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "net.sourceforge.opencamera.Remotecontrol.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "net.sourceforge.opencamera.Remotecontrol.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "net.sourceforge.opencamera.Remotecontrol.ACTION_DATA_AVAILABLE";
    public final static String ACTION_REMOTE_COMMAND =
            "net.sourceforge.opencamera.Remotecontrol.COMMAND";
    public final static String ACTION_SENSOR_VALUE =
            "net.sourceforge.opencamera.Remotecontrol.SENSOR";
    public final static String SENSOR_TEMPERATURE =
            "net.sourceforge.opencamera.Remotecontrol.TEMPERATURE";
    public final static String SENSOR_DEPTH =
            "net.sourceforge.opencamera.Remotecontrol.DEPTH";
    public final static String EXTRA_DATA =
            "net.sourceforge.opencamera.Remotecontrol.EXTRA_DATA";
    public final static int COMMAND_SHUTTER = 32;
    public final static int COMMAND_MODE = 16;
    public final static int COMMAND_MENU = 48;
    public final static int COMMAND_AFMF = 97;
    public final static int COMMAND_UP = 64;
    public final static int COMMAND_DOWN = 80;



    public void setRemoteDeviceType(String remoteDeviceType) {
        if( MyDebug.LOG )
            Log.d(TAG, "Setting remote type: " + remoteDeviceType);
        mRemoteDeviceType = remoteDeviceType;
    }

    // Various callback methods defined by the BLE API.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "Connected to GATT server.");
                    Log.d(TAG, "Attempting to start service discovery");
                }
                mBluetoothGatt.discoverServices();
                currentDepth = -1;
                currentTemp = -1;

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                if( MyDebug.LOG )
                    Log.d(TAG, "Disconnected from GATT server, reattempting every 5 seconds.");
                broadcastUpdate(intentAction);
                attemptReconnect();
            }
        }

        void attemptReconnect() {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                public void run() {
                    if( MyDebug.LOG )
                        Log.d(TAG, "Attempting to reconnect to remote.");
                    connect(mBluetoothDeviceAddress);
                }
            }, 5000);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                subscribeToServices();
            } else {
                if( MyDebug.LOG )
                    Log.d(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (MyDebug.LOG)
                Log.d(TAG,"Got notification");
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onDescriptorWrite (BluetoothGatt gatt,
                                       BluetoothGattDescriptor descriptor,
                                       int status) {
            // We need to wait for this callback before enabling the next notification in case we
            // have several in our list
            if (!charsToSubscribeTo.isEmpty()) {
                setCharacteristicNotification(charsToSubscribeTo.remove(0), true);
            }
        }
    };

    /**
     * Subscribe to the services/characteristics we need depending
     * on the remote device model
     *
     */
    private void subscribeToServices() {
        List<BluetoothGattService> gattServices = getSupportedGattServices();
        if (gattServices == null) return;
        List<UUID> mCharacteristicsWanted;

        switch (mRemoteDeviceType) {
            case "preference_remote_type_kraken":
                mCharacteristicsWanted = KrakenGattAttributes.getDesiredCharacteristics();
                break;
            default:
                mCharacteristicsWanted = Collections.singletonList(UUID.fromString("0000"));
                break;
        }

        // Loops through available GATT Services and characteristics, and subscribe to
        // the ones we want. Today, we just enable notifications since that's all we need.
        for (BluetoothGattService gattService : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                UUID uuid = gattCharacteristic.getUuid();
                if (mCharacteristicsWanted.contains(uuid)) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "Found characteristic to subscribe to: " + uuid);
                    charsToSubscribeTo.add(gattCharacteristic);
                }
            }
        }
        // We need to enable notifications asynchronously
        setCharacteristicNotification(charsToSubscribeTo.remove(0), true);
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate( String action,
                                 final BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        final int format_uint8 = BluetoothGattCharacteristic.FORMAT_UINT8;
        final int format_uint16 = BluetoothGattCharacteristic.FORMAT_UINT16;
        int remoteCommand = -1;

        if (KrakenGattAttributes.KRAKEN_BUTTONS_CHARACTERISTIC.equals(uuid)) {
            if( MyDebug.LOG )
                Log.d(TAG,"Got Kraken button press");
            final int buttonCode= characteristic.getIntValue(format_uint8, 0);
            if( MyDebug.LOG )
                Log.d(TAG, String.format("Received Button press: %d", buttonCode));
            // Note: we stay at a fairly generic level here and will manage variants
            // on the various button actions in MainActivity, because those will change depending
            // on the current state of the app, and we don't want to know anything about that state
            // from the Bluetooth LE service
            // TODO: update to remove all those tests and just forward buttonCode since value is identical
            //       but this is more readable if we want to implement other drivers
            if (buttonCode == 32) {
                // Shutter press
                remoteCommand = COMMAND_SHUTTER;
            } else if (buttonCode == 16) {
                // "Mode" button: either "back" action or "Photo/Camera" switch
                remoteCommand = COMMAND_MODE;
            } else if (buttonCode == 48) {
                // "Menu" button
                remoteCommand = COMMAND_MENU;
            } else if (buttonCode == 97) {
                // AF/MF button
                remoteCommand = COMMAND_AFMF;
            } else if (buttonCode == 96) {
                // Long press on MF/AF button.
                // Note: the camera issues button code 97 first, then
                // 96 after one second of continuous press
            } else if (buttonCode == 64) {
                // Up button
                remoteCommand = COMMAND_UP;
            } else if (buttonCode == 80) {
                // Down button
                remoteCommand = COMMAND_DOWN;
            }
            // Only send forward if we have something to say
            if (remoteCommand > -1) {
                final Intent intent = new Intent(ACTION_REMOTE_COMMAND);
                intent.putExtra(EXTRA_DATA, remoteCommand);
                sendBroadcast(intent);
            }
        } else if (KrakenGattAttributes.KRAKEN_SENSORS_CHARACTERISTIC.equals(uuid)) {
            // The housing returns four bytes.
            // Byte 0-1: depth = (Byte 0 + Byte 1 << 8) / 10 / density
            // Byte 2-3: temperature = (Byte 2 + Byte 3 << 8) / 10
            //
            // Depth is valid for fresh water by default ( makes you wonder whether the sensor
            // is really designed for saltwater at all), and the value has to be divided by the density
            // of saltwater. A commonly accepted value is 1030 kg/m3 (1.03 density)

            double temperature = characteristic.getIntValue(format_uint16, 2) / 10.0;
            double depth = characteristic.getIntValue(format_uint16, 0) / 10.0;

            if (temperature == currentTemp && depth == currentDepth)
                return;

            currentDepth = depth;
            currentTemp = temperature;

            if (MyDebug.LOG)
                Log.d(TAG, "Got new Kraken sensor reading. Temperature: " + temperature + " Depth:" + depth);

            final Intent intent = new Intent(ACTION_SENSOR_VALUE);
            intent.putExtra(SENSOR_TEMPERATURE, temperature);
            intent.putExtra(SENSOR_DEPTH, depth);
            sendBroadcast(intent);
        }

    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        if( MyDebug.LOG )
            Log.d(TAG, "Starting OpenCamera Bluetooth Service");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }


    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

	public boolean connect(final String address) {
        if( MyDebug.LOG )
            Log.d(TAG, "connect: " + address);
        if( mBluetoothAdapter == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "mBluetoothAdapter is null");
            return false;
        }
        else if( address == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "address is null");
            return false;
        }

        if( mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null ) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if( device == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "device not found");
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    if( MyDebug.LOG )
                        Log.d(TAG, "attempt to connect to remote");
                    connect(address);
                }
            }, 5000);
            return false;
        }

        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothDeviceAddress = address;
        return true;
	}

    private void close() {
        if( mBluetoothGatt == null ) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if( mBluetoothAdapter == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "mBluetoothAdapter is null");
            return;
        }
        else if( mBluetoothGatt == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "mBluetoothGatt is null");
            return;
        }

        String uuid = characteristic.getUuid().toString();
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        if (enabled) {
            subscribedCharacteristics.put(uuid, characteristic);
        } else {
            subscribedCharacteristics.remove(uuid);
        }

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(KrakenGattAttributes.CLIENT_CHARACTERISTIC_CONFIG);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    private List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}
