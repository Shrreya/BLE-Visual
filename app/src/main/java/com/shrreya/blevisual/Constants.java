package com.shrreya.blevisual;

import android.util.Log;

public class Constants {

    // Enter your custom service UUID below between the quotes
    private static final String ADC_SERVICE = "";

    // Enter your data characteristic UUID below between the quotes
    private static final String DATA_CHARACTERISTIC = "";

    // This is a standard descriptor UUID, change it if required
    public static final String CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb";

    // This is an unusable dummy UUID
    private static final String DUMMY_UUID = "00000000-0000-0000-0000-000000000000";

    // Enter your custom device name below between the quotes
    public static final String DEVICE_NAME = "";


    public static String getAdcService() {
        if(!ADC_SERVICE.equals("")) {
            return ADC_SERVICE;
        }
        Log.e(Constants.class.getSimpleName(), "Please make sure you have entered your custom UUIDs since " +
                    "the app is currently using dummy UUID for ADC_SERVICE.");
        return DUMMY_UUID;
    }

    public static String getDataCharacteristic() {
        if(!DATA_CHARACTERISTIC.equals("")) {
            return DATA_CHARACTERISTIC;
        }
        Log.e(Constants.class.getSimpleName(), "Please make sure you have entered your custom UUIDs since " +
                    "the app is currently using dummy UUID for DATA_CHARACTERISTIC.");
        return DUMMY_UUID;
    }

}
