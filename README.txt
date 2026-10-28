Adhan Alarm is an open source project. Code is available at https://github.com/gitsponge/AdhanAlarm

Introduction

In a busy world where there are many distractions, it would be nice if your cellphone could alert you when it's time to pray. Especially in western countries where the call to prayer is not publicly announced, this application can help Muslims establish prayer. The times for the five daily prayers are determined by the position of the sun, for example the noon prayer is established when the sun produces the shortest shadows of the day. Algorithms exist that can determine these times based on the latitude, longitude and a few other input parameters.

The app is no longer listed on Google Play. Build it and install it yourself as described below.

Building

Needs a full JDK 17 or newer (a JRE without javac won't do) and the Android SDK. Android Studio's bundled JDK works:

  JAVA_HOME=/path/to/android-studio/jbr ./gradlew assembleDebug
  adb install -r app/build/outputs/apk/debug/app-debug.apk

Testing the adhan without waiting for a prayer time

Debug builds include a receiver that sets a real alarm a few seconds ahead, so the whole path (exact alarm, notification, adhan) can be tried on a phone. prayer is fajr, sunrise, dhuhr, asr, maghrib or ishaa; type is none, default, beep or play:

  adb shell am broadcast -a islam.adhanalarm.debug.TEST_ALARM -n islam.adhanalarm/.debug.TestAlarmReceiver --es prayer fajr --es type play --ei delay 10
  adb shell am broadcast -a islam.adhanalarm.debug.STOP_ADHAN -n islam.adhanalarm/.debug.TestAlarmReceiver

The adhan and beep never play during a call. On silent or vibrate they only play if Play On Silent Or Vibrate is ticked in the settings.

What's new in 3.0.1

A new app icon: the minaret and dome of the original icon, redrawn as a white silhouette on the same olive green. It also comes as a themed icon on Android 13+ and as a simplified notification icon.

What's new in 3.0

Updated for Android 6 to 17 (minSdk 23, targetSdk 37), built with Gradle 9, AGP 9 and AndroidX.
- The adhan plays from a foreground service on the alarm stream, so Android no longer cuts it off. Android 17 requires this for audio started in the background. It pauses other audio and stops for phone calls.
- Prayer alarms use the exact alarm permission that Android 12+ requires, and fall back to an inexact alarm if it's revoked.
- Asks for the notification permission Android 13+ requires, and for location (precise or approximate).
- Tapping the notification opens the app again; Android 12+ blocked the old way of doing it.
- Two notification channels, so the phone's notification sound no longer plays on top of the adhan.
- Alarms are rescheduled after an app update, a clock change or a time zone change, and set when the app first opens.
- The Qibla compass uses the rotation vector sensor instead of the deprecated orientation API.
- Settings use AndroidX preferences, with number keyboards for the number fields.
- Works with Android 15+ edge-to-edge screens.
- Bismillah on boot plays from a short alarm, since Android 15+ won't start playback straight from boot.
- A setting to play the adhan and beep even when the phone is on silent or vibrate.
- A Privacy Policy item in the settings.

Bug fixes
- Fajr ignored its notification setting when the alarm was set the night before, so the Fajr adhan didn't play.
- Time zones with half hours, such as India, Newfoundland and Iran, were 30 minutes off.
- Tomorrow's fajr was an hour off on the night daylight savings started or ended.
- Negative latitudes, longitudes and Qibla directions were shown wrongly, for example -1.5° as -2° 30'. The compass itself was right.
- The Information item opened a website that no longer exists. It now opens the project page on GitHub.
