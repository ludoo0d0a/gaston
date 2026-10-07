package fr.geoking.gaston.auto.maplibre

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import fr.geoking.gaston.auto.AutoMapOverlayHelper
import fr.geoking.gaston.radar.DangerZoneHudState
import fr.geoking.gaston.radar.DangerZoneHudStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Screen-fixed AAC "Zone de danger" + VMA badge for MapLibre GL Presentation / EGL hosts
 * (Canvas overlay path already draws this via [AutoMapOverlayHelper.drawCompassAndScale]).
 */
internal class DangerZonePresenceBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var hud: DangerZoneHudState = DangerZoneHudState()
    private var scope: CoroutineScope? = null
    private var collectJob: Job? = null
    private var visibleArea: Rect? = null

    fun setVisibleArea(area: Rect?) {
        visibleArea = area
        invalidate()
    }

    fun bindHudStore(store: DangerZoneHudStore) {
        collectJob?.cancel()
        val s = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        collectJob = s.launch {
            store.hudState.collect { state ->
                hud = state
                visibility = if (state.active) VISIBLE else GONE
                invalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        collectJob?.cancel()
        collectJob = null
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (!hud.active) return
        val area = visibleArea ?: Rect(0, 0, width, height)
        val isMenuOnRight = (width - area.right) > area.left + (20 * resources.displayMetrics.density)
        AutoMapOverlayHelper.drawDangerZonePresenceBadge(
            canvas = canvas,
            context = context,
            area = area,
            density = resources.displayMetrics.density,
            speedLimitKmH = hud.speedLimitKmH,
            isMenuOnRight = isMenuOnRight,
        )
    }
}
