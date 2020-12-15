import time
from src.RemoteControl import RemoteControl

HOST = '10.30.65.125'  # The smartphone's IP address


def main():
    # example class usage
    # constructor starts the connection
    remote = RemoteControl(HOST)

    # remote.get_imu blocks until you get result (see async_imu_example for asynchronous usage in python 2)
    accel_data, gyro_data = remote.get_imu(10000, want_accel=False, want_gyro=True)
    print("Gyroscope data length: %d" % len(gyro_data))
    with open("gyro.csv", "w+") as gyro:
        gyro.writelines(gyro_data)

    # starts video and returns phase and duration in nanoseconds
    phase_ns, duration_ns = remote.start_video()
    print("Phase: %d Duration: %f" % (phase_ns, duration_ns))

    time.sleep(10)

    # stops video
    remote.stop_video()

    print('EXITED')
    remote.close()


if __name__ == '__main__':
    main()
