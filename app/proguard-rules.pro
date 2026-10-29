# The Hijri calendar library only uses reflection in Calendar.getInstance(), which the app doesn't call
# (it creates an IslamicCalendar itself), so R8 can trace it like any other code. Its applet demo refers
# to java.applet, which Android doesn't have.
-dontwarn fi.joensuu.joyds1.**
