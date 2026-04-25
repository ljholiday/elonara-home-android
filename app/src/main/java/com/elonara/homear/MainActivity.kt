package com.elonara.homear

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/*
 * Minimal proof for Elonara Home's two-layer model.
 * Room objects are shown in a plain Android container; carry objects stay fixed on top.
 */
class MainActivity : AppCompatActivity() {
    private val roomLayerObjects = listOf(
        RoomObjectSpec("Calendar", "Room layer placeholder", -0.9f, 0.15f, -2.0f),
        RoomObjectSpec("Weather", "Room layer placeholder", 0.15f, 0.35f, -2.4f),
        RoomObjectSpec("Messages", "Room layer placeholder", 1.0f, -0.1f, -2.8f)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        populateRoomLayerObjects()
    }

    private fun populateRoomLayerObjects() {
        val container = findViewById<LinearLayout>(R.id.room_layer_container)
        val inflater = LayoutInflater.from(this)

        roomLayerObjects.forEach { roomObject ->
            val cardView = inflater.inflate(R.layout.view_room_object, container, false)
            cardView.findViewById<TextView>(R.id.roomObjectTitle).text = roomObject.title
            cardView.findViewById<TextView>(R.id.roomObjectSubtitle).text = roomObject.subtitle
            container.addView(cardView)
        }
    }
}

data class RoomObjectSpec(
    val title: String,
    val subtitle: String,
    val x: Float,
    val y: Float,
    val z: Float
)
