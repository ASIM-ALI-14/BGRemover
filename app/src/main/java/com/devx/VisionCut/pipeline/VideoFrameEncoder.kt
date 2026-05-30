package com.devx.VisionCut.pipeline

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class VideoFrameEncoder(
    private val outputFile: File,
    private val width: Int,
    private val height: Int,
    private val fps: Int
) {
    companion object {
        private const val TAG            = "VideoFrameEncoder"
        private const val MIME           = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val TIMEOUT        = 10_000L
        private const val EGL_RECORDABLE = 0x3142
    }

    // Auto bitrate: 0.2 bits/pixel/frame, clamped 4–20 Mbps
    private val bitrate = (width * height * fps * 0.2).toInt().coerceIn(4_000_000, 20_000_000)

    private var codec         : MediaCodec? = null
    private var muxer         : MediaMuxer? = null
    private var muxerTrack                  = -1
    private var muxerStarted                = false
    private val bufInfo                     = MediaCodec.BufferInfo()
    private var encoderSurface: Surface?    = null

    // EGL handles
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    // GLES program handles
    private var program = 0
    private val tex     = IntArray(1)
    private var aPos    = 0
    private var aUV     = 0
    private var uTex    = 0

    // Pre-allocated vertex/UV buffers — created once in start(), reused every frame
    private lateinit var vertBuf: FloatBuffer
    private lateinit var uvBuf  : FloatBuffer

    private val VERT = """
        attribute vec2 aPos; attribute vec2 aUV;
        varying vec2 vUV;
        void main(){ gl_Position=vec4(aPos,0,1); vUV=aUV; }
    """.trimIndent()

    private val FRAG = """
        precision mediump float;
        uniform sampler2D uTex; varying vec2 vUV;
        void main(){ gl_FragColor=texture2D(uTex,vUV); }
    """.trimIndent()

    // NDC quad vertices + flipped UV (bitmap top → screen top)
    private val verts = floatArrayOf(-1f, -1f,  1f, -1f, -1f,  1f,  1f,  1f)
    private val uvs   = floatArrayOf( 0f,  1f,  1f,  1f,  0f,  0f,  1f,  0f)

    fun start() {
        val fmt = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE,         bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE,       fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val c = MediaCodec.createEncoderByType(MIME).also { codec = it }
        c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoderSurface = c.createInputSurface()
        c.start()

        initEgl()
        initGles()

        // Allocate geometry buffers once — reused on every encodeFrame call
        vertBuf = toFloatBuffer(verts)
        uvBuf   = toFloatBuffer(uvs)

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        Log.i(TAG, "Encoder: ${width}×${height} @${fps}fps ${bitrate / 1_000}kbps")
    }

    fun encodeFrame(bitmap: Bitmap, ptsUs: Long) {
        // Upload bitmap to GL texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        // Draw fullscreen quad
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)

        vertBuf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vertBuf)

        uvBuf.position(0)
        GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 0, uvBuf)

        GLES20.glUniform1i(uTex, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Set exact PTS per frame (convert µs → ns)
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsUs * 1_000L)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)

        drain(false)
    }

    fun finish() {
        codec?.signalEndOfInputStream()
        drain(true)
        destroyEgl()
        muxer?.stop()
        muxer?.release()
        codec?.stop()
        codec?.release()
        encoderSurface?.release()
        codec          = null
        muxer          = null
        encoderSurface = null
        muxerStarted   = false
    }

    // ── EGL setup ─────────────────────────────────────────────────────────────

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        EGL14.eglInitialize(eglDisplay, null, 0, null, 0)

        val att = intArrayOf(
            EGL14.EGL_RED_SIZE,        8,
            EGL14.EGL_GREEN_SIZE,      8,
            EGL14.EGL_BLUE_SIZE,       8,
            EGL14.EGL_ALPHA_SIZE,      8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE,            1,
            EGL14.EGL_NONE
        )
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val n    = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, att, 0, cfgs, 0, 1, n, 0)
        check(n[0] > 0) { "No EGL config found" }

        eglContext = EGL14.eglCreateContext(
            eglDisplay, cfgs[0]!!, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, cfgs[0]!!, encoderSurface,
            intArrayOf(EGL14.EGL_NONE), 0
        )
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun destroyEgl() {
        GLES20.glDeleteTextures(1, tex, 0)
        GLES20.glDeleteProgram(program)
        EGL14.eglMakeCurrent(
            eglDisplay,
            EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT
        )
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
    }

    // ── GLES setup ────────────────────────────────────────────────────────────

    private fun initGles() {
        fun compileShader(type: Int, src: String) = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src)
            GLES20.glCompileShader(it)
        }
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, compileShader(GLES20.GL_VERTEX_SHADER,   VERT))
            GLES20.glAttachShader(it, compileShader(GLES20.GL_FRAGMENT_SHADER, FRAG))
            GLES20.glLinkProgram(it)
        }
        aPos = GLES20.glGetAttribLocation(program,  "aPos")
        aUV  = GLES20.glGetAttribLocation(program,  "aUV")
        uTex = GLES20.glGetUniformLocation(program, "uTex")

        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,     GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,     GLES20.GL_CLAMP_TO_EDGE)
    }

    // ── Codec drain ───────────────────────────────────────────────────────────

    private fun drain(eos: Boolean) {
        val c = codec ?: return
        val m = muxer ?: return
        while (true) {
            val idx = c.dequeueOutputBuffer(bufInfo, TIMEOUT)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!eos) break
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        muxerTrack   = m.addTrack(c.outputFormat)
                        m.start()
                        muxerStarted = true
                    }
                }
                idx >= 0 -> {
                    val buf = c.getOutputBuffer(idx)!!
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0)
                        bufInfo.size = 0
                    if (bufInfo.size > 0 && muxerStarted) {
                        buf.position(bufInfo.offset)
                        buf.limit(bufInfo.offset + bufInfo.size)
                        m.writeSampleData(muxerTrack, buf, bufInfo)
                    }
                    c.releaseOutputBuffer(idx, false)
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
    }

    /** Create a native-order FloatBuffer from a float array. */
    private fun toFloatBuffer(arr: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(arr.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .also { it.put(arr); it.position(0) }
}