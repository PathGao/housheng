package io.github.pathgao.housheng

import android.accessibilityservice.AccessibilityService
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.function.Consumer

internal enum class MaskStyle(val label: String) { LIVE_BLUR("实时模糊"), FRAME_BLUR("截图模糊"), SOLID("纯色") }

/** Live blur keeps moving video unreadable; otherwise one captured frame is blurred; solid only when no frame is available. */
internal fun maskStyle(blurEnabled: Boolean, frameCaptured: Boolean) = when {
    blurEnabled -> MaskStyle.LIVE_BLUR
    frameCaptured -> MaskStyle.FRAME_BLUR
    else -> MaskStyle.SOLID
}

/** Returns left, top, right, bottom inside a width × height screen, or null when nothing is left to cover. */
internal fun clipToScreen(left: Int, top: Int, right: Int, bottom: Int, width: Int, height: Int): IntArray? {
    val clipped = intArrayOf(left.coerceAtLeast(0), top.coerceAtLeast(0), right.coerceAtMost(width), bottom.coerceAtMost(height))
    return clipped.takeIf { it[0] < it[2] && it[1] < it[3] }
}

/** Separable Gaussian blur of ARGB pixels, sigma = radius / 2; edges repeat the border pixel so nothing is read out of bounds. */
internal fun gaussianBlur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    if (radius <= 0) return pixels.copyOf()
    val sigma = radius / 2.0
    val raw = DoubleArray(2 * radius + 1) { val d = it - radius; kotlin.math.exp(-d * d / (2 * sigma * sigma)) }
    val total = raw.sum()
    val kernel = raw.map { it / total }
    fun pass(source: IntArray, horizontal: Boolean) = IntArray(source.size) { index ->
        val x = index % width
        val y = index / width
        val sums = DoubleArray(4)
        for (k in kernel.indices) {
            val pixel = if (horizontal) source[y * width + (x + k - radius).coerceIn(0, width - 1)]
            else source[(y + k - radius).coerceIn(0, height - 1) * width + x]
            for (channel in 0..3) sums[channel] += kernel[k] * (pixel ushr (24 - channel * 8) and 0xff)
        }
        (0..3).fold(0) { acc, channel -> acc or ((sums[channel] + 0.5).toInt().coerceIn(0, 255) shl (24 - channel * 8)) }
    }
    return pass(pass(pixels, true), false)
}

/**
 * Covers screen rectangles so the content underneath stays recognisable but unreadable, each with a "显示这条" button.
 * The captured frame lives only in memory: it is never written to storage or sent anywhere.
 */
internal class FeedMask(private val service: AccessibilityService) {
    companion object { /** Device tests turn this off to exercise the screenshot path on phones that support live blur. */ var liveBlurAllowed = true }
    private class Request(val rect: Rect, val label: String, val onReveal: () -> Unit)
    private class Shown(val rect: Rect, val views: List<View>, val dialog: Dialog?)
    private val manager = service.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val shown = mutableMapOf<Any, Shown>()
    private val waiting = mutableMapOf<Any, Request>()
    private var generation = 0L
    private var capturing = false
    private var blurBroken = false
    private val blurListener = Consumer<Boolean> { enabled ->
        // Battery saver or the ROM can switch blur off at any time; a blur window without blur would expose the content.
        val windows = shown.values.mapNotNull { it.dialog?.window }
        windows.forEach { tint(it, enabled) }
        if (!enabled && windows.isNotEmpty()) Session.record("遮挡 · 系统关闭实时模糊，已改为不透明")
    }

    init { if (Build.VERSION.SDK_INT >= 31) manager.addCrossWindowBlurEnabledListener(service.mainExecutor, blurListener) }

    operator fun contains(key: Any) = key in shown || key in waiting

    /** Covers [bounds] for [key]; calling again with new bounds moves the cover. */
    fun show(key: Any, bounds: Rect, label: String, onReveal: () -> Unit) {
        val screen = manager.maximumWindowMetrics.bounds
        val clipped = clipToScreen(bounds.left, bounds.top, bounds.right, bounds.bottom, screen.width(), screen.height()) ?: return remove(key)
        val rect = Rect(clipped[0], clipped[1], clipped[2], clipped[3])
        if (shown[key]?.rect == rect || waiting[key]?.rect == rect) return
        remove(key)
        val request = Request(rect, label, onReveal)
        if (maskStyle(blurEnabled(), false) == MaskStyle.LIVE_BLUR && add(key, request, MaskStyle.LIVE_BLUR, null)) return
        waiting[key] = request
        capture()
    }

    fun remove(key: Any) {
        waiting.remove(key)
        val gone = shown.remove(key) ?: return
        gone.views.forEach(manager::removeView)
        gone.dialog?.dismiss()
    }

    fun clear() { generation++; waiting.clear(); shown.keys.toList().forEach(::remove) }

    fun close() {
        clear()
        if (Build.VERSION.SDK_INT >= 31) manager.removeCrossWindowBlurEnabledListener(blurListener)
        worker.shutdownNow()
    }

    private fun blurEnabled() = liveBlurAllowed && !blurBroken && Build.VERSION.SDK_INT >= 31 && manager.isCrossWindowBlurEnabled

