package net.sourceforge.opencamera.sensorremote;

import java.io.IOException;
import fi.iki.elonen.NanoHTTPD;


public class ControlServer extends NanoHTTPD {
    public ControlServer() throws IOException {
        super(8080);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    @Override
    public Response serve(IHTTPSession session) {
        return newFixedLengthResponse("Hello world!");
    }
}
