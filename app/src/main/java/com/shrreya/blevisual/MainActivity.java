package com.shrreya.blevisual;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.shrreya.blevisual.fragments.SensorMapFragment;
import com.shrreya.blevisual.fragments.InfoFragment;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private CoordinatorLayout coordinatorLayout;
    private FloatingActionButton fab;
    private ProgressDialog scanningDialog, connectingDialog;
    private Snackbar snackbar;
    private ViewPagerAdapter adapter;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner blescanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BLEService mBLEService;

    private static final long SCAN_PERIOD = 20000;

    private static final UUID UUID_ADC_SERVICE = UUID.fromString(Constants.getAdcService());

    private static final int PERMISSION_REQUEST = 1;
    private static final String[] PERMISSIONS = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static int found = 0;

    // managing service lifecycle here
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBLEService = ((BLEService.LocalBinder) service).getService();
            if (!mBLEService.initialize()) {
                Log.e(LOG_TAG, "Unable to initialize Bluetooth!");
                finish();
            }
            Log.d(LOG_TAG, "BLE Service connected!");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBLEService = null;
            Log.d(LOG_TAG, "BLE Service disconnected!");
        }
    };

    // broadcast receiver for BLE service intents
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            switch (action) {

                case BLEService.ACTION_GATT_CONNECTED : {
                    connectingDialog.dismiss();
                    scanLeDevice(false);
                    invalidateOptionsMenu();
                    fab.hide();
                    InfoFragment infoFragment = (InfoFragment) adapter.getItem(0);
                    infoFragment.setStatus(getString(R.string.connected_msg));
                    break;
                }


                case BLEService.ACTION_GATT_DISCONNECTED : {
                    if (connectingDialog.isShowing()) {
                        connectingDialog.dismiss();
                    }
                    scanLeDevice(false);
                    invalidateOptionsMenu();
                    fab.show();
                    InfoFragment infoFragment = (InfoFragment) adapter.getItem(0);
                    infoFragment.setStatus(getString(R.string.disconnected_msg));
                    break;
                }

                case BLEService.ACTION_GATT_SERVICES_DISCOVERED : {
                    List<BluetoothGattService> services = mBLEService.getSupportedGattServices();
                    for (BluetoothGattService service : services) {
                        Log.d(LOG_TAG, "Discovered service : " + service.getUuid());
                    }
                    break;
                }

                case BLEService.ACTION_DATA_AVAILABLE : {
                    Log.d(LOG_TAG, "Data received!");

                    byte sensorValues[] = intent.getByteArrayExtra(BLEService.EXTRA_DATA);

                    // send sensor data to map fragment
                    String sensorData = Utils.bytesToHex(sensorValues);
                    SensorMapFragment sensorMapFragment = (SensorMapFragment) adapter.getItem(1);
                    sensorMapFragment.updateSensorMap(sensorData);

                    break;
                }
            }
        }
    };

    // broadcast receiver to handle bluetooth state change while app is running
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF) {
                    updateUI(false);
                } else if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
                    updateUI(true);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        View actionBarView = getLayoutInflater().inflate(R.layout.app_title, null);
        ActionBar.LayoutParams params = new ActionBar.LayoutParams(
                ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        if(actionBar != null) {
            actionBar.setCustomView(actionBarView, params);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
        }

        ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
        setupViewPager(viewPager);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);

        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.main_activity_layout);

        // check and prompt user for permissions
        if(!Utils.hasPermissions(this, PERMISSIONS)){
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST);
        }

        // set up bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        this.registerReceiver(bluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        // BLE scan settings and filters
        settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        filters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UUID_ADC_SERVICE)).build();
        filters.add(filter);

        // progress dialog for while scanning for devices
        scanningDialog = new ProgressDialog(this);
        scanningDialog.setMessage(getString(R.string.scanning_msg));
        scanningDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        scanningDialog.setCancelable(false);

        //progress dialog for while connecting to device
        connectingDialog = new ProgressDialog(this);
        connectingDialog.setMessage(getString(R.string.connecting_msg));
        connectingDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        connectingDialog.setCancelable(false);

        //snackbar for when bluetooth is turned off
        snackbar = Snackbar.make(coordinatorLayout, getString(R.string.bluetooth_off_msg), Snackbar.LENGTH_INDEFINITE);

        // floating action button to scan for devices
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanningDialog.show();
                scanLeDevice(true);
            }
        });

        //bind BLE service to activity
        Intent gattServiceIntent = new Intent(this, BLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, PERMISSION_REQUEST);
        } else {
            blescanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(bluetoothReceiver);
        if (mBLEService.getConnectionState() == BLEService.STATE_CONNECTED)
            disconnectDevice();
        unbindService(mServiceConnection);
        mBLEService = null;
    }

    // callback for request bluetooth access
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PERMISSION_REQUEST && resultCode == Activity.RESULT_CANCELED) {
            updateUI(false);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // callback for permissions requested
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST && permissions.length > 0 && grantResults.length > 0) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                        grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    // close app if coarse location permission is denied
                    finish();
                }
            }
        }
    }

    // modifications in UI according to bluetooth state change
    private void updateUI(boolean bluetoothOn) {
        if (bluetoothOn) {
            if (snackbar.isShown())
                snackbar.dismiss();
            if (!fab.isShown())
                fab.show();
        } else {
            snackbar.show();
            fab.hide();
        }
    }

    // view pager for tab view fragments
    private void setupViewPager(ViewPager viewPager) {
        adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new InfoFragment(), "Connection Info");
        adapter.addFragment(new SensorMapFragment(), "Sensor Map");
        viewPager.setAdapter(adapter);
    }

    // intent filter for BLE service broadcast intents
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    // BLE scanning
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stop scanning after a pre-defined scan period.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    blescanner.stopScan(mScanCallback);
                    if(scanningDialog.isShowing())
                        scanningDialog.dismiss();
                }
            }, SCAN_PERIOD);
            blescanner.startScan(filters, settings, mScanCallback);
            // initialise found count when scanning starts
            found = 0;
        } else {
            blescanner.stopScan(mScanCallback);
        }
    }

    // callback for BLE scan results
    private ScanCallback mScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(LOG_TAG, "Callback Type : " + String.valueOf(callbackType));
            Log.d(LOG_TAG, "Result : " + result.toString());
            final BluetoothDevice btDevice = result.getDevice();
            Log.d(LOG_TAG, "Identified device : " + btDevice.getAddress());
            String deviceName = btDevice.getName();
            // check device again by name
            if(deviceName != null && deviceName.equals(Constants.DEVICE_NAME)) {
                found++;
                if(scanningDialog.isShowing())
                    scanningDialog.dismiss();
                // show connect dialog only when found for the first time
                if(found == 1) {
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                    alertDialogBuilder.setMessage(getString(R.string.device_connect_msg));
                    alertDialogBuilder.setCancelable(false);
                    alertDialogBuilder.setPositiveButton(getString(R.string.yes_msg),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    connectToDevice(btDevice);
                                }
                            });
                    alertDialogBuilder.setNegativeButton(getString(R.string.cancel_msg),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    // stop scanning if user does not want to connect to device
                                    scanLeDevice(false);
                                }
                            });
                    AlertDialog alertDialog = alertDialogBuilder.create();
                    alertDialog.show();
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results : ", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(LOG_TAG, "Scan Failed! Error Code : " + errorCode);
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        connectingDialog.show();
        mBLEService.connect(device.getAddress());
    }

    private void disconnectDevice() {
        mBLEService.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // show or hide record and disconnect option depending on current connection status
        if(mBLEService.getConnectionState() == BLEService.STATE_DISCONNECTED) {
            setMenuVisibility(menu, false);
        } else {
            setMenuVisibility(menu, true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch(id) {
            // no settings in app as of now
            case R.id.action_settings :
                return true;
            case R.id.action_disconnect :
                disconnectDevice();
                break;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setMenuVisibility(Menu menu, boolean visible) {
        menu.getItem(0).setVisible(visible);
        menu.getItem(1).setVisible(visible);
    }
}


