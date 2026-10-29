Adhan Alarm is an open source project. Code is available at https://github.com/gitsponge/AdhanAlarm

Introduction

In a busy world where there are many distractions, it would be nice if your cellphone could alert you when it's time to pray. Especially in western countries where the call to prayer is not publicly announced, this application can help Muslims establish prayer. The times for the five daily prayers are determined by the position of the sun, for example the noon prayer is established when the sun produces the shortest shadows of the day. Algorithms exist that can determine these times based on the latitude, longitude and a few other input parameters.

The app is on Google Play at https://play.google.com/store/apps/details?id=islam.adhanalarm, or you can build it and install it yourself as described below.

Building

Needs a full JDK 17 or newer (a JRE without javac won't do) and the Android SDK. Android Studio's bundled JDK works:

  JAVA_HOME=/path/to/android-studio/jbr ./gradlew assembleDebug
  adb install -r app/build/outputs/apk/debug/app-debug.apk

Debug builds install beside the Play version as Adhān Alarm Dev (islam.adhanalarm.dev), with their own settings and alarms. Uninstall it after testing, or both will call you to prayer. The unit tests run on the computer:

  JAVA_HOME=/path/to/android-studio/jbr ./gradlew testDebugUnitTest

The app never goes online and has no internet permission. City names come from app/src/main/assets/cities.bin, made from GeoNames' cities5000 list (CC BY 4.0) by tools/make_cities.py, which says how to update it.

Testing the adhan without waiting for a prayer time

Debug builds include a receiver that sets a real alarm a few seconds ahead, so the whole path (exact alarm, notification, adhan) can be tried on a phone. prayer is fajr, sunrise, dhuhr, asr, maghrib or ishaa; type is none, notification, chime or adhan:

  adb shell am broadcast -a islam.adhanalarm.debug.TEST_ALARM -n islam.adhanalarm.dev/islam.adhanalarm.debug.TestAlarmReceiver --es prayer fajr --es type adhan --ei delay 10
  adb shell am broadcast -a islam.adhanalarm.debug.STOP_ADHAN -n islam.adhanalarm.dev/islam.adhanalarm.debug.TestAlarmReceiver

The adhan and chime never play during a call. On silent or vibrate they only play if Play On Silent Or Vibrate is on in the settings.

What's new in 4.0

A new design throughout, in light and dark, and still entirely offline.
- Today shows the next prayer with how long until it, and all of the day's times with their Arabic names in a column of their own. Phones and tablets get landscape layouts.
- Tap a prayer's icon to choose how it calls you: off, a notification, a chime or the adhan. Each prayer can also remind you 5 to 30 minutes before, and be moved up to an hour to match your mosque.
- A gentle chime replaces the beep.
- The adhan can be any sound on your phone, with a separate one for Fajr. Short Adhan plays only the opening Allāhu akbar.
- Find your city by name, also in Arabic or Russian script, or use your location. The nearest city within 20 km is named from a list of 64,000 places that comes with the app, so nothing is looked up online.
- A first-run setup suggests the calculation method used where you are.
- New methods: Gulf region, Kuwait, Qatar, Singapore, Malaysia and Indonesia, and Diyanet for Türkiye. Diyanet's times match its own timetable, safety minutes and rounding included.
- High latitudes use one-seventh of the night by default, so Isha and Fajr stay sensible in summer. Times estimated this way are marked *.
- Qibla says which way to turn and taps when you face it, shows the distance to Makkah, and helps when the compass needs calibrating.
- Settings are regrouped, and Alarm Check finds what could stop alarms: notifications, exact alarms, alarm volume and battery restrictions.
- New widgets: Next Prayer (wide, or a small tile) and Today's Times (a grid, or a small list). Both resize.
- The Hijri date can be moved up to two days either way.
- The language can be chosen in the app. Malay is new, and every language is complete. Number fields accept the language's own digits and decimal comma.
- Settings from 3.x carry over.

What's new in 3.0.2

Bug fixes
- Where Isha comes after midnight in summer (much of the UK, northern Europe and Canada), its adhan was skipped. It now plays, and the app keeps showing the evening's times until it has.
- On the nights the clocks change, Fajr could be an hour out if the times were worked out again between midnight and the change, for example by an app update.
- A number setting left empty, or holding only a minus sign or a point, crashed the app and stopped the alarms. Number settings are now checked when you enter them.
- The Qibla compass now allows for the difference between magnetic and true north, which is more than 10° in parts of North America and Australia.
- After a restart, the next prayer's alarm is set before the phone is first unlocked, so the adhan still plays.
- The pressure setting showed 110 instead of 1010 millibars.

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
