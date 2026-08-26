package fr.geoking.gaston.auto.maplibre

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import androidx.car.app.SurfaceContainer

/**
 * Native EGL Surface Manager for Android Auto.
 * Provides helper functions for EGL context initialization when driving GPU surface rendering directly.
 */
class CarEglSurfaceRenderer {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var eglConfig: EGLConfig? = null

    @Volatile
    var isInitialized = false
        private set

    fun attachSurface(surfaceContainer: SurfaceContainer): Boolean {
        val surface = surfaceContainer.surface
        if (surface == null || !surface.isValid) {
            Log.w(TAG, "SurfaceContainer.surface is null or invalid; skipping EGL attach")
            return false
        }

        try {
            detachSurface()

            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                Log.e(TAG, "eglGetDisplay failed")
                return false
            }

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                Log.e(TAG, "eglInitialize failed")
                return false
            }

            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
                Log.e(TAG, "eglChooseConfig failed")
                return false
            }
            val chosenConfig = configs[0] ?: return false
            eglConfig = chosenConfig

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, chosenConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                Log.e(TAG, "eglCreateContext failed")
                return false
            }

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, chosenConfig, surface, surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                Log.e(TAG, "eglCreateWindowSurface failed")
                return false
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                Log.e(TAG, "eglMakeCurrent failed")
                return false
            }

            GLES20.glViewport(0, 0, surfaceContainer.width, surfaceContainer.height)
            isInitialized = true
            Log.d(TAG, "EGL surface attached successfully (${surfaceContainer.width}x${surfaceContainer.height})")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching EGL surface", e)
            detachSurface()
            return false
        }
    }

    fun makeCurrent(): Boolean {
        if (!isInitialized || eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) {
            return false
        }
        return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    fun swapBuffers(): Boolean {
        if (!isInitialized || eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) {
            return false
        }
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun detachSurface() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
        isInitialized = false
        Log.d(TAG, "EGL surface detached")
    }

    companion object {
        private const val TAG = "CarEglSurfaceRenderer"
    }
}
