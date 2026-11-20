// iOS override of android/app/src/main/java/uk/noammm/kav/data/GovMapTiles.kt @ 449cd8a. Keep public API identical.
//
// Android hands MapLibre an OkHttp client that tells GovMap's tile server the requests come from its own site. MapLibre
// Native on iOS makes its requests in Swift (MLNNetworkConfiguration), with no OkHttp to hand it, so for now GovMap's
// layers go out as MapLibre sends them and the server may refuse them (UPSTREAM_SYNC.md, Known differences).
package uk.noammm.kav.data

import org.maplibre.android.MapLibre

object GovMapTiles {
    const val HOST = "basemaps.govmap.gov.il"
    const val HOST_URL = "https://$HOST"

    fun dropConnections() {}

    fun mapLibre(ctx: android.content.Context) {
        MapLibre.getInstance(ctx.applicationContext)
    }
}
