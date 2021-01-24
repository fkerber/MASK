package de.frederickerber.maskapp;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;


import de.frederickerber.maskapp.R;

import java.util.ArrayList;
import java.util.List;

import de.frederickerber.maskcommons.SensorType;

public class PluginFragment extends Fragment {

    private Tuples selectedTuple;
    private Integer selectedDeviceId;
    private String selectedSensorType;

    private LinearLayout linearLayout;

    private PluginHandler mHandler;

    String serviceName = null;
    Spinner mdevicesSpinner;
    Spinner supportedSensorsSpinner;

    Button action_Btn;
    private String TAG = "PluginFragment";
    private TextView txt;
    private TextView txt_status;
    private Button plugin_btn;

    public void setPluginHandler(PluginHandler handler) {
        mHandler = handler;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_plugin, container, false);

        txt_status = (TextView) rootView.findViewById(R.id.txt_status);
        supportedSensorsSpinner = (Spinner) rootView.findViewById(R.id.sensor_spinner);
        mdevicesSpinner = (Spinner) rootView.findViewById(R.id.devices_spinner);
        plugin_btn = (Button) rootView.findViewById(R.id.btn_plugin);
        action_Btn = (Button) rootView.findViewById(R.id.btn_sub);
        linearLayout = (LinearLayout) rootView.findViewById(R.id.ll);

        plugin_btn.setText("Connect to " + this.serviceName);

        plugin_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mHandler.isConnected()) {
                    mHandler.disconnectFromSensorService(getActivity().getApplicationContext());
                    plugin_btn.setText("Connect to " + serviceName);
                } else {
                    mHandler.connectToSensorService(getActivity().getApplicationContext(), mHandler.getServiceAction(), mHandler.getPackageName(), mHandler.getClassName());
                    plugin_btn.setText("Disconnect from " + serviceName);

                }
            }
        });
        return rootView;
    }


    public void connectToPlugin(ArrayList<String> devices, final List<String> supportedSensors) {
        Log.d(TAG, "connect To Plugin " + this + " called");

        supportedSensorsSpinner.setVisibility(View.VISIBLE);
        mdevicesSpinner.setVisibility(View.VISIBLE);
        action_Btn.setVisibility(View.VISIBLE);


        //init device spinner
        final ArrayAdapter<String> device_adapter = new ArrayAdapter<String>(
                getContext().getApplicationContext(), android.R.layout.simple_spinner_item, devices);

        device_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mdevicesSpinner.setAdapter(device_adapter);

        mdevicesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "item at postition: " + parent.getItemAtPosition(position));

                View v = mdevicesSpinner.getSelectedView();
                ((TextView) v).setTextColor(Color.BLACK);

                selectedDeviceId = position;

                //init supportedSensors spinner
                ArrayAdapter<String> sensors_adapter = new ArrayAdapter<String>(
                        getContext().getApplicationContext(), android.R.layout.simple_spinner_item, supportedSensors);

                sensors_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                supportedSensorsSpinner.setAdapter(sensors_adapter);

                supportedSensorsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        View v = supportedSensorsSpinner.getSelectedView();
                        ((TextView) v).setTextColor(Color.BLACK);

                        selectedSensorType = supportedSensors.get(position);


                        for (int i = 0; i < mHandler.getAllpossibleSubsribtions().size(); i++) {
                            if (mHandler.getAllpossibleSubsribtions().get(i).x == selectedDeviceId && mHandler.getAllpossibleSubsribtions().get(i).y == selectedSensorType) {
                                selectedTuple = mHandler.getAllpossibleSubsribtions().get(i);
                            }
                        }

                        if (mHandler.getSubscriptionList().get(mHandler.getAllpossibleSubsribtions().indexOf(selectedTuple))) {
                            action_Btn.setText("Unsubscribe from " + selectedSensorType);
                        } else {
                            action_Btn.setText("Subscribe to " + selectedSensorType);
                        }

                        action_Btn.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });
            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                supportedSensorsSpinner.setVisibility(View.INVISIBLE);
            }
        });

        action_Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (mHandler.getSubscriptionList().
                        get(mHandler.getAllpossibleSubsribtions().indexOf(selectedTuple))) {
                    mHandler.getSubscriptionList().
                            set((mHandler.getAllpossibleSubsribtions().indexOf(selectedTuple)), false);

                    unsubscribe();
                } else {
                    mHandler.getSubscriptionList()
                            .set((mHandler.getAllpossibleSubsribtions().indexOf(selectedTuple)), true);
                    txt = new TextView(getContext());
                    txt.setText(selectedSensorType);
                    linearLayout.addView(txt);
                    mHandler.mAllTextViews.put(selectedDeviceId + "#" + SensorType.fromStringToSensorType(selectedSensorType), txt);

                    subscribe();
                }
            }
        });
    }

    private void subscribe() {
        Log.d(TAG, "Subscribe to: " + selectedSensorType);
        mHandler
                .subscribeToSensor(selectedDeviceId, SensorType.fromStringToSensorType(selectedSensorType), 0);
        action_Btn.setText("Unsubscribe from " + selectedSensorType);

    }

    private void unsubscribe() {
        Log.d(TAG, "Unsubscribe From: " + selectedSensorType);
        linearLayout
                .removeView(mHandler.mAllTextViews.get(selectedDeviceId + "#" + SensorType.fromStringToSensorType(selectedSensorType)));
        mHandler
                .unsubscribeFromSensor(selectedDeviceId, SensorType.fromStringToSensorType(selectedSensorType));
        action_Btn.setText("Subscribe to " + selectedSensorType);

    }

    public void disconnectFromPlugin() {

        Log.d(TAG, "disconnect from plugin");
        supportedSensorsSpinner.setVisibility(View.INVISIBLE);
        mdevicesSpinner.setVisibility(View.INVISIBLE);
        action_Btn.setVisibility(View.INVISIBLE);
    }


    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public LinearLayout getLinearLayout() {
        return linearLayout;
    }

    public void setStatusText(String status) {
        txt_status.setText(status);
    }
}



