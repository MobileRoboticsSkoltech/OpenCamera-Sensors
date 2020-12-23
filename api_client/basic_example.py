import time
from src.RemoteControl import RemoteControl
import subprocess

HOST = '192.168.1.100'  # The smartphone's IP address


def main():
    # example class usage
    # constructor starts the connection
    remote = RemoteControl(HOST)
    print("Connected")
    
    remote.start_video()
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
