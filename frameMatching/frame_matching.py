import tempfile

import cv2
import ffmpeg
import numpy as np


def correct_rotation(frame, rotate_code):
    return cv2.rotate(frame, rotate_code)


class FrameMatcher:
    """
    Provides methods for matching frames in video with saved images
    """

    def __init__(self, video_filename, video_extension, capture_info_path, matching_threshold=0.99):
        self.video_filename = video_filename
        self.video_path = video_filename + "." + video_extension
        self.video = cv2.VideoCapture(self.video_path)
        self.capture_info_path = capture_info_path
        self.matching_threshold = matching_threshold

        timestamps_file = f"{self.capture_info_path}/{self.video_filename}_timestamps.csv"
        with open(timestamps_file, 'r') as timestamps_f:
            self.timestamps = timestamps_f.readlines()

    def split_to_frames(self, frame_dir):
        """
        Splits current video to frames and saves them in specified directory
        """

        # We need to check rotation metadata tag as android videos use it
        # to change video orientation
        rotate_code = self.check_rotation()
        success, image = self.video.read()
        count = 0
        print("Splitting video...")
        while success:
            if rotate_code is not None:
                image = correct_rotation(image, rotate_code)
            cv2.imwrite(f"{frame_dir}/frame{count}.jpg", image)  # save frame as JPEG file
            success, image = self.video.read()
            count += 1
        print("Finished splitting video")
        return count

    def match_frames(self):
        print(f"Matching frame images with threshold {self.matching_threshold}")
        with tempfile.TemporaryDirectory() as frame_dir:
            count = self.split_to_frames(frame_dir)
            assert len(
                self.timestamps) == count, f"Frames and timestamps counts do not match, {count}, {len(self.timestamps)}"

            flags = []
            step = 60
            for index, timestamp_str in enumerate(self.timestamps[::step]):
                i = index * step
                timestamp = timestamp_str.strip('\n')
                im1 = cv2.imread(f"{frame_dir}/frame{i}.jpg", cv2.IMREAD_GRAYSCALE)
                im2 = cv2.imread(f"{self.capture_info_path}/{timestamp}.jpg", cv2.IMREAD_GRAYSCALE)
                # print(f"Matching {self.capture_info_path}/{timestamp}.jpg")

                # Resize video frame
                height1, width1 = im1.shape
                ratio1 = height1 / width1
                height2, width2 = im2.shape
                ratio2 = height2 / width2
                assert ratio1 == ratio2, "Image and frame ratio do not match"

                im1 = cv2.resize(im1, (width2, height2))

                # Match two images using cross-correlation
                res = cv2.matchTemplate(im1, im2, cv2.TM_CCOEFF_NORMED)
                flag = False
                if np.amax(res) > self.matching_threshold:
                    flag = True
                flags.append(flag)

            return all(flags)

    def check_rotation(self):
        """
        Checks video metadata for rotation tag
        :return: Optional rotation code
        """
        meta_dict = ffmpeg.probe(self.video_path)

        # from the dictionary, meta_dict['streams'][0]['tags']['rotate'] is the key
        # we are looking for
        # from the dictionary, meta_dict['streams'][0]['tags']['rotate'] is the key
        # we are looking for
        rotate_code = None
        if int(meta_dict['streams'][0]['tags']['rotate']) == 90:
            rotate_code = cv2.ROTATE_90_CLOCKWISE
        elif int(meta_dict['streams'][0]['tags']['rotate']) == 180:
            rotate_code = cv2.ROTATE_180
        elif int(meta_dict['streams'][0]['tags']['rotate']) == 270:
            rotate_code = cv2.ROTATE_90_COUNTERCLOCKWISE

        return rotate_code
