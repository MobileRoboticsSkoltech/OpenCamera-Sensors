package net.sourceforge.opencamera.sensorremote;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Properties;

/**
 * Static builder class for RPC response messages
 */
public class RemoteRpcResponse {
    private final static String SUCCESS = "SUCCESS";
    private final static String ERROR = "ERROR";

    private String mMessage;

    private RemoteRpcResponse() {
        mMessage = "";
    }

    public static RemoteRpcResponse error(String message, Context context) {
        Properties config = RemoteRpcConfig.getProperties(context);
        RemoteRpcResponse result = new RemoteRpcResponse();
        result.mMessage = ERROR +
                "\n" +
                message +
                "\n" +
                config.getProperty("CHUNK_END_DELIMITER");
        return result;
    }

    public static RemoteRpcResponse success(String message, Context context) {
        Properties config = RemoteRpcConfig.getProperties(context);
        RemoteRpcResponse result = new RemoteRpcResponse();
        result.mMessage = SUCCESS +
                "\n" +
                message +
                config.getProperty("CHUNK_END_DELIMITER");
        return result;
    }

    public String getMessage() {
        return mMessage;
    }

    @NonNull
    @Override
    public String toString() {
        return mMessage;
    }
}
