package net.sourceforge.opencamera.sensorremote;


import android.app.AlertDialog;
import android.util.Log;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenCamera Sensors server v. 0.1.2
 *
 * Accepted message types:
 *  - get IMU (accelerometer/gyroscope)
 *  - start/stop video
 * Response structure:
 *  - 1st line: SUCCESS/ERROR message
 *  - 2nd line: version string
 *  - the rest: request response
 */
public class RemoteRpcServer extends Thread {
    private static final String TAG = "RemoteRpcServer";
    private static final int SOCKET_WAIT_TIME_MS = 1000;
    private static final String IMU_REQUEST_REGEX =
            "(imu\\?duration=)(\\d+)(&accel=)(\\d)(&gyro=)(\\d)(&magnetic=)(\\d)(&gravity=)(\\d)(&rotation=)(\\d)";
    private static final Pattern IMU_REQUEST_PATTERN = Pattern.compile(IMU_REQUEST_REGEX);

    private final Properties mConfig;
    private final RemoteRpcRequestHandler mRequestHandler;
    private volatile boolean mIsExecuting;
    private final MainActivity mContext;

    public RemoteRpcServer(MainActivity context) throws IOException {
        mContext = context;
        mConfig = RemoteRpcConfig.getProperties(context);
        mRequestHandler = new RemoteRpcRequestHandler(context);

        if (MyDebug.LOG) {
            Log.d(TAG, "Hostname " + getIPAddress());
        }

    }

    public boolean isExecuting() {
        return mIsExecuting;
    }

    /**
     * Safe to call even when not executing
     */
    public void stopExecuting() {
        mIsExecuting = false;
    }

    private void handleRequest(String msg, PrintStream outputStream, BufferedOutputStream outputByte) {
        // IMU remote control API
        Matcher imuRequestMatcher = IMU_REQUEST_PATTERN.matcher(msg);
        if (imuRequestMatcher.find()) {
            long duration = Long.parseLong(imuRequestMatcher.group(2));
            boolean wantAccel = Integer.parseInt(imuRequestMatcher.group(4)) == 1;
            boolean wantGyro = Integer.parseInt(imuRequestMatcher.group(6)) == 1;
            boolean wantMagnetic = Integer.parseInt(imuRequestMatcher.group(8)) == 1;
            boolean wantGravity = Integer.parseInt(imuRequestMatcher.group(10)) == 1;
            boolean wantRotation = Integer.parseInt(imuRequestMatcher.group(12)) == 1;


            if (MyDebug.LOG) {
                Log.d(TAG, "received IMU control request, duration = " + duration);
            }
            RemoteRpcResponse imuResponse = mRequestHandler.handleImuRequest(
                    duration, wantAccel, wantGyro, wantMagnetic, wantGravity, wantRotation
            );

            outputStream.println(imuResponse.toString());
            if (MyDebug.LOG) {
                Log.d(TAG, "IMU request file sent");
            }
        } else if (msg.equals(mConfig.getProperty("VIDEO_START_REQUEST"))) {
            outputStream.println(
                    mRequestHandler.handleVideoStartRequest()
            );
        } else if (msg.equals(mConfig.getProperty("VIDEO_STOP_REQUEST"))) {
            outputStream.println(
                    mRequestHandler.handleVideoStopRequest()
            );
        } else if (msg.equals(mConfig.getProperty("GET_VIDEO_REQUEST"))) {
            mRequestHandler.handleVideoGetRequest(outputStream);
        } else {
            outputStream.println(
                mRequestHandler.handleInvalidRequest()
            );
        }
    }

    @Override
    public void run() {
        // TODO: report hostname some other way
        mContext.runOnUiThread(
                () -> {
                    try {
                        new AlertDialog.Builder(mContext)
                                .setTitle("Smartphone hostname")
                                .setMessage("Hostname: " + getIPAddress())

                                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                })
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .show();
                    } catch (SocketException e) {
                        e.printStackTrace();
                    }

                }
        );

        mIsExecuting = true;
        if (MyDebug.LOG) {
            Log.d(TAG, "waiting to accept connection from client...");
        }
            try (
                    ServerSocket rpcSocket = new ServerSocket(Integer.parseInt(mConfig.getProperty("RPC_PORT")))
            ) {
                rpcSocket.setReuseAddress(true);
                rpcSocket.setSoTimeout(SOCKET_WAIT_TIME_MS);
                while (mIsExecuting) {
                    try (
                            Socket clientSocket = rpcSocket.accept()
                    ) {
                        clientSocket.setKeepAlive(true);
                        if (MyDebug.LOG) {
                            Log.d(TAG, "accepted connection from client");
                        }
                        try (
                                InputStream inputStream = clientSocket.getInputStream();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                                PrintStream outputStream = new PrintStream(clientSocket.getOutputStream());
                                BufferedOutputStream outputByte = new BufferedOutputStream(clientSocket.getOutputStream())
                        ) {

                            String inputLine;
                            while (mIsExecuting && !clientSocket.isClosed() && (inputLine = reader.readLine()) != null) {
                                // Received new request from the client
                                handleRequest(inputLine, outputStream, outputByte);
                                outputStream.flush();
                            }
                        }
                        if (MyDebug.LOG) {
                            Log.d(TAG, "closing connection to client");
                        }

                    } catch (IOException e) {
                   /* if (MyDebug.LOG) {
                        Log.d(TAG, "socket timed out, waiting for new connection to client");
                    }*/
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    /**
     * Finds this devices's IPv4 address that is not localhost and not on a dummy interface.
     *
     * @return the String IP address on success.
     * @throws SocketException on failure to find a suitable IP address.
     */
    public static InetAddress getIPAddress() throws SocketException {
        List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        for (NetworkInterface intf : interfaces) {
            for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                if (!addr.isLoopbackAddress()
                        && !intf.getName().equals("dummy0")
                        && addr instanceof Inet4Address) {
                    return addr;
                }
            }
        }
        throw new SocketException("No viable IP Network addresses found.");
    }
}
