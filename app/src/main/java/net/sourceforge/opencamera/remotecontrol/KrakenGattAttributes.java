package net.sourceforge.opencamera.remotecontrol;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * This class includes the GATT attributes of the Kraken Smart Housing, which is
 * an underwater camera housing that communicates its key presses with the phone over
 * Bluetooth Low Energy
 */
class KrakenGattAttributes {
    static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    //static final UUID KRAKEN_SENSORS_SERVICE = UUID.fromString("00001623-1212-efde-1523-785feabcd123");
    static final UUID KRAKEN_SENSORS_CHARACTERISTIC = UUID.fromString("00001625-1212-efde-1523-785feabcd123");
    //static final UUID KRAKEN_BUTTONS_SERVICE= UUID.fromString("00001523-1212-efde-1523-785feabcd123");
    static final UUID KRAKEN_BUTTONS_CHARACTERISTIC= UUID.fromString("00001524-1212-efde-1523-785feabcd123");
    //static final UUID BATTERY_SERVICE = UUID.fromString("180f");
    //static final UUID BATTERY_LEVEL = UUID.fromString("2a19");

    static List<UUID> getDesiredCharacteristics() {
        return Arrays.asList(KRAKEN_BUTTONS_CHARACTERISTIC, KRAKEN_SENSORS_CHARACTERISTIC);
    }

}
