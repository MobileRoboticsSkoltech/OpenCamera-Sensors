from frame_matching import FrameMatcher
import config


def main():
    frame_matcher = FrameMatcher(
        config.VID_FILENAME,
        config.VID_EXTENSION,
        config.CAPTURE_INFO_PATH
    )

    if frame_matcher.match_frames():
        print("Frame counts and frame images are matching")
    else:
        print("Couldn't match frame images")


if __name__ == '__main__':
    main()
