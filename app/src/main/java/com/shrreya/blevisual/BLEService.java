package com.shrreya.blevisual;

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
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

public class BLEService extends Service {

    private final static String LOG_TAG = BLEService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    private int mConnectionState = STATE_DISCONNECTED;

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.shrreya.blevisual.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.shrreya.blevisual.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.shrreya.blevisual.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.shrreya.blevisual.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.shrreya.blevisual.bluetooth.le.EXTRA_DATA";

    private final static UUID UUID_ADC_SERVICE = UUID.fromString(Constants.getAdcService());
    private final static UUID UUID_DATA_CHARACTERISTIC = UUID.fromString(Constants.getDataCharacteristic());
    private final static UUID UUID_CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR = UUID.fromString(Constants.CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR);

    private static final int  MTU_LENGTH = 42;

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            String intentAction;

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(LOG_TAG, "Connected to GATT server.");
                Log.d(LOG_TAG, "Attempting to start service discovery.");
                mBluetoothGatt.discoverServices();
            }

            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.e(LOG_TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                mBluetoothGatt.requestMtu(MTU_LENGTH);
            }

            else {
                Log.w(LOG_TAG, "onServicesDiscovered received : " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Descriptor write successful!");
            }

            else {
                Log.w(LOG_TAG, "onDescriptorWrite received : " + status);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "MTU changed : " + mtu);
                setCharacteristicNotification();
            }

            else {
                Log.w(LOG_TAG, "onMtuChanged received : " + status);
            }
        }
    };

    private void setCharacteristicNotification() {
        // get the service from the device
        BluetoothGattService mCustomService = mBluetoothGatt.getService(UUID_ADC_SERVICE);

        if (mCustomService != null) {

            // get the data characteristic from the service
            BluetoothGattCharacteristic mDataCharacteristic = mCustomService.getCharacteristic(UUID_DATA_CHARACTERISTIC);
            if (mDataCharacteristic != null) {

                if(mBluetoothGatt.setCharacteristicNotification(mDataCharacteristic, true)) {
                    Log.d(LOG_TAG, "Set Characteristic Notification : SUCCESS");
                } else {
                    Log.e(LOG_TAG, "Set Characteristic Notification : FAILURE");
                }

                // get the client characteristic configuration descriptor from the characteristic
                BluetoothGattDescriptor mConfigDescriptor = mDataCharacteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR);
                if(mConfigDescriptor != null) {
                    Log.d(LOG_TAG, "Writing to descriptor...");
                    mConfigDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mBluetoothGatt.writeDescriptor(mConfigDescriptor);
                } else {
                    Log.w(LOG_TAG, "Client Configuration Descriptor not found!");
                }
            } else {
                Log.w(LOG_TAG, "Data characteristic not found!");
                return;
            }
        } else{
            Log.w(LOG_TAG, "ADC Service not found!");
            return;
        }
    }

    // broadcast intents
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    // broadcasts intents with data
    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        if (UUID_DATA_CHARACTERISTIC.equals(characteristic.getUuid())) {
            final byte[] data = characteristic.getValue();
            Log.d(LOG_TAG, "Received data.");
            intent.putExtra(EXTRA_DATA, data);
        }

        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BLEService getService() {
            return BLEService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(LOG_TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(LOG_TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(LOG_TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(LOG_TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(LOG_TAG, "Device not found.  Unable to connect.");
            return false;
        }

        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(LOG_TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        broadcastUpdate(ACTION_GATT_DISCONNECTED);
    }

    public int getConnectionState() {
        return mConnectionState;
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}
