package net.sourceforge.opencamera.sensorremote;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class RemoteRpcConfig {
    public static Properties getProperties(Context context) {
        try {
            Properties serverProperties = new Properties();
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open("server_config.properties");
            serverProperties.load(inputStream);
            return serverProperties;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Couldn't load server properties from the config");
        }
    }
}
