# Open Camera Extensions
[![Build Status](https://travis-ci.org/azaat/opencamera-extensions.svg?branch=master)](https://travis-ci.org/azaat/opencamera-extensions)

**This repository is created to extend OpenCamera application with the following capabilities:**

- Support asynchronous IMU recording, provide settings for different IMU frequencies
- Menu with some important smartphone properties
  - preprocessing options (optical/digital video stabilization)
  - whether or not IMU and camera be synchronized
- Provide recording of smartphone model and build number
- Support remote recording over the network (Open Camera Remote can be used as basis)

**How to enable sensor data recording?**

Go to OpenCamera preferences and press the ```Enable IMU recording``` switch.

**How to find recorded IMU data?**

All videos recorded by the camera are located in OpenCamera folder on the disk (usually in DCIM).

Additional information is stored in the folder ```OpenCamera_sensor_info```. Here you will find folders that correspond to the recorded videos. They are named by the date and time of recording to easily find what you need.
