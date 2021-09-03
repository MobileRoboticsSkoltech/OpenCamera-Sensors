package net.sourceforge.opencamera.ui;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.R;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import androidx.annotation.StringRes;

import com.googleresearch.capturesync.SoftwareSyncController;

/**
 * This defines the UI for the "recSync" button, that provides quick access to a
 * range of options.
 * <p>
 * Requires {@link SoftwareSyncController} to be initialized (i.e. RecSync to be started)
 */
public class RecSyncView extends LinearLayout {
    private static final String TAG = "RecSyncView";
    public static final float ALPHA_BUTTON_SELECTED = 1.0f;
    public static final float ALPHA_BUTTON = 0.6f; // 0.4f tends to be hard to see in bright light

    private static final float title_text_size_dip = 17.0f;
    private static final float standard_text_size_dip = 16.0f;

    private final float scale = getResources().getDisplayMetrics().density;
    private int total_width_dp;

    private final Button button_sync_settings;
    private final Button button_align_phases;

    public RecSyncView(Context context) {
        super(context);

        if (MyDebug.LOG) {
            Log.d(TAG, "new RecSyncView: " + this);
        }

        this.setOrientation(LinearLayout.VERTICAL);

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

        button_sync_settings = addButton(R.string.sync_settings_unlocked);
        button_align_phases = addButton(R.string.align_phases);
    }

    public int getTotalWidth() {
        return (int) (total_width_dp * scale + 0.5f); // convert dps to pixels;
    }

    public void updateSyncSettingsButton(@StringRes int text) {
        button_sync_settings.setText(text);
    }

    public void setSyncSettingsOnClickListener(View.OnClickListener listener) {
        button_sync_settings.setOnClickListener(listener);
    }

    public void setAlignPhasesOnClickListener(View.OnClickListener listener) {
        button_align_phases.setOnClickListener(listener);
    }

    private CheckBox addCheckbox(Context context, @StringRes int text, boolean is_checked) {
        CheckBox check_box = new CheckBox(context);
        check_box.setText(getResources().getString(text));
        check_box.setTextSize(TypedValue.COMPLEX_UNIT_DIP, standard_text_size_dip);
        check_box.setTextColor(Color.WHITE);

        Utils.alignCheckbox(check_box, scale);

        if (is_checked) {
            check_box.setChecked(is_checked);
        }

        this.addView(check_box);

        return check_box;
    }

    private Button addButton(@StringRes int text) {
        final Button button = new Button(this.getContext());
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, title_text_size_dip);
        this.addView(button);

        return button;
    }

    static class Utils {

        /**
         * Aligns the provided checkbox a bit better.
         */
        public static void alignCheckbox(CheckBox check_box, float scale) {
            LayoutParams params = new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
            );
            final int left_padding = (int) (10 * scale + 0.5f); // convert dps to pixels
            final int right_padding = (int) (20 * scale + 0.5f); // convert dps to pixels
            params.setMargins(left_padding, 0, right_padding, 0);
            check_box.setLayoutParams(params);
        }
    }
}
