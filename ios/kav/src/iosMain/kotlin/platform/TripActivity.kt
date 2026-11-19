package uk.noammm.kav.platform

interface TripActivityHandler {
    fun update(
        tripId: String,
        title: String,
        subtitle: String,
        chip: String,
        tintHex: String,
        modeName: String,
        arrivalTime: String,
        progress: Double,
        dueText: String,
    )
    fun end()
}

object TripActivityBridge {
    var handler: TripActivityHandler? = null
}
