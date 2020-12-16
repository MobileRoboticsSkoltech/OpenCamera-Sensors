import time
from src.RemoteControl import RemoteControl
from concurrent.futures import ThreadPoolExecutor
import subprocess
import rospy
from sensor_msgs.msg import Imu
import numpy as np
import pandas as pd
from io import StringIO
from src.TimeSync import TimeSync2

HOST = '10.30.65.125'  # The smartphone's IP address

mcu_imu_time = []
mcu_imu_data = []


def callback(data):
    dat = data.header.stamp.secs + data.header.stamp.nsecs  / 1e9
    mcu_imu_time.append(dat)

    dat = data.angular_velocity
    mcu_imu_data.append([dat.x, dat.y, dat.z])

def listener():
    rospy.init_node('listener', anonymous=True)
    rospy.Subscriber("mcu_imu", Imu, callback)
    #rospy.spin()

def main():
    remote = RemoteControl(HOST)
    print('start shaking')
    listener()

    duration = 3


    with ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(remote.get_imu, 1000 * (duration + 4), False, True)
        accel_data, gyro_data = future.result()

        time.sleep(duration)

        print('put back')

        rospy.signal_shutdown('it is enough')

    mcu_gyro_data = np.array(mcu_imu_data) - np.array(mcu_imu_data)[:200].mean(axis=0)
    mcu_gyro_time = np.array(mcu_imu_time)

    sm_df = pd.read_csv(StringIO(unicode(gyro_data)), header=None, index_col=False)
    sm_gyro_data = sm_df.iloc[:, :3].to_numpy()
    sm_gyro_time = sm_df.iloc[:, 3].to_numpy() / 1e9
    
    print(mcu_gyro_data.shape, sm_gyro_data.shape, mcu_gyro_time.shape, sm_gyro_time.shape)

    min_length = min(sm_gyro_time.shape[0], mcu_gyro_time.shape[0])


    mcu_gyro_data, mcu_gyro_time, sm_gyro_data, sm_gyro_time = \
    mcu_gyro_data[:min_length], mcu_gyro_time[:min_length], sm_gyro_data[:min_length], sm_gyro_time[:min_length]

    time_sync2 = TimeSync2(
        mcu_gyro_data, sm_gyro_data, mcu_gyro_time, sm_gyro_time, False
    )
    time_sync2.resample(accuracy=1)
    time_sync2.obtain_delay()
    comp_delay2 = time_sync2.time_delay

    resulting_offset_s = (np.mean(sm_gyro_time - mcu_gyro_time) + comp_delay2)    #resulting_offset_s = (sm_gyro_time[0] - mcu_gyro_time[0] + comp_delay2)
    resulting_offset_ns = resulting_offset_s * 1e9

    phase_ns, duration_ns = remote.start_video()
    phase = (phase_ns / 1e9 - resulting_offset_s)

    print (phase, resulting_offset_s)
    with open(time.strftime("%b_%d_%Y_%H_%M_%S") + ".txt", "w+") as out:
            out.writelines('phase,resulting_offset_s\n'+str(phase) + ',' + str(resulting_offset_s)
    )
    #print(resulting_offset_s, sm_gyro_time[0] - mcu_gyro_time[0], mcu_gyro_data.shape, sm_gyro_data.shape, mcu_gyro_time.shape, sm_gyro_time.shape)

    bashCommand = "rosrun mcu_interface align_mcu_cam_phase_client " + str(phase)
    process = subprocess.Popen(bashCommand.split(), stdout=subprocess.PIPE)
    output, error = process.communicate()
    
    time.sleep(3)

    remote.stop_video()
    #print('EXITED')
    remote.close()
    

if __name__ == '__main__':
    main()
