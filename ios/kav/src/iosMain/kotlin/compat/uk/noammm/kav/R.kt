// Android generates R from res/; Kotlin code only ever names these ids, so they are stand-ins.
package uk.noammm.kav

object R {
    object drawable {
        const val ic_launcher_background = 1
        const val ic_launcher_foreground = 2
        const val ic_trip_notice = 3
    }

    object string { const val app_name = 10 }
    object mipmap { const val ic_launcher = 20 }
}