    /** The screenshot must be taken before the cover exists, otherwise it would capture the cover itself. */
    private fun capture() {
        if (capturing || waiting.isEmpty()) return
        capturing = true
        val batch = waiting.toList()
        val token = generation
        var done = false
        fun finish(images: Map<Any, Bitmap>, failure: String?) {
            if (done) return
            done = true
            capturing = false
            if (failure != null) Session.record("遮挡 · $failure，改用纯色")
            for ((key, request) in batch) {
                if (token != generation || waiting[key] !== request) continue
                waiting.remove(key)
                val image = images[key]
                add(key, request, maskStyle(false, image != null), image)
            }
            // takeScreenshot rejects calls closer than about 333 ms apart.
            if (waiting.isNotEmpty()) main.postDelayed(::capture, 350)
        }
        main.postDelayed({ finish(emptyMap(), "截图超时") }, 1500)
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        worker.execute {
                            val images = runCatching { blurredFrames(result, batch) }.getOrDefault(emptyMap())
                            main.post { finish(images, if (images.isEmpty()) "截图处理失败" else null) }
                        }
                    } catch (_: RuntimeException) { result.hardwareBuffer.close() }
                }
                override fun onFailure(errorCode: Int) = finish(emptyMap(), "截图失败 · 系统代码 $errorCode")
            })
        } catch (_: RuntimeException) { finish(emptyMap(), "截图不可用") }
    }

    private fun blurredFrames(result: AccessibilityService.ScreenshotResult, batch: List<Pair<Any, Request>>): Map<Any, Bitmap> {
        val hardware = result.hardwareBuffer
        try {
            val wrapped = Bitmap.wrapHardwareBuffer(hardware, result.colorSpace) ?: return emptyMap()
            val frame = try { wrapped.copy(Bitmap.Config.ARGB_8888, false) } finally { wrapped.recycle() }
            try {
                val scale = ProductUi.dp(service, 8)
                return batch.mapNotNull { (key, request) ->
                    val r = request.rect
                    val c = clipToScreen(r.left, r.top, r.right, r.bottom, frame.width, frame.height) ?: return@mapNotNull null
                    // Blur at 1/8 dp scale: sigma 2 there is about 16 dp on screen, close to the live blur and enough to erase large titles.
                    val small = Bitmap.createScaledBitmap(Bitmap.createBitmap(frame, c[0], c[1], c[2] - c[0], c[3] - c[1]), maxOf(1, (c[2] - c[0]) / scale), maxOf(1, (c[3] - c[1]) / scale), true)
                    val pixels = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
                    key to Bitmap.createBitmap(gaussianBlur(pixels, small.width, small.height, 4), small.width, small.height, Bitmap.Config.ARGB_8888)
                }.toMap()
            } finally { frame.recycle() }
        } finally { hardware.close() }
    }

    /** Returns false only when a blur window cannot be shown, so the caller can fall back to a blurred frame. */
    private fun add(key: Any, request: Request, style: MaskStyle, image: Bitmap?): Boolean {
        val views = mutableListOf<View>()
        var dialog: Dialog? = null
        try {
            val content = FrameLayout(service).apply {
                addView(TextView(service).apply {
                    text = "后生已遮挡\n命中你的筛选规则"
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTextColor(service.getColor(R.color.housheng_text))
                    setBackgroundColor(service.getColor(R.color.housheng_primary_container))
                    ProductUi.dp(service, 12).let { setPadding(it, it, it, it) }
                }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            }
            if (style == MaskStyle.LIVE_BLUR) {
                dialog = Dialog(service, android.R.style.Theme_Translucent_NoTitleBar).apply {
                    setContentView(content)
                    window!!.apply {
                        attributes = params(request.rect, false).also { it.token = overlayToken; it.windowAnimations = 0 }
                        tint(this, true)
                    }
                    show()
                }
            } else {
                content.background = image?.let { BitmapDrawable(service.resources, it) } ?: ColorDrawable(service.getColor(R.color.housheng_primary_container))
                manager.addView(content, params(request.rect, false)); views.add(content)
            }
            val height = ProductUi.dp(service, 60)
            val show = ProductUi.button(service, "显示这条") { remove(key); request.onReveal() }
            val r = request.rect
            manager.addView(show, params(Rect(r.left + 16, r.bottom - height - 16, r.right - 16, r.bottom - 16), true)); views.add(show)
            shown[key] = Shown(r, views, dialog)
            Session.record("${request.label} · 已遮挡 · ${style.label}")
            return true
        } catch (_: RuntimeException) {
            views.forEach(manager::removeView)
            runCatching { dialog?.dismiss() }
            if (style == MaskStyle.LIVE_BLUR) { blurBroken = true; Session.record("遮挡 · 模糊窗口不可用，改用截图模糊"); return false }
            Session.record("${request.label} · 遮挡失败，保留原内容")
            return true
        }
    }

    private fun tint(window: Window, blur: Boolean) {
        val color = service.getColor(R.color.housheng_primary_container)
        window.setBackgroundDrawable(ColorDrawable(if (blur) color and 0x00ffffff or 0x40000000 else color))
        if (Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(if (blur) ProductUi.dp(service, 30) else 0)
    }

    /** A Dialog's window manager drops the service's overlay token, so borrow it from a throwaway overlay. */
    private val overlayToken by lazy {
        val probe = View(service)
        val params = params(Rect(0, 0, 1, 1), false)
        manager.addView(probe, params)
        manager.removeView(probe)
        params.token
    }

    private fun params(rect: Rect, touchable: Boolean) = WindowManager.LayoutParams(
        rect.width(), rect.height(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE), PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.LEFT; x = rect.left; y = rect.top }
}
