package com.shrreya.blevisual.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


public class SensorMapFragment extends Fragment {

    private com.shrreya.blevisual.SensorMap sensorMap;

    public SensorMapFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        sensorMap = new com.shrreya.blevisual.SensorMap(getContext());
        return sensorMap;

    }

    public void updateSensorMap(String postureData) {

        if (postureData.length() > 0) {

            int[][] values = new int[4][4];
            int j = 0, k = 0;
            for (int i = 0; i < 64; i += 4) {
                values[j][k] = Integer.parseInt(postureData.substring(i, i + 4), 16);
                if(k == 3) {
                    j++;
                    k = 0;
                } else {
                    k++;
                }
            }

            sensorMap.setValues(values);
            sensorMap.invalidate();
        }
    }
}
