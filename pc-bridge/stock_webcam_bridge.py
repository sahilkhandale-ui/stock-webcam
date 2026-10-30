"""
Stock Android Webcam - Python Virtual Camera Bridge
Feeds the Android camera stream directly into a Windows DirectShow Virtual Camera,
allowing Zoom, Teams, Google Meet, Skype, Discord, and Windows Camera to detect it.

Prerequisites (optional, ultra-light):
    pip install opencv-python pyvirtualcam
"""

import sys
import time

try:
    import cv2
    import pyvirtualcam
except ImportError:
    print("[!] Missing required lightweight libraries.")
    print("[*] Run: pip install opencv-python pyvirtualcam")
    print("[*] Alternatively, simply open the URL directly in Chrome/Edge or OBS Studio!")
    sys.exit(1)

STREAM_URL = "http://localhost:8080/stream"
if len(sys.argv) > 1:
    STREAM_URL = sys.argv[1]

print("======================================================")
print("      Stock Android Webcam - Virtual Camera Bridge    ")
print("======================================================")
print(f"[*] Connecting to camera stream at: {STREAM_URL}")

cap = cv2.VideoCapture(STREAM_URL)

if not cap.isOpened():
    # Try USB Tethering default IP
    fallback_url = "http://192.168.42.129:8080/stream"
    print(f"[!] Could not connect to {STREAM_URL}. Trying {fallback_url}...")
    cap = cv2.VideoCapture(fallback_url)
    if not cap.isOpened():
        print("[X] Error: Could not connect to stream. Make sure StockWebcam is running on the phone.")
        sys.exit(1)

ret, frame = cap.read()
if not ret or frame is None:
    print("[X] Error reading first frame from camera stream.")
    sys.exit(1)

height, width, _ = frame.shape
print(f"[+] Connected! Resolution: {width}x{height}")
print("[+] Creating Windows Virtual Camera: 'Stock Android Webcam'...")

try:
    with pyvirtualcam.Camera(width=width, height=height, fps=60, fmt=pyvirtualcam.PixelFormat.BGR) as cam:
        print(f"[✓] Virtual camera created successfully: {cam.device}")
        print("[*] You can now select this camera in Zoom, Teams, Google Meet, Discord, etc.")
        print("[*] Press Ctrl+C in this console to stop.")
        
        while True:
            ret, frame = cap.read()
            if not ret or frame is None:
                time.sleep(0.01)
                continue
            cam.send(frame)
            cam.sleep_until_next_frame()

except KeyboardInterrupt:
    print("\n[*] Stopping virtual camera...")
finally:
    cap.release()
    print("[*] Done.")

