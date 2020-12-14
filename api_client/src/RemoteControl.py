import socket
import csv

PORT = 6969  # The port used by the OpenCamera Sensors
END_MARKER = 'end'
SENSOR_END_MARKER = 'sensor_end'
SUCCESS = 'SUCCESS'
ERROR = 'ERROR'


class RemoteControl:
    """
    Provides communication methods with the smartphone
    running OpenCamera Sensors application
    """

    def __init__(self, hostname):
        """
        Args:
            hostname (str): Smartphones hostname (IP address) in the current network.
            Is displayed in the dialog when starting OpenCamera Sensors on the smartphone.
        """
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.socket.connect((hostname, PORT))

    def get_imu(self, duration_ms, want_accel, want_gyro):
        """
        Request IMU data recording
        :param duration_ms: (int) duration in milliseconds
        :param want_accel: (boolean) request accelerometer recording
        :param want_gyro: (boolean) request gyroscope recording
        :return: Tuple (accel_data, gyro_data) - csv data strings
        If one of the sensors wasn't requested, the corresponding data is None
        """
        accel = int(want_accel)
        gyro = int(want_gyro)
        status, socket_file = self._send_and_get_response_status(
            'imu?duration=%d&accel=%d&gyro=%d\n' % (duration_ms, accel, gyro)
        )
        accel_data = None
        gyro_data = None

        for i in range(2):
            # read filename or end marker
            line = socket_file.readline()
            msg = line.strip('\n')
            if msg == END_MARKER:
                break
            data = ""
            # accept file contents
            line = socket_file.readline()
            while line.strip("\n") != SENSOR_END_MARKER:
                # print('Received: %s' % (line.strip('\n')))
                data += line
                line = socket_file.readline()

            # TODO: refactor these hardcoded checks
            if msg.endswith("accel.csv"):
                accel_data = data
            elif msg.endswith("gyro.csv"):
                gyro_data = data
        socket_file.close()
        return accel_data, gyro_data

    def start_video(self):
        """
        Starts video recording and receives phase and duration info
        :return: Tuple (phase, average duration) - all in nanoseconds
        """
        status, socket_file = self._send_and_get_response_status("video_start")
        # print(status)

        line = socket_file.readline()
        phase_ns = long(line)

        line = socket_file.readline()
        avg_duration_ns = float(line)
        return phase_ns, avg_duration_ns

    def stop_video(self):
        """
        Stops video recording
        """

        # receive response
        status, socket_file = self._send_and_get_response_status("video_stop")
        # print(status)

        line = socket_file.readline()
        while line.strip('\n') != END_MARKER:
            # print(line)
            line = socket_file.readline()

    def _send_and_get_response_status(self, msg):
        # open socket as a file
        socket_file = self.socket.makefile()
        # send request message
        socket_file.write(
            msg + '\n'
        )
        socket_file.flush()
        # receive response
        status = socket_file.readline()
        if status.strip('\n') == ERROR:
            msg = socket_file.readline()
            socket_file.close()
            raise RuntimeError(msg)

        return status.strip('\n') == SUCCESS, socket_file

    def close(self):
        self.socket.close()
