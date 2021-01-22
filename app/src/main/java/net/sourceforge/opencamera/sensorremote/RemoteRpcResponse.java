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

    public static RemoteRpcResponse error(String message, Context context) {
        Properties config = RemoteRpcConfig.getProperties(context);
        RemoteRpcResponse result = new RemoteRpcResponse();
        result.mMessage = config.getProperty("ERROR") +
                "\n" +
                message +
                "\n" +
                config.getProperty("CHUNK_END_DELIMITER");
        return result;
    }

    public static RemoteRpcResponse success(String message, Context context) {
        Properties config = RemoteRpcConfig.getProperties(context);
        RemoteRpcResponse result = new RemoteRpcResponse();
        result.mMessage = config.getProperty("SUCCESS") +
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
