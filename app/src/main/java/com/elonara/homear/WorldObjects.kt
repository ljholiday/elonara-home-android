package com.elonara.homear

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class WorldObject(
    val id: String,
    val type: String,
    val label: String,
    val latitude: Double,
    val longitude: Double
)

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val headingDegrees: Float
)

object PlaceholderWorldObjects {
    val deviceLocation = DeviceLocation(
        latitude = 37.7749,
        longitude = -122.4194,
        headingDegrees = 35.0f
    )

    val objects = listOf(
        nearbyWorldObject(
            id = "restaurant_1",
            type = "restaurants",
            label = "Restaurants",
            bearingDegrees = 35.0f,
            distanceMeters = 3.0
        ),
        nearbyWorldObject(
            id = "gas_1",
            type = "gas_stations",
            label = "Gas",
            bearingDegrees = 107.0f,
            distanceMeters = 4.2
        ),
        nearbyWorldObject(
            id = "park_1",
            type = "parks",
            label = "Parks",
            bearingDegrees = 179.0f,
            distanceMeters = 5.4
        ),
        nearbyWorldObject(
            id = "transit_1",
            type = "transit",
            label = "Transit",
            bearingDegrees = 251.0f,
            distanceMeters = 6.6
        ),
        nearbyWorldObject(
            id = "poi_1",
            type = "local_points_of_interest",
            label = "Local POI",
            bearingDegrees = 323.0f,
            distanceMeters = 7.8
        )
    )

    private fun nearbyWorldObject(
        id: String,
        type: String,
        label: String,
        bearingDegrees: Float,
        distanceMeters: Double
    ): WorldObject {
        val bearingRadians = bearingDegrees * PI / 180.0
        val latitudeOffset = distanceMeters * cos(bearingRadians) / METERS_PER_DEGREE_LATITUDE
        val longitudeOffset = distanceMeters * sin(bearingRadians) /
            (METERS_PER_DEGREE_LATITUDE * cos(deviceLocation.latitude * PI / 180.0))

        return WorldObject(
            id = id,
            type = type,
            label = label,
            latitude = deviceLocation.latitude + latitudeOffset,
            longitude = deviceLocation.longitude + longitudeOffset
        )
    }

    private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
}
