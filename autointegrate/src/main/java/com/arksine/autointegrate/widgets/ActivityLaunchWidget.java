package com.arksine.autointegrate.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.arksine.autointegrate.R;
import com.arksine.autointegrate.activities.ButtonLearningActivity;
import com.arksine.autointegrate.activities.MainActivity;
import com.arksine.autointegrate.activities.RadioActivity;

/**
 * Widget to Launch various activities
 */

public class ActivityLaunchWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);

        for (int widgetId : appWidgetIds) {
            PendingIntent settingsPi = getActivityLaunchIntent(context, MainActivity.class);
            PendingIntent resMapPi = getActivityLaunchIntent(context, ButtonLearningActivity.class);
            PendingIntent radioPi = getActivityLaunchIntent(context, RadioActivity.class);

            // Set the view
            RemoteViews views = new RemoteViews(context.getPackageName(),
                    R.layout.activity_launch_widget_layout);
            views.setOnClickPendingIntent(R.id.widget_btn_settings, settingsPi);
            views.setOnClickPendingIntent(R.id.widget_btn_resistive_map, resMapPi);
            views.setOnClickPendingIntent(R.id.widget_btn_radio, radioPi);

            Intent cameraIntent  = context.getPackageManager()
                    .getLaunchIntentForPackage("com.arksine.autocamera");

            if (cameraIntent != null) {
                PendingIntent cameraPi = PendingIntent.getActivity(context, 0, cameraIntent, 0);
                views.setOnClickPendingIntent(R.id.widget_btn_camera, cameraPi);
            }

            appWidgetManager.updateAppWidget(widgetId, views);
        }
    }

    private PendingIntent getActivityLaunchIntent(Context context, Class<?> cls) {
        Intent activityIntent = new Intent(context, cls);
        return PendingIntent.getActivity(context, 0, activityIntent, 0);
    }

}
