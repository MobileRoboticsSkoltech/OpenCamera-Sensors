import time
from src.RemoteControl import RemoteControl
from concurrent.futures import ThreadPoolExecutor

HOST = '10.30.65.125'  # The smartphone's IP address


def main():
    remote = RemoteControl(HOST)

    with ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(remote.get_imu, 1000, False, True)
        # Do something else
        print("doing other stuff...")
        time.sleep(1)
        print("done doing other stuff")
        # Get result when needed
        accel_data, gyro_data = future.result()
        # Process result somehow (here just file output)
        print("Gyroscope data length: %d" % len(gyro_data))
        with open("gyro.csv", "w+") as gyro:
            gyro.writelines(gyro_data)

    print('EXITED')
    remote.close()


if __name__ == '__main__':
    main()
