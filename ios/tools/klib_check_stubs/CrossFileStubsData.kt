// LOCAL-CHECK ONLY: data/JourneyFile.kt needs ActiveJourney (MainActivity.kt, Compose), so klib_check.sh leaves it
// out; these are the members the rest of the data layer calls. Signatures copied from upstream.
@file:Suppress("unused", "UNUSED_PARAMETER")
package uk.noammm.kav.data

import org.json.JSONObject

object JourneyFile {
    internal fun routesJson(routes: List<Transit.Itinerary>, r: Transit.Resolved, sections: List<Transit.Section>): JSONObject = JSONObject()
    internal fun routes(o: JSONObject): RecentRoutes.Saved? = null
}
