[![Build Status](https://travis-ci.org/MobileRoboticsSkoltech/OpenCamera-Sensors.svg?branch=master)](https://travis-ci.org/MobileRoboticsSkoltech/OpenCamera-Sensors)

This is an experimental branch to analyze the delays between capture request time and resulting image timestamp

## Experimental usage

- Configure number of photos for experiment and setup time between captures in ```ConfigExpCaptureTime``` class (5 captures with 1000 ms delay enabled by default)
- Press the "take photo" button
- Check logs by tag ```EXP_CAPTURE_TIME```
