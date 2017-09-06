package com.arksine.autointegrate.activities;

import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.arksine.autointegrate.AutoIntegrate;
import com.arksine.autointegrate.MainService;
import com.arksine.autointegrate.interfaces.ServiceControlInterface;
import com.arksine.autointegrate.preferences.MainSettings;
import com.arksine.autointegrate.preferences.MicroControllerSettings;
import com.arksine.autointegrate.preferences.CameraSettings;
import com.arksine.autointegrate.preferences.PowerSettings;
import com.arksine.autointegrate.preferences.RadioSettings;
import com.arksine.autointegrate.R;
import com.arksine.autointegrate.utilities.UtilityFunctions;

import timber.log.Timber;


public class MainActivity extends AppCompatActivity {

    private String[] mSettingTitles;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;

    private ActionBarDrawerToggle mDrawerToggle;
    private CharSequence mDrawerTitle;
    private CharSequence mTitle;

    // This listener is called after a shared preference is applied (not before like with
    // Preference::OnPreferenceChangeListener).  This makes sure that all preferences
    // are set before executing, which is required to refresh the service thread.
    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case "controller_pref_key_select_device": {
                    // MCU Device has been changed, refresh the connection
                    ServiceControlInterface serviceControl =
                            AutoIntegrate.getServiceControlInterface();
                    if (serviceControl != null) {
                        serviceControl.refreshMcuConnection(false, null);
                    }
                    break;
                }
                case "radio_pref_key_select_driver": {
                    // Radio driver has been changed, refresh the connection
                    ServiceControlInterface serviceControl =
                            AutoIntegrate.getServiceControlInterface();
                    if (serviceControl != null) {
                        serviceControl.refreshRadioConnection();
                    }
                    break;
                }
                default:
                    Timber.v("Preference Changed: %s", key);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(mPreferenceListener);

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

        // Store a list of applications installed on the device
        UtilityFunctions.initAppList(this);

        // Make sure we are granted settings permission, launch dialog if necessary
        boolean canWriteSettings = UtilityFunctions.checkSettingsPermission(this);

        // Start the service if it isn't started
        if (canWriteSettings && !UtilityFunctions.isServiceRunning(MainService.class, this)) {
            Intent startIntent = new Intent(this, MainService.class);
            this.startService(startIntent);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        UtilityFunctions.destroyAppList();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(mPreferenceListener);
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
        Timber.v("Display size inches: " + screenInches);

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
                fragment = new MainSettings();
                title = "Main Settings";
                break;
            case 1:     // Power Settings
                if (sharedPrefs.getBoolean("main_pref_key_toggle_power", false)) {
                    fragment = new PowerSettings();
                    title = "Power Settings";
                } else {
                    Toast.makeText(this, "Power Management Integration Disabled",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case 2:     // Arduino Settings
                if (sharedPrefs.getBoolean("main_pref_key_toggle_controller", false)) {
                    fragment = new MicroControllerSettings();
                    title = "Controller Settings";
                    Timber.v("Controller Settings Selected");
                } else {
                    Toast.makeText(this, "Main Integration Disabled",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case 3:     // Camera Settings
                if (sharedPrefs.getBoolean("main_pref_key_toggle_camera", false)) {
                    fragment = new CameraSettings();
                    title = "Camera Settings";
                } else {
                    Toast.makeText(this, "Camera Integration Disabled",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case 4:     // HD Radio Settings
                if (sharedPrefs.getBoolean("main_pref_key_toggle_radio", false)) {
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
                Timber.w("Unsupported navigation selection: %d", position);
                Toast.makeText(this, "Selection not supported",
                        Toast.LENGTH_SHORT).show();
                return;

        }

        // Insert the fragment by replacing any existing fragment
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commit();

        // Highlight the selected item, update the title, and onDisconnect the drawer
        mDrawerList.setItemChecked(position, true);
        setTitle(title);
        if(mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(mDrawerList);
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            getSupportActionBar().setTitle(mTitle);
        } else {
            Timber.w("Error retrieving Action Bar");
        }
    }

}
