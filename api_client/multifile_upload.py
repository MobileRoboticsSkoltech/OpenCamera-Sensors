import time
from src.RemoteControl import RemoteControl

HOST = '192.168.1.75'  # The smartphone's IP address

def main():
    # example class usage for upload files
    # constructor starts the connection
    remote = RemoteControl(HOST)
    print("Connected")

    # A simple server example - https://pypi.org/project/uploadserver/
    remote.upload_files("http://192.168.152.181:8000/upload", "TEST")
    print('Closing connection')
    remote.close()

if __name__ == '__main__':
    main()
