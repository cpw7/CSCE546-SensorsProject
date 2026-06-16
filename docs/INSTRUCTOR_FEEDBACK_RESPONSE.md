# Response to Instructor Feedback

The earlier sensor project was too basic because it mainly showed common accelerometer/magnetometer values. This version was redesigned to show stronger 500-level effort.

Major improvements:

- Uses accelerometer, magnetometer, gyroscope, and light sensor instead of only two basic sensors.
- Combines accelerometer and magnetometer into a live compass heading.
- Combines accelerometer and gyroscope into a motion classifier.
- Uses light sensor data for environmental classification.
- Adds shake-triggered behavior: background color change, shake count, and challenge mode.
- Includes a fusion decision card instead of only raw readings.
- Handles missing sensors cleanly for emulator/device differences.
