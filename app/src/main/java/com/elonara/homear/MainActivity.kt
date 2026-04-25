package com.elonara.homear

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
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
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/*
 * Minimal spatial proof for Elonara Home's two-layer model.
 * Room objects are ARCore anchors projected into Android views; carry objects stay fixed on top.
 */
class MainActivity : AppCompatActivity() {
    private val tag = "ElonaraHomeAr"

    private lateinit var arSurface: GLSurfaceView
    private lateinit var roomLayerContainer: FrameLayout
    private lateinit var renderer: SpatialRenderer

    private var arSession: Session? = null
    private var installRequested = false
    private var isSessionResumed = false
    private val smoothedRoomObjectPositions = mutableMapOf<String, ScreenPoint>()

    private val roomLayerObjects = listOf(
        RoomObjectSpec("Calendar", "Room layer placeholder", -0.9f, 0.15f, -2.0f),
        RoomObjectSpec("Weather", "Room layer placeholder", 0.15f, 0.35f, -2.4f),
        RoomObjectSpec("Messages", "Room layer placeholder", 1.0f, -0.1f, -2.8f)
    )
    private val roomObjectViews = mutableMapOf<String, View>()

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(tag, "camera permission result granted=$granted")
            if (granted) {
                resumeArSession()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate")
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
        Log.d(tag, "onResume")
        if (hasCameraPermission()) {
            resumeArSession()
        } else {
            Log.d(tag, "requesting camera permission")
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
        arSurface.onResume()
    }

    override fun onPause() {
        Log.d(tag, "onPause")
        super.onPause()
        arSurface.onPause()
        renderer.setSession(null)
        arSession?.pause()
        isSessionResumed = false
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
        super.onDestroy()
        renderer.clearSession()
        arSession?.close()
        arSession = null
        isSessionResumed = false
    }

    private fun resumeArSession() {
        val session = ensureArSession() ?: return
        if (isSessionResumed) {
            Log.d(tag, "AR session already resumed")
            return
        }

        try {
            Log.d(tag, "resuming AR session")
            renderer.setSession(session)
            session.resume()
            isSessionResumed = true
        } catch (_: CameraNotAvailableException) {
            renderer.setSession(null)
            isSessionResumed = false
            showSpatialStatus("Camera is not available for AR tracking.")
        }
    }

    private fun ensureArSession(): Session? {
        arSession?.let { return it }

        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    Log.d(tag, "ARCore install requested")
                    installRequested = true
                    return null
                }

                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }

            Log.d(tag, "creating AR session")
            val session = Session(this).apply {
                configure(
                    Config(this).apply {
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    }
                )
            }
            arSession = session
            return session
        } catch (_: UnavailableException) {
            Log.d(tag, "ARCore unavailable")
            showSpatialStatus("ARCore is not available on this device.")
        } catch (_: SecurityException) {
            Log.d(tag, "camera permission missing while creating AR session")
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
        return null
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
                smoothedRoomObjectPositions.remove(projection.title)
                view.visibility = View.INVISIBLE
                return@forEach
            }

            val width = view.width.takeIf { it > 0 } ?: view.measuredWidth
            val height = view.height.takeIf { it > 0 } ?: view.measuredHeight
            val targetX = projection.x - width / 2f
            val targetY = projection.y - height / 2f
            val smoothedPoint = smoothedRoomObjectPositions.getOrPut(projection.title) {
                ScreenPoint(targetX, targetY)
            }

            smoothedPoint.x += (targetX - smoothedPoint.x) * ROOM_OBJECT_SMOOTHING
            smoothedPoint.y += (targetY - smoothedPoint.y) * ROOM_OBJECT_SMOOTHING
            view.x = smoothedPoint.x
            view.y = smoothedPoint.y
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

    private companion object {
        private const val ROOM_OBJECT_SMOOTHING = 0.42f
    }
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

data class ScreenPoint(
    var x: Float,
    var y: Float
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
    private var trackingFrameCount = 0
    private lateinit var cameraBackground: CameraBackgroundRenderer

    fun setSession(session: Session?) {
        synchronized(lock) {
            this.session = session
            if (session != null && cameraTextureId != 0) {
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
        cameraBackground = CameraBackgroundRenderer()
        cameraTextureId = cameraBackground.createOnGlThread()
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
            cameraBackground.draw(frame)
            val camera = frame.camera
            if (camera.trackingState != TrackingState.TRACKING) {
                trackingFrameCount = 0
                return
            }

            if (anchors.isEmpty()) {
                trackingFrameCount += 1
                if (trackingFrameCount < REQUIRED_TRACKING_FRAMES_BEFORE_ANCHORING) {
                    return
                }

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

    private companion object {
        private const val REQUIRED_TRACKING_FRAMES_BEFORE_ANCHORING = 24
    }
}

private class CameraBackgroundRenderer {
    private val quadCoords = floatBufferOf(
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f, 1.0f,
        1.0f, 1.0f
    )
    private val quadTexCoords = floatBufferOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f
    )
    private val transformedTexCoords = floatBufferOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f
    )

    private var textureId = 0
    private var program = 0
    private var positionAttribute = 0
    private var texCoordAttribute = 0
    private var textureUniform = 0

    fun createOnGlThread(): Int {
        textureId = createExternalTexture()
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionAttribute = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureUniform = GLES20.glGetUniformLocation(program, "u_Texture")
        return textureId
    }

    fun draw(frame: Frame) {
        if (frame.timestamp == 0L) {
            return
        }

        if (frame.hasDisplayGeometryChanged()) {
            quadCoords.position(0)
            transformedTexCoords.position(0)
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quadCoords,
                Coordinates2d.TEXTURE_NORMALIZED,
                transformedTexCoords
            )
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureUniform, 0)

        quadCoords.position(0)
        GLES20.glVertexAttribPointer(positionAttribute, 2, GLES20.GL_FLOAT, false, 0, quadCoords)
        GLES20.glEnableVertexAttribArray(positionAttribute)

        transformedTexCoords.position(0)
        GLES20.glVertexAttribPointer(
            texCoordAttribute,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            transformedTexCoords
        )
        GLES20.glEnableVertexAttribArray(texCoordAttribute)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionAttribute)
        GLES20.glDisableVertexAttribArray(texCoordAttribute)
        GLES20.glDepthMask(true)
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

    private fun createProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int =
        GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }

    private companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;

            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES u_Texture;

            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """
    }
}

private fun floatBufferOf(vararg values: Float): FloatBuffer =
    ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(values)
            position(0)
        }
