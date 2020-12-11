package net.sourceforge.opencamera.sensorremote;

import androidx.annotation.NonNull;

/**
 * Static builder class for RPC response messages
 */
public class RemoteRpcResponse {
    private final static String SUCCESS = "SUCCESS";
    private final static String ERROR = "ERROR";

    private String message;

    private RemoteRpcResponse() {
        message = "";
    }

    public static RemoteRpcResponse error(String message) {
        RemoteRpcResponse result = new RemoteRpcResponse();
        result.message = ERROR +
                "\n" +
                message +
                "\n" +
                RemoteRpcServer.CHUNK_END_DELIMITER;
        return result;
    }

    public static RemoteRpcResponse success(String message) {
        RemoteRpcResponse result = new RemoteRpcResponse();
        result.message = SUCCESS +
                "\n" +
                message +
                "\n" +
                RemoteRpcServer.CHUNK_END_DELIMITER;
        return result;
    }

    public String getMessage() {
        return message;
    }

    @NonNull
    @Override
    public String toString() {
        return message;
    }
}
