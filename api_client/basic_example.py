import time
from src.RemoteControl import RemoteControl
import subprocess

HOST = '192.168.1.100'  # The smartphone's IP address


def main():
    # example class usage
    # constructor starts the connection
    remote = RemoteControl(HOST)
    print("Connected")
    
    accel_data, gyro_data = remote.get_imu(10000, True, False)
    print("Accelerometer data length: %d" % len(accel_data))
    with open("accel.csv", "w+") as accel:
        accel.writelines(accel_data)
    
    phase, duration = remote.start_video()
    print("%d %f" % (phase, duration))
    time.sleep(5)
    remote.stop_video()
    
    # receives last video (blocks until received)
    start = time.time()
    filename = remote.get_video(want_progress_bar=True)
    end = time.time()
    print("elapsed: %f" % (end - start))
    print('Closing connection')
    remote.close()


if __name__ == '__main__':
    main()
