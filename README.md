## Smart Doorbell on ESP32-CAM

The *smart doorbell* works like this: when someone presses the bell button, the *ESP32-CAM* board takes a photo, sends a notification to your smartphone, and uploads the photo to *Firebase Storage*. You can also receive photos when you click on a special button in *my app*. All photos will be stored in the *cloud*, so you can view them from different devices. **Below is a video about my project on YouTube**.

[![Video about my project on YouTube](https://img.youtube.com/vi/1FJSTyXEtSo/0.jpg)](https://www.youtube.com/watch?v=1FJSTyXEtSo)

## Tools and frameworks that I used

[<img align="left" alt="ArduinoIDE" width="40px" src="https://raw.githubusercontent.com/github/explore/80688e429a7d4ef2fca1e82350fe8e3517d3494d/topics/arduino/arduino.png"/>](https://www.arduino.cc/en/software)
[<img align="left" alt="AndroidStudio" width="46px" src="https://cdn.worldvectorlogo.com/logos/android-studio-1.svg"/>](https://developer.android.com/studio)
[<img align="left" alt="Firebase" width="40px" src="https://raw.githubusercontent.com/github/explore/80688e429a7d4ef2fca1e82350fe8e3517d3494d/topics/firebase/firebase.png"/>](https://firebase.google.com)
</br>
</br>
## Arduino IDE libraries

+ [Firebase ESP Client](https://github.com/mobizt/Firebase-ESP-Client)
+ [Arduino JSON v6.15.2](https://github.com/bblanchon/ArduinoJson)

## Firebase

I also created a project on the [Firebase](https://firebase.google.com) platform. I have used the following Firebase tools:
+ [Firebase Authentication](https://firebase.google.com/docs/auth)
+ [Realtime Database](https://firebase.google.com/docs/database)
+ [Firebase Storage](https://firebase.google.com/docs/storage)
+ [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging)