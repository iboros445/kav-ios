// LOCAL-CHECK ONLY: data/JourneyFile.kt needs ActiveJourney (MainActivity.kt, Compose), so klib_check.sh leaves it
// out; these are the members the rest of the data layer calls. Signatures copied from upstream.
@file:Suppress("unused", "UNUSED_PARAMETER")
package uk.noammm.kav.data

import org.json.JSONObject

object JourneyFile {
    internal fun routesJson(routes: List<Transit.Itinerary>, r: Transit.Resolved, sections: List<Transit.Section>): JSONObject = JSONObject()
    internal fun routes(o: JSONObject): RecentRoutes.Saved? = null
}

// data/RecentRoutes.kt keeps the last trip as an ActiveJourney (MainActivity.kt) since 4.3, so klib_check.sh leaves it
// out; TripShare reads its Saved.
object RecentRoutes {
    class Saved(val routes: List<Transit.Itinerary>, val resolved: Transit.Resolved, val sections: List<Transit.Section>)
}
