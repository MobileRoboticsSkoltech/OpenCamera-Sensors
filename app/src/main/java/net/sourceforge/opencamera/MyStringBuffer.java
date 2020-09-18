package net.sourceforge.opencamera;
import android.os.AsyncTask;

import java.io.PrintStream;

public final class MyStringBuffer {

    private StringBuffer mUpdatedBuilder;
    private StringBuffer mBuilderToFile;
    private PrintStream mGyroWriter;

    private static final int mCharCount = 262144;

    public MyStringBuffer(PrintStream printStream) {
        mGyroWriter = printStream;
        mUpdatedBuilder = new StringBuffer(mCharCount);
        mBuilderToFile = null;
    }

    public void append(String str) {
        if (mUpdatedBuilder.length() < mCharCount) {
            mUpdatedBuilder.append(str);
        } else {
            mBuilderToFile = mUpdatedBuilder;
            mUpdatedBuilder = new StringBuffer(mCharCount);
            new AsyncTaskWriter().execute(mBuilderToFile);
        }
    }

    public void close() {
        mGyroWriter.append(mUpdatedBuilder);
        mGyroWriter.close();
    }

    private class AsyncTaskWriter extends AsyncTask<StringBuffer, Integer, Integer> {
        @Override
        protected Integer doInBackground(StringBuffer... stringBuilders) {
            mGyroWriter.append(stringBuilders[0]);
            return 1;
        }
    }
}