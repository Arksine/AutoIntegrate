package com.arksine.autointegrate;

import android.app.FragmentManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.preference.PreferenceFragment;
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


        // TODO: Start service if not started?
        // TODO: Add navigation drawer
        // TODO: Add other screens (status, hdradio, power)

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
        switch (position) {
            case 0:
                fragment = new StatusFragment();
                title = "Service Status";
                break;
            case 1:     // TODO: Power Settings
                break;
            case 2:     // Arduino Settings
                fragment = new ArduinoSettings();
                title = "Arduino Settings";
                Log.i(TAG, "Arduino Settings Selected");
                break;
            case 3:     // TODO: Camera Settings
                break;
            case 4:     // TODO: HD Radio Settings
                break;
            default:
                // TODO: not supported, log and send toast
                return;

        }

        if (fragment == null)
            return;

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
        getSupportActionBar().setTitle(title);
    }


}
