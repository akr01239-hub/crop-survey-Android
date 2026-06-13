package com.cropsurvey.app.camera

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

/**
 * Burns a static stamp bitmap (GPS/timestamp/IDs) into every frame of an
 * H.264 mp4's video track via decode -> GL composite -> encode, copying the
 * audio track through unchanged. Runs entirely off the live camera pipeline,
 * so a failure here can never affect recording — callers should fall back to
 * uploading the original file if this throws.
 */
object VideoStampTranscoder {

    private const val TIMEOUT_US = 10_000L

    fun stampVideo(inputFile: File, outputFile: File, stampBitmap: Bitmap) {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputFile.absolutePath)

        var videoTrack = -1
        var audioTrack = -1
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") && videoTrack < 0) { videoTrack = i; videoFormat = fmt }
            else if (mime.startsWith("audio/") && audioTrack < 0) { audioTrack = i; audioFormat = fmt }
        }
        if (videoTrack < 0 || videoFormat == null) throw IllegalStateException("No video track found")

        val width  = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val srcMime = videoFormat.getString(MediaFormat.KEY_MIME)!!

        val gl = GlPipeline(width, height, stampBitmap)
        val decoder = MediaCodec.createDecoderByType(srcMime)
        extractor.selectTrack(videoTrack)
        decoder.configure(videoFormat, gl.decoderInputSurface, null, 0)
        decoder.start()

        val outFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            val bitrate = if (videoFormat.containsKey(MediaFormat.KEY_BIT_RATE)) videoFormat.getInteger(MediaFormat.KEY_BIT_RATE) else (width * height * 4)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            val frameRate = if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE) else 30
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        gl.encoderOutputSurface = encoder.createInputSurface()
        encoder.start()
        gl.initGl()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxVideoTrack = -1
        var muxAudioTrack = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var decoderDone = false
        var encoderDone = false

        while (!encoderDone) {
            if (!inputDone) {
                val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val buf = decoder.getInputBuffer(inIdx)!!
                    val sampleSize = extractor.readSampleData(buf, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            if (!decoderDone) {
                val outIdx = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIdx >= 0) {
                    val render = bufferInfo.size > 0
                    decoder.releaseOutputBuffer(outIdx, render)
                    if (render) {
                        gl.awaitFrame()
                        gl.drawFrame()
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        decoderDone = true
                        encoder.signalEndOfInputStream()
                    }
                }
            }

            val encIdx = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                encIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    muxVideoTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                encIdx >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(encIdx)!!
                    if (bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(muxVideoTrack, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(encIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encoderDone = true
                }
            }
        }

        decoder.stop(); decoder.release()
        encoder.stop(); encoder.release()
        gl.release()

        // Copy audio track through unchanged
        if (audioTrack >= 0 && audioFormat != null && muxerStarted) {
            muxAudioTrack = muxer.addTrack(audioFormat)
            val audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(inputFile.absolutePath)
            audioExtractor.selectTrack(audioTrack)
            val buf = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = audioExtractor.readSampleData(buf, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = audioExtractor.sampleTime
                info.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(muxAudioTrack, buf, info)
                audioExtractor.advance()
            }
            audioExtractor.release()
        }

        muxer.stop(); muxer.release()
        extractor.release()
    }

    /** Minimal GL pipeline: render decoded frame + stamp overlay to encoder's input Surface. */
    private class GlPipeline(private val width: Int, private val height: Int, private val stampBitmap: Bitmap) {
        lateinit var encoderOutputSurface: Surface
        val decoderInputSurface: Surface

        private lateinit var egl: EGL10
        private lateinit var eglDisplay: EGLDisplay
        private lateinit var eglContext: EGLContext
        private lateinit var eglSurface: EGLSurface

        private var cameraTextureId = 0
        private var stampTextureId = 0
        private lateinit var surfaceTexture: SurfaceTexture
        private var program = 0
        private var stampProgram = 0
        private val texMatrix = FloatArray(16)
        private val frameSyncLock = Object()
        private var frameAvailable = false

        init {
            // Placeholder texture id; real GL texture is created in initGl()
            // after the EGL context exists, then SurfaceTexture is rebound to it.
            surfaceTexture = SurfaceTexture(0)
            surfaceTexture.detachFromGLContext()
            surfaceTexture.setDefaultBufferSize(width, height)
            decoderInputSurface = Surface(surfaceTexture)
        }

        fun initGl() {
            egl = EGLContext.getEGL() as EGL10
            eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            egl.eglInitialize(eglDisplay, version)

            val attribList = intArrayOf(
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8, EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                EGL10.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            egl.eglChooseConfig(eglDisplay, attribList, configs, 1, numConfigs)
            val config = configs[0]!!

            val ctxAttribs = intArrayOf(0x3098, 2, EGL10.EGL_NONE) // EGL_CONTEXT_CLIENT_VERSION = 2
            eglContext = egl.eglCreateContext(eglDisplay, config, EGL10.EGL_NO_CONTEXT, ctxAttribs)
            eglSurface = egl.eglCreateWindowSurface(eglDisplay, config, encoderOutputSurface, null)
            egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            val texIds = IntArray(2)
            GLES20.glGenTextures(2, texIds, 0)
            cameraTextureId = texIds[0]
            stampTextureId = texIds[1]

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
            setTexParamsExternal()
            surfaceTexture.attachToGLContext(cameraTextureId)
            surfaceTexture.setOnFrameAvailableListener {
                synchronized(frameSyncLock) { frameAvailable = true; frameSyncLock.notifyAll() }
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stampTextureId)
            setTexParams2D()
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, stampBitmap, 0)

            program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXTERNAL)
            stampProgram = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D)
            GLES20.glViewport(0, 0, width, height)
        }

        private fun setTexParamsExternal() {
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        private fun setTexParams2D() {
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        fun awaitFrame() {
            synchronized(frameSyncLock) {
                while (!frameAvailable) frameSyncLock.wait(1000)
                frameAvailable = false
            }
        }

        fun drawFrame() {
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(texMatrix)

            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            drawTexturedQuad(program, cameraTextureId, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texMatrix)

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            drawTexturedQuad(stampProgram, stampTextureId, GLES20.GL_TEXTURE_2D, IDENTITY, flipY = true)
            GLES20.glDisable(GLES20.GL_BLEND)

            egl.eglSwapBuffers(eglDisplay, eglSurface)
        }

        private fun drawTexturedQuad(prog: Int, texId: Int, texTarget: Int, transform: FloatArray, flipY: Boolean = false) {
            GLES20.glUseProgram(prog)
            val posLoc = GLES20.glGetAttribLocation(prog, "aPosition")
            val texLoc = GLES20.glGetAttribLocation(prog, "aTexCoord")
            val mtxLoc = GLES20.glGetUniformLocation(prog, "uTexMatrix")
            val texSamplerLoc = GLES20.glGetUniformLocation(prog, "uTexture")

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(texTarget, texId)
            GLES20.glUniform1i(texSamplerLoc, 0)
            GLES20.glUniformMatrix4fv(mtxLoc, 1, false, transform, 0)

            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, QUAD_VERTICES)
            GLES20.glEnableVertexAttribArray(texLoc)
            GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, if (flipY) QUAD_TEXCOORDS_FLIPPED else QUAD_TEXCOORDS)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(posLoc)
            GLES20.glDisableVertexAttribArray(texLoc)
        }

        private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
            val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
            val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus)
            if (linkStatus[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(prog)
                android.util.Log.e("VideoStamp", "Program link failed: $log")
                throw RuntimeException("GL program link failed: $log")
            }
            return prog
        }

        private fun loadShader(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled)
            if (compiled[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                android.util.Log.e("VideoStamp", "Shader compile failed (type=$type): $log")
                throw RuntimeException("GL shader compile failed: $log")
            }
            return shader
        }

        fun release() {
            try {
                surfaceTexture.detachFromGLContext()
                surfaceTexture.release()
                decoderInputSurface.release()
                egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
                egl.eglDestroySurface(eglDisplay, eglSurface)
                egl.eglDestroyContext(eglDisplay, eglContext)
                egl.eglTerminate(eglDisplay)
            } catch (_: Exception) {}
        }

        companion object {
            private val IDENTITY = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

            private val QUAD_VERTICES = floatBuffer(floatArrayOf(
                -1f, -1f,  1f, -1f,  -1f, 1f,  1f, 1f
            ))
            private val QUAD_TEXCOORDS = floatBuffer(floatArrayOf(
                0f, 0f,  1f, 0f,  0f, 1f,  1f, 1f
            ))
            private val QUAD_TEXCOORDS_FLIPPED = floatBuffer(floatArrayOf(
                0f, 1f,  1f, 1f,  0f, 0f,  1f, 0f
            ))

            private fun floatBuffer(arr: FloatArray) =
                ByteBuffer.allocateDirect(arr.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(arr); position(0)
                }

            private const val VERTEX_SHADER = """
                attribute vec4 aPosition;
                attribute vec4 aTexCoord;
                uniform mat4 uTexMatrix;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vTexCoord = (uTexMatrix * aTexCoord).xy;
                }
            """

            private const val FRAGMENT_SHADER_EXTERNAL = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTexCoord;
                uniform samplerExternalOES uTexture;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """

            private const val FRAGMENT_SHADER_2D = """
                precision mediump float;
                varying vec2 vTexCoord;
                uniform sampler2D uTexture;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """
        }
    }
}
