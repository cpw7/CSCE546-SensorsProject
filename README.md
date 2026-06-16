# Sensor Fusion Lab - CSCE 546 Sensors Project

Student: Christopher Wright

## Project idea
Sensor Fusion Lab is a polished Android sensor application designed to go beyond a basic accelerometer/magnetometer demo. Instead of only printing raw sensor numbers, the app combines several sensors into a useful live lab screen:

- Accelerometer: shake detection, movement classification, pitch/roll posture
- Magnetometer: compass heading
- Gyroscope: rotation-rate detection and motion classification
- Light sensor: indoor/bright/dim environment classification

The app uses sensor data to drive real app behavior. Shake events change the interface color, rotate the active challenge mode, update the shake count, and feed into the final fusion decision.

## Why this is not base-level
The app does more than list sensor values:

1. It uses more than the required two sensors.
2. It fuses accelerometer + magnetometer data into a compass heading.
3. It uses gyroscope + accelerometer data to classify motion.
4. It uses light data to decide whether the environment is dim, normal, or bright.
5. Shake events trigger UI changes and challenge modes.
6. The app handles missing sensors gracefully, which matters on emulators and different Android devices.

## How to demo
1. Open the app on a physical Android phone if possible.
2. Show the sensor availability card.
3. Tilt the phone to show pitch/roll posture updates.
4. Rotate the phone to show compass heading changes.
5. Shake the phone to increase the shake count and trigger a background color/challenge change.
6. Cover/uncover the light sensor to show lighting classification changes.
7. Show the fusion decision card combining stability and light level.

## Notes for emulator testing
Android emulators may not expose every physical sensor. The app is written to keep running even when a sensor is unavailable. A real Android phone is best for the final demonstration.
