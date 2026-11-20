package uk.noammm.kav.ios

import uk.noammm.kav.platform.TripActivityBridge
import uk.noammm.kav.platform.TripActivityHandler

/**
 * Called once from Swift at launch (KavApp.init) with the LiveActivity implementation.
 * Referencing TripActivityHandler here is what exports it to Swift.
 */
fun registerTripActivityHandler(handler: TripActivityHandler) {
    TripActivityBridge.handler = handler
}
