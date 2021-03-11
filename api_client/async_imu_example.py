import time
from src.RemoteControl import RemoteControl
from concurrent.futures import ThreadPoolExecutor

HOST = '192.168.1.100'  # The smartphone's IP address


def main():
    remote = RemoteControl(HOST)

    with ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(remote.get_imu, 10000, True, False)
        # Do something else
        print("doing other stuff...")
        time.sleep(10)
        print("done doing other stuff")
        # Get result when needed
        accel_data, gyro_data = future.result()
        # Process result somehow (here just file output)
        print("Accelerometer data length: %d" % len(accel_data))
        with open("accel.csv", "w+") as accel:
            accel.writelines(accel_data)

    print('EXITED')
    remote.close()


if __name__ == '__main__':
    main()

