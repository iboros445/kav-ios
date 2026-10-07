package android.content

import platform.UIKit.UIScreen

class DisplayMetrics { val density: Float get() = UIScreen.mainScreen.scale.toFloat() }

class Resources { val displayMetrics: DisplayMetrics = DisplayMetrics() }
