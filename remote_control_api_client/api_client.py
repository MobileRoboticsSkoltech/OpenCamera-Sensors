# OpenCamera Sensors remote control Python API
import time
import asyncio

HOST = '192.168.1.48'  # The smartphone's IP address
PORT = 6969             # The port used by the OpenCamera Sensors
END_MARKER = 'end'
SENSOR_END_MARKER = 'sensor_end'
SUCCESS = 'SUCCESS'
ERROR = 'ERROR'

async def tcp_client_imu():
    reader, writer = await asyncio.open_connection(HOST, PORT)

    # sample IMU message to record only gyroscope, duration in milliseconds
    message = 'imu?duration=1000&accel=0&gyro=1\n'
    print(f'Send: {message!r}')
    writer.write(message.encode())
    await writer.drain()
    status = await reader.readline()
    
    # check response status
    if status.decode() == ERROR:
        msg = await reader.readline()
        print(msg.decode().strip('\n'))
        print('Close the connection')
        writer.close()  
        await writer.wait_closed()
        return;
    
    # receive imu file (or files)
    for i in range(2):
        line = await reader.readline()
        msg = line.decode().strip('\n')
        if (msg == END_MARKER):
            break;
        
        with open(f"{msg}", "w+") as imu_file:
            line = await reader.readline()
            while line.decode().strip("\n") != SENSOR_END_MARKER:
                print(f'Received: {line.decode()!r}')
                imu_file.write(line.decode())
                line = await reader.readline()
    print('Close the connection')
    writer.close()  
    await writer.wait_closed()

asyncio.run(tcp_client_imu())
# time.sleep(10)

