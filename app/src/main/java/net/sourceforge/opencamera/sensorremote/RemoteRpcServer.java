package net.sourceforge.opencamera.sensorremote;


import android.app.AlertDialog;
import android.content.Context;
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
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteRpcServer extends Thread {
    private static final String TAG = "RemoteRpcServer";
    private static final int SOCKET_WAIT_TIME_MS = 1000;
    private static final String IMU_REQUEST_REGEX = "(imu\\?duration=)(\\d+)(&accel=)(\\d)(&gyro=)(\\d)";

    private final ServerSocket mRpcSocket;
    private final Properties mConfig;
    private final RemoteRpcRequestHandler mRequestHandler;
    private final AtomicBoolean mIsExecuting = new AtomicBoolean();
    private final MainActivity mContext;

    public RemoteRpcServer(MainActivity context) throws IOException {
        mContext = context;
        mConfig = RemoteRpcConfig.getProperties(context);
        mRequestHandler = new RemoteRpcRequestHandler(context);
        mRpcSocket = new ServerSocket(Integer.parseInt(mConfig.getProperty("RPC_PORT")));
        mRpcSocket.setReuseAddress(true);
        mRpcSocket.setSoTimeout(SOCKET_WAIT_TIME_MS);

        if (MyDebug.LOG) {
            Log.d(TAG, "Hostname " + getIPAddress());
        }

    }

    public boolean isExecuting() {
        return mIsExecuting.get();
    }

    public void stopExecuting() {
        mIsExecuting.set(false);
    }

    private void handleRequest(String msg, PrintStream outputStream, BufferedOutputStream outputByte) {
        // IMU remote control API
        Pattern r = Pattern.compile(IMU_REQUEST_REGEX);
        Matcher imuRequestMatcher = r.matcher(msg);
        if (imuRequestMatcher.find()) {
            long duration = Long.parseLong(imuRequestMatcher.group(2));
            boolean wantAccel = Integer.parseInt(imuRequestMatcher.group(4)) == 1;
            boolean wantGyro = Integer.parseInt(imuRequestMatcher.group(6)) == 1;

            if (MyDebug.LOG) {
                Log.d(TAG, "received IMU control request, duration = " + duration);
            }
            RemoteRpcResponse imuResponse = mRequestHandler.handleImuRequest(duration, wantAccel, wantGyro);

            outputStream.println(imuResponse.toString());
            if (MyDebug.LOG) {
                Log.d(TAG, "IMU request file sent");
            }
            return;
        }

        // Video remote control API
        if (msg.equals(mConfig.getProperty("VIDEO_START_REQUEST"))) {
            outputStream.println(
                    mRequestHandler.handleVideoStartRequest()
            );
        } else if (msg.equals(mConfig.getProperty("VIDEO_STOP_REQUEST"))) {
            outputStream.println(
                    mRequestHandler.handleVideoStopRequest()
            );
        } else if (msg.equals(mConfig.getProperty("GET_VIDEO_REQUEST"))) {
            mRequestHandler.handleVideoGetRequest(outputStream);
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

        mIsExecuting.set(true);
        if (MyDebug.LOG) {
            Log.d(TAG, "waiting to accept connection from client...");
        }
        while (mIsExecuting.get()) {
            try {
                Socket clientSocket = mRpcSocket.accept();
                clientSocket.setKeepAlive(true);
                if (MyDebug.LOG) {
                    Log.d(TAG, "accepted connection from client");
                }
                InputStream inputStream = clientSocket.getInputStream();
                PrintStream outputStream = new PrintStream(clientSocket.getOutputStream());
                BufferedOutputStream outputByte = new BufferedOutputStream(clientSocket.getOutputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                String inputLine;
                while (!clientSocket.isClosed() && (inputLine = reader.readLine()) != null) {
                    // Received new request from the client
                    handleRequest(inputLine, outputStream, outputByte);
                    outputStream.flush();
                }
                if (MyDebug.LOG) {
                    Log.d(TAG, "closing connection to client");
                }
                outputByte.close();
                outputStream.close();
            } catch (SocketTimeoutException e) {
               /* if (MyDebug.LOG) {
                    Log.d(TAG, "socket timed out, waiting for new connection to client");
                }*/
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Stopped accepting requests, close the server socket
        try {
            mRpcSocket.close();
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
