package com.elonara.homear

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object BearingMath {
    fun bearingDegrees(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double
    ): Float {
        val fromLat = fromLatitude.toRadians()
        val toLat = toLatitude.toRadians()
        val deltaLon = (toLongitude - fromLongitude).toRadians()

        val y = sin(deltaLon) * cos(toLat)
        val x = cos(fromLat) * sin(toLat) -
            sin(fromLat) * cos(toLat) * cos(deltaLon)

        return normalizeDegrees((atan2(y, x) * 180.0 / PI).toFloat())
    }

    fun normalizeSignedDegrees(angle: Float): Float {
        val normalized = normalizeDegrees(angle)
        return if (normalized > 180.0f) normalized - 360.0f else normalized
    }

    fun normalizeDegrees(angle: Float): Float {
        val normalized = angle % 360.0f
        return if (normalized < 0.0f) normalized + 360.0f else normalized
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
}
