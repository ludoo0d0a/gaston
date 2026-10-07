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
 *
 * Fields are nullable because Robolectric stubs [EGL14.EGL_NO_DISPLAY] / [EGL14.EGL_NO_CONTEXT] /
 * [EGL14.EGL_NO_SURFACE] as null — assigning those constants into non-null fields NPEs in unit tests.
 */
class CarEglSurfaceRenderer {

    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
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

            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == null || display == EGL14.EGL_NO_DISPLAY) {
                Log.e(TAG, "eglGetDisplay failed")
                return false
            }
            eglDisplay = display

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
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
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
                Log.e(TAG, "eglChooseConfig failed")
                return false
            }
            val chosenConfig = configs[0] ?: return false
            eglConfig = chosenConfig

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            val context = EGL14.eglCreateContext(display, chosenConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (context == null || context == EGL14.EGL_NO_CONTEXT) {
                Log.e(TAG, "eglCreateContext failed")
                return false
            }
            eglContext = context

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            val winSurface = EGL14.eglCreateWindowSurface(display, chosenConfig, surface, surfaceAttribs, 0)
            if (winSurface == null || winSurface == EGL14.EGL_NO_SURFACE) {
                Log.e(TAG, "eglCreateWindowSurface failed")
                return false
            }
            eglSurface = winSurface

            if (!EGL14.eglMakeCurrent(display, winSurface, winSurface, context)) {
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
        val display = eglDisplay ?: return false
        val surface = eglSurface ?: return false
        val context = eglContext ?: return false
        if (!isInitialized) return false
        return EGL14.eglMakeCurrent(display, surface, surface, context)
    }

    fun swapBuffers(): Boolean {
        val display = eglDisplay ?: return false
        val surface = eglSurface ?: return false
        if (!isInitialized) return false
        return EGL14.eglSwapBuffers(display, surface)
    }

    fun detachSurface() {
        val display = eglDisplay
        if (display != null && display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            val surface = eglSurface
            if (surface != null && surface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, surface)
            }
            val context = eglContext
            if (context != null && context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context)
            }
            EGL14.eglTerminate(display)
        }
        eglSurface = null
        eglContext = null
        eglDisplay = null
        eglConfig = null
        isInitialized = false
        Log.d(TAG, "EGL surface detached")
    }

    companion object {
        private const val TAG = "CarEglSurfaceRenderer"
    }
}
