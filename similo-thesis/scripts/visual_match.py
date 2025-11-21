# visual_match.py
import sys
import cv2
import numpy as np

def main():
    full_img_path = sys.argv[1]
    template_path = sys.argv[2]

    full_img = cv2.imread(full_img_path)
    template = cv2.imread(template_path)

    if full_img is None or template is None:
        print("error: image load failed", file=sys.stderr)
        sys.exit(1)

    result = cv2.matchTemplate(full_img, template, cv2.TM_CCOEFF_NORMED)
    _, max_val, _, max_loc = cv2.minMaxLoc(result)

    print(f"{max_loc[0]} {max_loc[1]}")  # Output: x y

if __name__ == "__main__":
    main()
