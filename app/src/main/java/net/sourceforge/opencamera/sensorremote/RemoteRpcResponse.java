package net.sourceforge.opencamera.sensorremote;

import androidx.annotation.NonNull;

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

    public static RemoteRpcResponse error(String message) {
        RemoteRpcResponse result = new RemoteRpcResponse();
        result.mMessage = ERROR +
                "\n" +
                message +
                "\n" +
                RemoteRpcServer.CHUNK_END_DELIMITER;
        return result;
    }

    public static RemoteRpcResponse success(String message) {
        RemoteRpcResponse result = new RemoteRpcResponse();
        result.mMessage = SUCCESS +
                "\n" +
                message +
                RemoteRpcServer.CHUNK_END_DELIMITER;
        return result;
    }

    public String getmMessage() {
        return mMessage;
    }

    @NonNull
    @Override
    public String toString() {
        return mMessage;
    }
}
