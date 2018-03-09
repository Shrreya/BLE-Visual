package com.shrreya.blevisual.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.shrreya.blevisual.R;

public class InfoFragment extends Fragment {

    private TextView statusTv;

    public InfoFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_info, container, false);

        statusTv = (TextView) view.findViewById(R.id.statusTv);

        return view;
    }

    public void setStatus(String statusMsg) {
        statusTv.setText(statusMsg);
    }

}