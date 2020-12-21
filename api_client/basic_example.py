import time
from src.RemoteControl import RemoteControl
import subprocess

HOST = '10.30.65.125'  # The smartphone's IP address


def main():
    # example class usage
    # constructor starts the connection
    remote = RemoteControl(HOST)

    # remote.get_imu blocks until you get result (see async_imu_example for asynchronous usage in python 2)
    accel_data, gyro_data = remote.get_imu(1000, want_accel=False, want_gyro=True)
    print("Gyroscope data length: %d" % len(gyro_data))
    with open("gyro.csv", "w+") as gyro:
        gyro.writelines(gyro_data)

    # starts video and returns phase and duration in nanoseconds
    phase_ns, duration_ns = remote.start_video()
    
    bashCommand = "rosrun mcu_interface align_mcu_cam_phase_client " + str(500)
    process = subprocess.Popen(bashCommand.split(), stdout=subprocess.PIPE)
    output, error = process.communicate()

    print("Phase: %d Duration: %f" % (phase_ns, duration_ns))

    time.sleep(1)

    # stops video
    remote.stop_video()

    # receives last video (blocks until received)
    filename = remote.get_video(want_progress_bar=True)
    print("filename: %s" % filename)
    print('EXITED')
    remote.close()


if __name__ == '__main__':
    main()
