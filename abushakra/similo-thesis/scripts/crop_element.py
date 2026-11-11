# crop_element.py
import sys
import cv2

def main():
    screenshot = sys.argv[1]
    output = sys.argv[2]
    x, y, w, h = map(int, sys.argv[3:])

    img = cv2.imread(screenshot)
    if img is None:
        print(f"[ERROR] Could not load image: {screenshot}")
        sys.exit(1)
    crop = img[y:y+h, x:x+w]
    if crop.size == 0:
        print(f"[ERROR] Crop region is empty or out of bounds: x={x}, y={y}, w={w}, h={h}")
        sys.exit(1)
    cv2.imwrite(output, crop)

if __name__ == "__main__":
    main()