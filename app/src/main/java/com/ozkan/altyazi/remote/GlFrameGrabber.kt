package com.ozkan.altyazi.remote

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.ozkan.altyazi.data.Roi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch

/** A decoded stream frame; [read] may only be called during the frame callback. */
interface StreamFrame {
    /** The [roi] of the picture (normalized, top-left origin) scaled to [width]×[height] pixels. */
    fun read(roi: Roi, width: Int, height: Int, reuse: Bitmap? = null): Bitmap
}

/**
 * Receives the video decoder's output on a GPU texture and reads parts of it back as RGB bitmaps.
 *
 * Hardware decoders may write vendor-specific layouts (MediaTek uses tiled blocks) even when asked
 * for plain YUV through an ImageReader; sampling the frame as an external texture lets the GPU
 * convert any such layout exactly like the display does. Scaling is done on the GPU as well.
 */
class GlFrameGrabber(
    width: Int,
    height: Int,
    private val onFrame: (StreamFrame) -> Unit,
) : StreamFrame {

    private val thread = HandlerThread("remote-gl").apply { start() }
    private val handler = Handler(thread.looper)

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var texture = 0
    private var framebuffer = 0
    private var renderTexture = 0
    private var renderWidth = 0
    private var renderHeight = 0
    private var readBuffer: ByteBuffer? = null
    private val texMatrix = FloatArray(16)
    private lateinit var surfaceTexture: SurfaceTexture

    /** The decoder renders into this surface. */
    lateinit var surface: Surface
        private set

    @Volatile private var released = false

    init {
        val ready = CountDownLatch(1)
        var failure: Throwable? = null
        handler.post {
            try {
                setUp(width, height)
            } catch (e: Throwable) {
                failure = e
            } finally {
                ready.countDown()
            }
        }
        ready.await()
        failure?.let {
            release()
            throw it
        }
    }

    private fun setUp(width: Int, height: Int) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "No EGL config" }
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
        eglSurface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent failed" }

        program = buildProgram()
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glGenFramebuffers(1, ids, 0)
        framebuffer = ids[0]

        surfaceTexture = SurfaceTexture(texture).apply {
            setDefaultBufferSize(width, height)
            setOnFrameAvailableListener({ onFrameAvailable() }, handler)
        }
        surface = Surface(surfaceTexture)
    }

    private fun onFrameAvailable() {
        if (released) return
        try {
            // Latching every frame keeps the decoder's buffers flowing even when nothing is read.
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(texMatrix)
            onFrame(this)
        } catch (e: Throwable) {
            // A frame that cannot be read is skipped.
        }
    }

    override fun read(roi: Roi, width: Int, height: Int, reuse: Bitmap?): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        ensureTarget(w, h)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTex"), 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, texMatrix, 0)
        // Texture space has its origin bottom-left; output row 0 (read first) must be the region's top.
        GLES20.glUniform4f(
            GLES20.glGetUniformLocation(program, "uCrop"),
            roi.left, 1f - roi.top, roi.right - roi.left, -(roi.bottom - roi.top),
        )

        val pos = GLES20.glGetAttribLocation(program, "aPos")
        val tex = GLES20.glGetAttribLocation(program, "aTex")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, QUAD_POS)
        GLES20.glEnableVertexAttribArray(tex)
        GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, 0, QUAD_TEX)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        val buffer = readBuffer?.takeIf { it.capacity() >= w * h * 4 }
            ?: ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder()).also { readBuffer = it }
        buffer.clear()
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        val bitmap = reuse?.takeIf { it.width == w && it.height == h && !it.isRecycled && it.isMutable }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun ensureTarget(w: Int, h: Int) {
        if (w == renderWidth && h == renderHeight && renderTexture != 0) return
        val ids = IntArray(1)
        if (renderTexture != 0) {
            ids[0] = renderTexture
            GLES20.glDeleteTextures(1, ids, 0)
        }
        GLES20.glGenTextures(1, ids, 0)
        renderTexture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture, 0)
        check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) { "Framebuffer incomplete" }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        renderWidth = w
        renderHeight = h
    }

    /** Frees GPU objects and stops the thread; call after the decoder stopped using [surface]. */
    fun release() {
        if (released) return
        released = true
        handler.post {
            runCatching { surfaceTexture.setOnFrameAvailableListener(null) }
            runCatching { surface.release() }
            runCatching { surfaceTexture.release() }
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(display)
            }
            thread.quitSafely()
        }
    }

    private fun buildProgram(): Int {
        fun shader(type: Int, source: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, source)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "Shader: " + GLES20.glGetShaderInfoLog(s) }
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, VERTEX))
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "Program: " + GLES20.glGetProgramInfoLog(p) }
        return p
    }

    private companion object {
        const val VERTEX = """
            attribute vec2 aPos;
            attribute vec2 aTex;
            uniform mat4 uTexMatrix;
            uniform vec4 uCrop;
            varying vec2 vTex;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                vec2 t = uCrop.xy + aTex * uCrop.zw;
                vTex = (uTexMatrix * vec4(t, 0.0, 1.0)).xy;
            }
        """
        const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            // Full precision: mediump texture coordinates are too coarse for 1920-pixel-wide text.
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform samplerExternalOES sTex;
            varying vec2 vTex;
            void main() {
                gl_FragColor = texture2D(sTex, vTex);
            }
        """

        val QUAD_POS = floatBuffer(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val QUAD_TEX = floatBuffer(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

        fun floatBuffer(vararg values: Float) = ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
    }
}
