package net.sourceforge.opencamera.sensorremote;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Properties;

/**
 * Static builder class for RPC response messages
 */

public class RemoteRpcResponse {
    private String mMessage;

    private RemoteRpcResponse() {
        mMessage = "";
    }

    public String getMessage() {
        return mMessage;
    }

    @NonNull
    @Override
    public String toString() {
        return mMessage;
    }

//    public static RemoteRpcResponse error(String message, Context context) {
//        Properties config = RemoteRpcConfig.getProperties(context);
//        RemoteRpcResponse result = new RemoteRpcResponse();
//        result.mMessage = config.getProperty("ERROR") +
//                "\n" +
//                message +
//                "\n" +
//                config.getProperty("CHUNK_END_DELIMITER");
//        return result;
//    }
//
//    public static RemoteRpcResponse success(String message, Context context) {
//        Properties config = RemoteRpcConfig.getProperties(context);
//        RemoteRpcResponse result = new RemoteRpcResponse();
//        result.mMessage = config.getProperty("SUCCESS") +
//                "\n" +
//                message +
//                config.getProperty("CHUNK_END_DELIMITER");
//        return result;
//    }

    public static class Builder {
        private final String mEndDelimiter;
        private final String mSuccess;
        private final String mError;
        private final String mServerVersion;

        public Builder(Context context) {
            Properties config = RemoteRpcConfig.getProperties(context);
            mEndDelimiter = config.getProperty("CHUNK_END_DELIMITER");
            mSuccess = config.getProperty("SUCCESS");
            mError = config.getProperty("ERROR");
            mServerVersion = config.getProperty("SERVER_VERSION");
        }

        public RemoteRpcResponse error(String message, Context context) {
            RemoteRpcResponse result = new RemoteRpcResponse();
            result.mMessage = mError + "\n" +
                    mServerVersion +
                    "\n" +
                    message +
                    "\n" +
                    mEndDelimiter;
            return result;
        }

        public RemoteRpcResponse success(String message, Context context) {
            RemoteRpcResponse result = new RemoteRpcResponse();
            result.mMessage = mSuccess + "\n" +
                    mServerVersion +
                    "\n" +
                    message +
                    mEndDelimiter;
            return result;
        }
    }
}
