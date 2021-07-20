package net.sourceforge.opencamera.ui;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.R;
import net.sourceforge.opencamera.preview.Preview;

import java.text.DecimalFormat;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

/**
 * This defines the UI for the "recSync" button, that provides quick access to a
 * range of options.
 */

public class RecSyncView extends LinearLayout {
    private static final String TAG = "RecSyncView";
    public static final float ALPHA_BUTTON_SELECTED = 1.0f;
    public static final float ALPHA_BUTTON = 0.6f; // 0.4f tends to be hard to see in bright light

    private static final float title_text_size_dip = 17.0f;
    private static final float standard_text_size_dip = 16.0f;

    private int total_width_dp;

    @SuppressWarnings("FieldCanBeLocal")
    private final DecimalFormat decimal_format_1dp_force0 = new DecimalFormat("0.0");

    public RecSyncView(Context context) {
        super(context);
        if (MyDebug.LOG)
            Log.d(TAG, "new RecSyncView: " + this);

        this.setOrientation(LinearLayout.VERTICAL);
        final float scale = getResources().getDisplayMetrics().density;

        final MainActivity main_activity = (MainActivity) this.getContext();

        boolean small_screen = false;
        total_width_dp = 280;
        int max_width_dp = main_activity.getMainUI().getMaxHeightDp(false);
        if (total_width_dp > max_width_dp) {
            total_width_dp = max_width_dp;
            small_screen = true;
        }
        if (MyDebug.LOG) {
            Log.d(TAG, "max_width_dp: " + max_width_dp);
            Log.d(TAG, "total_width_dp: " + total_width_dp);
            Log.d(TAG, "small_screen: " + small_screen);
        }

        final Preview preview = main_activity.getPreview();

        // TODO if (support recSync)

        // checkbox switch recSync
        CheckBox checkBox = new CheckBox(main_activity);
        checkBox.setText(getResources().getString(R.string.preference_switch_rec_sync));
        checkBox.setTextSize(TypedValue.COMPLEX_UNIT_DIP, standard_text_size_dip);
        checkBox.setTextColor(Color.WHITE);
        {
            // align the checkbox a bit better
            LayoutParams params = new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
            );
            final int left_padding = (int) (10 * scale + 0.5f); // convert dps to pixels
            params.setMargins(left_padding, 0, 0, 0);
            checkBox.setLayoutParams(params);
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);

        boolean switch_rec_sync = sharedPreferences.getBoolean(PreferenceKeys.SwitchRecSyncPreferenceKey, false);
        if (switch_rec_sync)
            checkBox.setChecked(switch_rec_sync);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                main_activity.clickedSwitchRecSync();
            }
        });

        this.addView(checkBox);

        // button sync settings TODO createButton() for this

        final Button buttonSyncSettings = new Button(this.getContext());
        buttonSyncSettings.setBackgroundColor(Color.TRANSPARENT);
        buttonSyncSettings.setText(R.string.sync_settings);
        buttonSyncSettings.setAllCaps(false);
        buttonSyncSettings.setTextSize(TypedValue.COMPLEX_UNIT_DIP, title_text_size_dip);
        this.addView(buttonSyncSettings);

        buttonSyncSettings.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View view) {
                if (MyDebug.LOG)
                    Log.d(TAG, "clicked to buttonSyncSettings");

                // TODO sync settings
                preview.showToast(null, "Sync settings finished");
            }
        });


        // button sync devices

        final Button buttonSyncDevices = new Button(this.getContext());
        buttonSyncDevices.setBackgroundColor(Color.TRANSPARENT);
        buttonSyncDevices.setText(R.string.sync_devices);
        buttonSyncDevices.setAllCaps(false);
        buttonSyncDevices.setTextSize(TypedValue.COMPLEX_UNIT_DIP, title_text_size_dip);
        this.addView(buttonSyncDevices);

        buttonSyncDevices.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View view) {
                if (MyDebug.LOG)
                    Log.d(TAG, "clicked to buttonSyncDevices");

                // TODO sync devices
                preview.showToast(null, "Sync devices finished"); // TODO bug in repeated clicks
            }
        });

    }

    public int getTotalWidth() {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (total_width_dp * scale + 0.5f); // convert dps to pixels;
    }
}
