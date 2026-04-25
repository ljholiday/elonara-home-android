package com.elonara.homear

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/*
 * Minimal spatial proof for Elonara Home's two-layer model.
 * Room objects are ARCore anchors projected into Android views; carry objects stay fixed on top.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var arSurface: GLSurfaceView
    private lateinit var roomLayerContainer: FrameLayout
    private lateinit var renderer: SpatialRenderer

    private var arSession: Session? = null
    private var installRequested = false

    private val roomLayerObjects = listOf(
        RoomObjectSpec("Calendar", "Room layer placeholder", -0.9f, 0.15f, -2.0f),
        RoomObjectSpec("Weather", "Room layer placeholder", 0.15f, 0.35f, -2.4f),
        RoomObjectSpec("Messages", "Room layer placeholder", 1.0f, -0.1f, -2.8f)
    )
    private val roomObjectViews = mutableMapOf<String, View>()

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                ensureArSession()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arSurface = findViewById(R.id.ar_surface)
        roomLayerContainer = findViewById(R.id.room_layer_container)
        renderer = SpatialRenderer(
            roomLayerObjects = roomLayerObjects,
            rotationProvider = { displayRotation() },
            onFrame = { projections -> runOnUiThread { updateRoomObjectPositions(projections) } }
        )

        arSurface.setEGLContextClientVersion(2)
        arSurface.setRenderer(renderer)
        arSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        inflateRoomLayerObjects()
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) {
            ensureArSession()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
        arSurface.onResume()
    }

    override fun onPause() {
        super.onPause()
        arSurface.onPause()
        arSession?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer.clearSession()
        arSession?.close()
        arSession = null
    }

    private fun ensureArSession() {
        if (arSession != null) {
            return
        }

        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return
                }

                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }

            val session = Session(this).apply {
                configure(
                    Config(this).apply {
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    }
                )
            }
            arSession = session
            renderer.setSession(session)
            session.resume()
        } catch (_: UnavailableException) {
            showSpatialStatus("ARCore is not available on this device.")
        } catch (_: CameraNotAvailableException) {
            showSpatialStatus("Camera is not available for AR tracking.")
        } catch (_: SecurityException) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun inflateRoomLayerObjects() {
        val inflater = LayoutInflater.from(this)

        roomLayerObjects.forEach { roomObject ->
            val cardView = inflater.inflate(R.layout.view_room_object, roomLayerContainer, false)
            cardView.findViewById<TextView>(R.id.roomObjectTitle).text = roomObject.title
            cardView.findViewById<TextView>(R.id.roomObjectSubtitle).text = roomObject.subtitle
            cardView.visibility = View.INVISIBLE
            roomLayerContainer.addView(cardView)
            roomObjectViews[roomObject.title] = cardView
        }
    }

    private fun updateRoomObjectPositions(projections: List<ObjectProjection>) {
        projections.forEach { projection ->
            val view = roomObjectViews[projection.title] ?: return@forEach
            if (!projection.visible) {
                view.visibility = View.INVISIBLE
                return@forEach
            }

            val width = view.width.takeIf { it > 0 } ?: view.measuredWidth
            val height = view.height.takeIf { it > 0 } ?: view.measuredHeight
            val params = (view.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )

            params.leftMargin = (projection.x - width / 2f).toInt()
            params.topMargin = (projection.y - height / 2f).toInt()
            view.layoutParams = params
            view.visibility = View.VISIBLE
        }
    }

    private fun showSpatialStatus(message: String) {
        roomObjectViews.values.firstOrNull()?.let { firstView ->
            firstView.findViewById<TextView>(R.id.roomObjectTitle).text = "Spatial unavailable"
            firstView.findViewById<TextView>(R.id.roomObjectSubtitle).text = message
            firstView.visibility = View.VISIBLE
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun displayRotation(): Int = display?.rotation ?: Surface.ROTATION_0
}

data class RoomObjectSpec(
    val title: String,
    val subtitle: String,
    val x: Float,
    val y: Float,
    val z: Float
)

data class ObjectProjection(
    val title: String,
    val x: Float,
    val y: Float,
    val visible: Boolean
)

private class SpatialRenderer(
    private val roomLayerObjects: List<RoomObjectSpec>,
    private val rotationProvider: () -> Int,
    private val onFrame: (List<ObjectProjection>) -> Unit
) : GLSurfaceView.Renderer {
    private val lock = Any()
    private var session: Session? = null
    private var cameraTextureId = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var anchors: List<Pair<RoomObjectSpec, Anchor>> = emptyList()

    fun setSession(session: Session) {
        synchronized(lock) {
            this.session = session
            if (cameraTextureId != 0) {
                session.setCameraTextureName(cameraTextureId)
            }
        }
    }

    fun clearSession() {
        synchronized(lock) {
            anchors.forEach { (_, anchor) -> anchor.detach() }
            anchors = emptyList()
            session = null
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.03f, 0.06f, 0.07f, 1.0f)
        cameraTextureId = createExternalTexture()
        synchronized(lock) {
            session?.setCameraTextureName(cameraTextureId)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        synchronized(lock) {
            session?.setDisplayGeometry(rotationProvider(), viewportWidth, viewportHeight)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val currentSession = synchronized(lock) { session } ?: return
        try {
            currentSession.setDisplayGeometry(rotationProvider(), viewportWidth, viewportHeight)
            val frame = currentSession.update()
            val camera = frame.camera
            if (camera.trackingState != TrackingState.TRACKING) {
                return
            }

            if (anchors.isEmpty()) {
                anchors = roomLayerObjects.map { roomObject ->
                    roomObject to currentSession.createAnchor(
                        camera.pose.compose(Pose.makeTranslation(roomObject.x, roomObject.y, roomObject.z))
                    )
                }
            }

            onFrame(projectAnchors(camera))
        } catch (_: CameraNotAvailableException) {
            // The Activity will retry when the session resumes.
        }
    }

    private fun projectAnchors(camera: Camera): List<ObjectProjection> {
        val viewMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)
        val viewProjectionMatrix = FloatArray(16)

        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        return anchors.map { (roomObject, anchor) ->
            val point = floatArrayOf(
                anchor.pose.tx(),
                anchor.pose.ty(),
                anchor.pose.tz(),
                1.0f
            )
            val clip = FloatArray(4)
            Matrix.multiplyMV(clip, 0, viewProjectionMatrix, 0, point, 0)

            val visible = clip[3] > 0.0f && anchor.trackingState == TrackingState.TRACKING
            if (!visible) {
                ObjectProjection(roomObject.title, 0.0f, 0.0f, visible = false)
            } else {
                val normalizedX = clip[0] / clip[3]
                val normalizedY = clip[1] / clip[3]
                ObjectProjection(
                    title = roomObject.title,
                    x = (normalizedX * 0.5f + 0.5f) * viewportWidth,
                    y = (1.0f - (normalizedY * 0.5f + 0.5f)) * viewportHeight,
                    visible = normalizedX in -1.2f..1.2f && normalizedY in -1.2f..1.2f
                )
            }
        }
    }

    private fun createExternalTexture(): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIds[0])
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        return textureIds[0]
    }
}
