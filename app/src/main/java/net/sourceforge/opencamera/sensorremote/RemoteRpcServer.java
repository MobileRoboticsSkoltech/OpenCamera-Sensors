package net.sourceforge.opencamera.sensorremote;


import android.util.Log;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteRpcServer extends Thread {
    private static final String TAG = "RemoteRpcServer";
    private static final int SOCKET_WAIT_TIME_MS = 1000;
    private static final int RPC_PORT = 6969;
    private static final String IMU_REQUEST_REGEX = "(imu\\?duration=)(\\d+)(&accel=)(\\d)(&gyro=)(\\d)";
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

    private void handleRequest(String msg, PrintWriter outputStream) {
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
            outputStream.println(imuResponse.getMessage());
            if (MyDebug.LOG) {
                Log.d(TAG, "IMU request file sent");
            }
            return;
        }

        // Video remote control API
        if (msg.equals(VIDEO_START_REQUEST)) {
            outputStream.println(
                    mRequestHandler.handleVideoStartRequest()
            );
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
                clientSocket.setKeepAlive(true);
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
               /* if (MyDebug.LOG) {
                    Log.d(TAG, "socket timed out, waiting for new connection to client");
                }*/
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
