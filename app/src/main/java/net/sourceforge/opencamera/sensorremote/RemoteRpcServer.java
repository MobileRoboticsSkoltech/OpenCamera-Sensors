package net.sourceforge.opencamera.sensorremote;


import android.util.Log;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteRpcServer extends Thread {
    private static final String TAG = "RemoteRpcServer";
    private static final int SOCKET_WAIT_TIME_MS = 1000;
    private static final int RPC_PORT = 6969;
    private static final String IMU_REQUEST_REGEX = "(imu\\?duration=)(\\d+)";
    private static final String VIDEO_START_REQUEST = "video_start";
    private static final String VIDEO_STOP_REQUEST = "video_stop";

    private final ServerSocket rpcSocket;
    private final RequestHandler mRequestHandler;
    private final AtomicBoolean mIsExecuting = new AtomicBoolean();

    public RemoteRpcServer(MainActivity context) throws IOException {
        mRequestHandler = new RequestHandler(context);

        rpcSocket = new ServerSocket(RPC_PORT);
        rpcSocket.setReuseAddress(true);
        rpcSocket.setSoTimeout(SOCKET_WAIT_TIME_MS);
    }

    public boolean isExecuting() {
        return mIsExecuting.get();
    }

    public void stopExecuting() {
        mIsExecuting.set(false);
    }

    private void handleRequest(String msg, PrintWriter outputStream) throws IOException {
        // IMU remote control API
        Pattern r = Pattern.compile(IMU_REQUEST_REGEX);
        Matcher imuRequestMatcher = r.matcher(msg);
        if (imuRequestMatcher.find()) {
            long duration = Long.parseLong(imuRequestMatcher.group(2));

            if (MyDebug.LOG) {
                Log.d(TAG, "received IMU control request, duration = " + duration);
            }
            File imuFile = mRequestHandler.handleImuRequest(duration);
            outputStream.println(imuFile.getName());
            try (BufferedReader br = new BufferedReader(new FileReader(imuFile))) {
                for (String line; (line = br.readLine()) != null; ) {
                    outputStream.println(line);
                }
            }
            if (MyDebug.LOG) {
                Log.d(TAG, "IMU request file sent");
            }
            return;
        }

        // Video remote control API
        if (msg.equals(VIDEO_START_REQUEST)) {
            mRequestHandler.handleVideoStartRequest();
        } else if (msg.equals(VIDEO_STOP_REQUEST)) {
            mRequestHandler.handleVideoStopRequest();
        }
    }

    @Override
    public void run() {
        mIsExecuting.set(true);
        if (MyDebug.LOG) {
            Log.d(TAG, "waiting to accept connection from client...");
        }
        while (mIsExecuting.get()) {
            try {
                Socket clientSocket = rpcSocket.accept();
                if (MyDebug.LOG) {
                    Log.d(TAG, "accepted connection from client");
                }
                InputStream inputStream = clientSocket.getInputStream();
                PrintWriter outputStream = new PrintWriter(clientSocket.getOutputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                String inputLine;
                while (!clientSocket.isClosed() && (inputLine = reader.readLine()) != null) {
                    // Received new request from the client
                    handleRequest(inputLine, outputStream);
                }
                if (MyDebug.LOG) {
                    Log.d(TAG, "closing connection to client");
                }
                outputStream.flush();
                outputStream.close();
            } catch (SocketTimeoutException e) {
                if (MyDebug.LOG) {
                    Log.d(TAG, "socket timed out, waiting for new connection to client");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Stopped accepting requests, close the server socket
        try {
            rpcSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
