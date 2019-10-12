package net.sourceforge.opencamera;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

/** Handles the Open Camera "take photo" widget. This widget launches Open
 *  Camera, and immediately takes a photo.
 */
public class MyWidgetProviderTakePhoto extends AppWidgetProvider {
    private static final String TAG = "MyWidgetProviderTakePho";
    
    // see http://developer.android.com/guide/topics/appwidgets/index.html
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int [] appWidgetIds) {
        if( MyDebug.LOG )
            Log.d(TAG, "onUpdate");
        if( MyDebug.LOG )
            Log.d(TAG, "length = " + appWidgetIds.length);

        for(int appWidgetId : appWidgetIds) {
            if( MyDebug.LOG )
                Log.d(TAG, "appWidgetId: " + appWidgetId);

            Intent intent = new Intent(context, TakePhoto.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

            RemoteViews remote_views = new RemoteViews(context.getPackageName(), R.layout.widget_layout_take_photo);
            remote_views.setOnClickPendingIntent(R.id.widget_take_photo, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, remote_views);
        }
    }

    /*@Override
    public void onReceive(Context context, Intent intent) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "onReceive " + intent);
        }
        if (intent.getAction().equals("net.sourceforge.opencamera.LAUNCH_OPEN_CAMERA")) {
            if( MyDebug.LOG )
                Log.d(TAG, "Launching MainActivity");
            final Intent activity = new Intent(context, MainActivity.class);
            activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activity);
            if( MyDebug.LOG )
                Log.d(TAG, "done");
        }
        super.onReceive(context, intent);
    }*/
}
