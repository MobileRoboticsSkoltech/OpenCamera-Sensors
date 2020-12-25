![OpenCamera Sensors logo](https://imgur.com/NnS1NW5.png)

[![Build Status](https://travis-ci.org/MobileRoboticsSkoltech/OpenCamera-Sensors.svg?branch=master)](https://travis-ci.org/MobileRoboticsSkoltech/OpenCamera-Sensors)

OpenCamera Sensors is an Android application for synchronized recording of video and IMU data. It records IMU data and video with frame timestamps synced to the same clock.


This project is based on [Open Camera](https://opencamera.org.uk/)  —  a popular open-source camera application with flexibility in camera parameters settings, actively supported by the community. By regular merging of Open Camera updates our app will adapt to new smartphones and APIs — this is an advantage over the other video + IMU recording applications built from scratch for Camera2API.


## Usage

![screenshot settings](https://imgur.com/BytzCvA.png)

- Go to preferences, enable Camera2API and press the “Enable sync video IMU recording” switch
- (Optional) **Disable video stabilization** in video preferences of OpenCamera Sensors to minimize preprocessing effects
- (Optional) Enable **save frames** option if you want to verify recorded data correctness
- **Switch to video**, setup ISO and exposure time
- **Record video**
- **Get data** from ```DCIM/OpenCamera```:
    - Video file
    - IMU data and frame timestamps in the directory ```{VIDEO_DATE}```:
        -```{VIDEO_NAME}_gyro.csv```, data format: ```X-data, Y-data, Z-data, timestamp (ns)```
        - ```{VIDEO_NAME}_accel.csv```, data format: ```X-data, Y-data, Z-data, timestamp (ns)```
        - ```{VIDEO_NAME}_timestamps.csv```, data format: ```timestamp (ns)``` 

### Remote recording

- **Connect** smartphone to the same network as PC
- Use scripts provided in ```./api_client/``` directory to **send requests** for the application

## Restrictions

One important restriction is that our app requires full Camera2API support.

Another restriction of our application is that synchronized timestamping for camera and IMU data isn’t available on all the devices with Camera2API support.

You can check whether your device supports this feature in preferences


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
