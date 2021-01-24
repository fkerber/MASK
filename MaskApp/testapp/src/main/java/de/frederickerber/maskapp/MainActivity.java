package de.frederickerber.maskapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import de.frederickerber.maskapp.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MaskAppMain";

    Spinner plugin_spinner;

    private List<ResolveInfo> resolveInfo;
    private PluginFragment selectedPlugin;
    private String selectedPluginServiceName;
    private ResolveInfo selectedResolveInfo;
    private FragmentTransaction ft;
    private FragmentManager fm;
    private String[] selectedStringArray;

    //Fragments for each Plugin
    private Map<String, PluginFragment> mPluginFragments;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ArrayList<String> mplugins = new ArrayList<String>();

        mPluginFragments = new HashMap<>();

        fm = getSupportFragmentManager();

        //get all installed plugins
        PackageManager packageManager = getPackageManager();
        Intent sendIntent = new Intent();
        sendIntent.setAction("de.frederickerber.mask.plugin");

        resolveInfo = packageManager.queryIntentServices(sendIntent, PackageManager.MATCH_DEFAULT_ONLY);
        Log.d(TAG, "ResolveInfo " + resolveInfo);

        plugin_spinner = (Spinner) findViewById(R.id.pluginSpinner);

        Iterator<ResolveInfo> iterator = resolveInfo.iterator();
        while (iterator.hasNext()) {
            ResolveInfo res = iterator.next();
            String className = res.serviceInfo.name;
            String packageName = res.serviceInfo.packageName;
            String[] split = sendIntent.getAction().split("\\.");
            String serviceAction = split[0] + "." + split[1] + "." + split[2] + "." + packageName.split("\\.")[2].substring(4);
            PluginFragment pluginFragment = new PluginFragment();
            pluginFragment.setPluginHandler(new PluginHandler(pluginFragment, serviceAction, packageName, className));
            pluginFragment.setServiceName(packageName.split("\\.")[2].substring(4));
            mPluginFragments.put(packageName.split("\\.")[2].substring(4), pluginFragment);

            mplugins.add(packageName.split("\\.")[2].substring(4));

        }

        //init spinner with all plugins
        ArrayAdapter<String> plugin_adapter = new ArrayAdapter<String>(
                getApplicationContext(), android.R.layout.simple_spinner_item, mplugins);

        plugin_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        plugin_spinner.setAdapter(plugin_adapter);


        plugin_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                View v = plugin_spinner.getSelectedView();
                ((TextView) v).setTextColor(Color.BLACK);

                selectedResolveInfo = resolveInfo.get(position);
                selectedPluginServiceName = selectedResolveInfo.serviceInfo.name;
                selectedStringArray = selectedPluginServiceName.split("\\.");
                selectedPlugin = mPluginFragments.get(selectedStringArray[2].substring(4));
                Log.d(TAG, "selected Plugin name: " + selectedPlugin);

                changeFragment(selectedPlugin);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void changeFragment(PluginFragment fragment) {

        ft = fm.beginTransaction();
        if (fm.findFragmentByTag(fragment.getServiceName()) == null) {
            ft.add(R.id.fragment_placeholder, fragment, fragment.getServiceName());
            hideAllOtherFragments(fragment);
            ft.addToBackStack(fragment.getServiceName());

        } else {
            hideAllOtherFragments(fragment);
            Log.d(TAG, "fragment show");
        }
    }

    //hide all other fragment and show the given fragment
    private void hideAllOtherFragments(android.support.v4.app.Fragment plugin) {

        for (Map.Entry<String, PluginFragment> entry : mPluginFragments.entrySet()) {
            if (entry.getValue() != plugin) {
                ft.hide(entry.getValue());
                Log.d(TAG, "hide Fragments");
            }
        }
        ft.show(plugin);
        ft.commit();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        //disconnect from all plugins
        for (Map.Entry<String, PluginFragment> entry : mPluginFragments.entrySet()) {
            entry.getValue().disconnectFromPlugin();
        }
    }

}
