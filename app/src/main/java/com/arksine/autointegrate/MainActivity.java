package com.arksine.autointegrate;

import android.app.ActivityManager;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {

    private static String TAG = "MainActivity";
    private String[] mSettingTitles;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;

    private ActionBarDrawerToggle mDrawerToggle;
    private CharSequence mDrawerTitle;
    private CharSequence mTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: Edit the layouts so the theme is better
        setLayout();

        mTitle = mDrawerTitle = getTitle();
        mSettingTitles = getResources().getStringArray(R.array.drawer_setting_titles);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.listview_drawer);

        // Set the adapter for the list view
        mDrawerList.setAdapter(new ArrayAdapter<String>(this,
                R.layout.drawer_list_item, mSettingTitles));
        // Set the list's click listener
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());


        if (mDrawerLayout != null) {
            // Set a custom shadow that overlays the main content when the drawer opens
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
            // Enable ActionBar app icon to behave as action to toggle nav drawer
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

            // ActionBarDrawerToggle ties together the proper interactions
            // between the sliding drawer and the action bar app icon
            mDrawerToggle = new ActionBarDrawerToggle(
                    this,
                    mDrawerLayout,
                    R.string.drawer_open,
                    R.string.drawer_close) {

                public void onDrawerClosed(View view) {
                    super.onDrawerClosed(view);
                }

                public void onDrawerOpened(View drawerView) {
                    // Set the title on the action when drawer open
                    getSupportActionBar().setTitle(mDrawerTitle);
                    super.onDrawerOpened(drawerView);
                }
            };

            mDrawerLayout.addDrawerListener(mDrawerToggle);
        }

        if (savedInstanceState == null) {
            selectItem(0);
        }

        // Start the service if it isn't started
        if (!isServiceRunning(MainService.class)) {
            Intent startIntent = new Intent(this, MainService.class);
            this.startService(startIntent);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TODO: refresh service...refresh service in each preferencefragment's onDestroy as well
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (mDrawerToggle != null) {
            // Sync the toggle state after onRestoreInstanceState has occurred.
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Sets the current content view based on the current orientation and screen size.
     * If the device is in landscape mode 6.5" or larger, a linear layout is used
     * with a fixed navigation drawer.  Otherwise a hidden navigation drawer is used
     */
    private void setLayout() {

        int orientation = getResources().getConfiguration().orientation;
        Display display = getWindowManager().getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        int widthPixels;
        int heightPixels;

        try {
            Point realSize = new Point();
            Display.class.getMethod("getRealSize", Point.class).invoke(display, realSize);
            widthPixels = realSize.x;
            heightPixels = realSize.y;
        } catch (Exception e) {
            // something went wrong, just use displaymetrics
            widthPixels = dm.widthPixels;
            heightPixels = dm.heightPixels;
        }
        display.getMetrics(dm);
        double x = Math.pow(widthPixels/dm.xdpi,2);
        double y = Math.pow(heightPixels/dm.ydpi,2);
        double screenInches = Math.sqrt(x+y);
        Log.i(TAG, "Display size inches: " + screenInches);

        if (orientation == Configuration.ORIENTATION_LANDSCAPE &&
                screenInches >= 6.5) {
            setContentView(R.layout.activity_main_linear);
        } else {
            setContentView(R.layout.activity_main);
        }
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectItem(position);
        }
    }

    /** Swaps fragments in the main content view */
    private void selectItem(int position) {
        CharSequence title = "";
        PreferenceFragment fragment = null;

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        switch (position) {
            case 0:     // Service Settings
                fragment = new StatusFragment();
                title = "Service Status";
                break;
            case 1:     // Power Settings
                if (sharedPrefs.getBoolean("status_pref_key_toggle_power", false)) {
                    fragment = new PowerSettings();
                    title = "Power Settings";
                } else {
                    Toast.makeText(this, "Power Management Integration Disabled",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case 2:     // Arduino Settings
                if (sharedPrefs.getBoolean("status_pref_key_toggle_arduino", false)) {
                    fragment = new ArduinoSettings();
                    title = "Arduino Settings";
                    Log.i(TAG, "Arduino Settings Selected");
                } else {
                    Toast.makeText(this, "Arduino Integration Disabled",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case 3:     // Camera Settings
                if (sharedPrefs.getBoolean("status_pref_key_toggle_camera", false)) {
                    fragment = new CameraSettings();
                    title = "Camera Settings";
                } else {
                    Toast.makeText(this, "Camera Integration Disabled",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case 4:     // HD Radio Settings
                if (sharedPrefs.getBoolean("status_pref_key_toggle_radio", false)) {
                    fragment = new RadioSettings();
                    title = "HD Radio Settings";
                } else {
                    Toast.makeText(this, "HD Radio Integration Disabled",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            default:
                // not supported, log and send toast
                Log.e(TAG, "Unsupported navigation selection: " + position);
                Toast.makeText(this, "Selection not supported",
                        Toast.LENGTH_SHORT).show();
                return;

        }

        // Insert the fragment by replacing any existing fragment
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commit();

        // Highlight the selected item, update the title, and close the drawer
        mDrawerList.setItemChecked(position, true);
        setTitle(title);
        if(mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(mDrawerList);
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getSupportActionBar().setTitle(mTitle);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}
