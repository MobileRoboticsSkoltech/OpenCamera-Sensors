package net.sourceforge.opencamera.ui;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.R;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
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

        total_width_dp = 280;
        int max_width_dp = main_activity.getMainUI().getMaxHeightDp(false);
        if (total_width_dp > max_width_dp) {
            total_width_dp = max_width_dp;
        }
        if (MyDebug.LOG) {
            Log.d(TAG, "max_width_dp: " + max_width_dp);
            Log.d(TAG, "total_width_dp: " + total_width_dp);
        }

        LayoutInflater inflater = main_activity.getLayoutInflater();
        inflater.inflate(R.layout.recsyncview_layout, this, true);

        button_sync_settings = findViewById(R.id.button_sync_settings);
        button_align_phases = findViewById(R.id.button_align_phases);
    }

    public int getTotalWidth() {
        return (int) (total_width_dp * scale + 0.5f); // convert dps to pixels;
    }

    public void updateSyncSettingsButton(@StringRes int textId) {
        button_sync_settings.setText(textId);
    }

    public void setSyncSettingsOnClickListener(View.OnClickListener listener) {
        button_sync_settings.setOnClickListener(listener);
    }

    public void setAlignPhasesOnClickListener(View.OnClickListener listener) {
        button_align_phases.setOnClickListener(listener);
    }
}
