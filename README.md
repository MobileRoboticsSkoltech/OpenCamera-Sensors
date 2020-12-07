![OpenCamera Sensors logo](https://imgur.com/NnS1NW5.png)

[![Build Status](https://travis-ci.org/MobileRoboticsSkoltech/OpenCamera-Sensors.svg?branch=master)](https://travis-ci.org/MobileRoboticsSkoltech/OpenCamera-Sensors)


[Open Camera](https://opencamera.org.uk/) is an opensource camera application with many advanced capture options. We modify it to support synchronized recording of video and IMU data, which can be useful in scientific applications (e.g. to record data for experiments).

## Our contributions to the app:

- Support IMU sensor data recording, provide settings for different IMU frequencies
- Support extended video recording with frame timestamps (frame timestamps can be used together with sensor timestamps if your device has SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME in CameraMetadata).

## How to enable synchronized video and IMU recording?

![screenshot settings](https://imgur.com/BytzCvA.png)

Firstly, make sure Camera2API is used for capture (this can be checked in application preferences).

After that, press the ```Enable IMU recording``` switch in preferences. Additionally, you can specify IMU frquency by choosing a Hz value from the list, but this number is only a hint to the system according to the android documentation.

## How to find recorded IMU and video data?

All videos recorded by the camera are located in OpenCamera folder on the disk (usually in DCIM), additional info can be found in subdirectories with video date as a name. 

Data saved in the subdirectory ```./<currentVideoDate>```:

- ```<video_name>_gyro.csv```, data format: ```X-data, Y-data, Z-data, timestamp (ns)```
- ```<video_name>_accel.csv```, data format: ```X-data, Y-data, Z-data, timestamp (ns)```
- ```<video_name>_timestamps.csv```, data format: ```timestamp (ns)```

## Contribution

The project follows [AOSP Java Code Style](https://source.android.com/setup/contribute/code-style), main principles:

- Non-public fields should start with ```m```, constants are ```ALL_CAPS_UNDERSCORES``` 
- Standard brace style:
```java
if () {
    //...
} else {
    //...
}
```
- Limit line length
