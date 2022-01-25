![OpenCamera Sensors logo](https://imgur.com/7qjCtgp.png)

[![Build Status](https://travis-ci.org/MobileRoboticsSkoltech/OpenCamera-Sensors.svg?branch=master)](https://travis-ci.org/MobileRoboticsSkoltech/OpenCamera-Sensors)

OpenCamera Sensors is an Android application for synchronized recording of video and IMU data. It records sensor data (accelerometer, gyroscope, magnetometer) and video with frame timestamps synced to the same clock.

## Install

[Get latest apk from GH releases](https://github.com/MobileRoboticsSkoltech/OpenCamera-Sensors/releases/latest/download/app-release.apk)

<center><a href="https://apt.izzysoft.de/fdroid/index/apk/com.opencamera_extended.app"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" width="170"></a></center>

## Description

OpenCamera Sensors is an Android application for synchronized recording of video and IMU data. It records IMU data and video with frame timestamps synced to the same clock.

This project is based on [Open Camera](https://opencamera.org.uk/)  —  a popular open-source camera application with flexibility in camera parameters settings, actively supported by the community. By regular merging of Open Camera updates our app will adapt to new smartphones and APIs — this is an advantage over the other video + IMU recording applications built from scratch for Camera2API.

## Usage

![screenshot settings](https://imgur.com/BytzCvA.png)

- Go to preferences, enable Camera2API and press the “Enable sync video IMU recording” switch
- (Optional) **Disable video stabilization** in video preferences of OpenCamera Sensors to minimize preprocessing effects
- (Optional) Enable **save frames** option if you want to verify recorded data correctness
- (Optional) Enable flash strobe and specify its frequency in additional sensor settings
- **Switch to video**, setup ISO and exposure time
- **Record video**
- **Get data** from ```DCIM/OpenCamera```:
    - Video file
    - Sensor data and frame timestamps in the directory ```{VIDEO_DATE}```:
        -```{VIDEO_NAME}_gyro.csv```, data format: ```X-data, Y-data, Z-data, timestamp (ns)```
        - ```{VIDEO_NAME}_accel.csv```, data format: ```X-data, Y-data, Z-data, timestamp (ns)```
        - ```{VIDEO_NAME}_magnetic.csv```, data format: ```X-data, Y-data, Z-data, timestamp (ns)```
        - ```{VIDEO_NAME}_location```, data format: ```latitude, lognitude, altitude, timestamp (ns)```
        - ```{VIDEO_NAME}_rotation```, data format: ```azimuth, pitch, roll, timestamp (ns)```
        - ```{VIDEO_NAME}_gravity```, data format: ```X-data, Y-data, Z-data, timestamp (ns)```
        - ```{VIDEO_NAME}_timestamps.csv```, data format: ```timestamp (ns)```
        - ```{VIDEO_NAME}_flash.csv```, data format: ```timestamp (ns)``` (timestamps of when the flash fired)

### Remote recording

- **Connect** smartphone to the same network as PC
- Use scripts provided in ```./api_client/``` directory to **send requests** for the application.
    - *Note: phase, which is returned by* ```start_recording``` *method, can be used to perform synchronization with external devices*
 ![remote control methods](https://www.websequencediagrams.com/files/render?link=6txhpHrdgaebT4DYz2C3SaEQjHM1esYDkJZJvPZcgCJHbRAg3c8hqcJYgOmGirze)

## Good practices for data recording

- When recording video with audio recording enabled, MediaRecorder adds extra frames to the video to match the sound.
Due to this problem, the **audio recording** feature **is disabled** in our app by default.

- To minimize the amount of preprocessing done by the smartphone, we also disable **video stabilization** and **OIS** options.

## Restrictions

One important restriction is that our app requires full Camera2API support.

Another restriction of our application is that synchronized timestamping for camera and IMU data isn’t available on all the devices with Camera2API support.
You can check whether your device supports this feature in preferences.

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
