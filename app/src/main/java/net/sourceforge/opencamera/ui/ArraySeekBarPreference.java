package net.sourceforge.opencamera.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import net.sourceforge.opencamera.R;

/** This contains a custom preference to display a seekbar in place of a ListPreference.
 */
public class ArraySeekBarPreference extends DialogPreference {
    //private static final String TAG = "ArraySeekBarPreference";

    private SeekBar seekbar;
    private TextView textView;

    private CharSequence [] entries; // user readable strings
    private CharSequence [] values; // values corresponding to each string

    private final String default_value;
    private String value; // current saved value of this preference (note that this is intentionally not updated when the seekbar changes, as we don't save until the user clicks ok)
    private boolean value_set;

    public ArraySeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        String namespace = "http://schemas.android.com/apk/res/android";
        this.default_value = attrs.getAttributeValue(namespace, "defaultValue");

        int entries_id = attrs.getAttributeResourceValue(namespace, "entries", 0);
        if( entries_id > 0 )
            this.setEntries(entries_id);
        int values_id = attrs.getAttributeResourceValue(namespace, "entryValues", 0);
        if( values_id > 0 )
            this.setEntryValues(values_id);

        setDialogLayoutResource(R.layout.arrayseekbarpreference);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        if( entries == null || values == null ) {
            throw new IllegalStateException("ArraySeekBarPreference requires entries and entryValues array");
        }
        else if( entries.length != values.length ) {
            throw new IllegalStateException("ArraySeekBarPreference requires entries and entryValues arrays of same length");
        }

        this.seekbar = view.findViewById(R.id.arrayseekbarpreference_seekbar);
        this.textView = view.findViewById(R.id.arrayseekbarpreference_value);

        seekbar.setMax(entries.length-1);
        {
            int index = getValueIndex();
            if( index == -1 ) {
                // If we're here, it means the stored value isn't in the values array.
                // ListPreference just shows a dialog with no selected entry, but that doesn't really work for
                // a seekbar that needs to show the current position! So instead, set the position to the default.
                if( default_value != null && values != null ) {
                    for(int i = values.length - 1; i >= 0; i--) {
                        if( values[i].equals(default_value) ) {
                            index = i;
                            break;
                        }
                    }
                }
            }
            if( index >= 0 )
                seekbar.setProgress(index);
        }
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                String new_entry = entries[progress].toString();
                textView.setText(new_entry);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        String new_entry = entries[seekbar.getProgress()].toString();
        textView.setText(new_entry);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if( positiveResult && values != null ) {
            int progress = seekbar.getProgress();
            String new_value = values[progress].toString();
            if( callChangeListener(new_value) ) {
                setValue(new_value);
            }
        }
    }

    public void setEntries(CharSequence[] entries) {
        this.entries = entries;
    }

    private void setEntries(int entries) {
        setEntries(getContext().getResources().getTextArray(entries));
    }

    public void setEntryValues(CharSequence[] values) {
        this.values = values;
    }

    private void setEntryValues(int values) {
        setEntryValues(getContext().getResources().getTextArray(values));
    }

    @Override
    public CharSequence getSummary() {
        CharSequence summary = super.getSummary();
        if( summary != null ) {
            CharSequence entry = getEntry();
            return String.format(summary.toString(), entry == null ? "" : entry);
        }
        else
            return null;
    }

    /** Returns the index of the current value in the values array, or -1 if not found.
     */
    private int getValueIndex() {
        if( value != null && values != null ) {
            // go backwards for compatibility with ListPreference in cases with duplicate values
            for(int i = values.length - 1; i >= 0; i--) {
                if( values[i].equals(value) ) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Returns the human readable string of the current value.
     */
    private CharSequence getEntry() {
        int index = getValueIndex();
        return index >= 0 && entries != null ? entries[index] : null;
    }

    private void setValue(String value) {
        final boolean changed = !TextUtils.equals(this.value, value);
        if( changed || !value_set ) {
            this.value = value;
            value_set = true;
            persistString(value);
            if( changed ) {
                notifyChanged();
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedString(value) : (String) defaultValue);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if( isPersistent() ) {
            return superState;
        }

        final SavedState state = new SavedState(superState);
        state.value = value;
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if( state == null || !state.getClass().equals(SavedState.class) ) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState)state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
    }

    private static class SavedState extends BaseSavedState {
        String value;

        SavedState(Parcel source) {
            super(source);
            value = source.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
        }

        SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
