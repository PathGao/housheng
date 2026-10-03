package io.github.pathgao.housheng

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object ProductUi {
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun page(activity: Activity, title: String, subtitle: String = ""): LinearLayout {
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val gutter = if (resources.configuration.screenWidthDp >= 600) 48 else 24
            setPadding(dp(activity, gutter), dp(activity, 24), dp(activity, gutter), dp(activity, 32))
        }
        val scroll = ScrollView(activity).apply {
            setBackgroundColor(activity.getColor(R.color.housheng_background))
            isFillViewport = true
            addView(body)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            }
        }
        activity.window.setDecorFitsSystemWindows(false)
        activity.setContentView(scroll)
        if (activity !is MainActivity) body.addView(button(activity, "返回首页") { activity.finish() })
        body.addView(text(activity, title, 32f, true).apply { isAccessibilityHeading = true })
        if (subtitle.isNotEmpty()) body.addView(text(activity, subtitle).apply { setTextColor(activity.getColor(R.color.housheng_secondary)) })
        return body
    }

    fun text(context: Context, value: String, size: Float = 18f, bold: Boolean = false): TextView = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(context.getColor(R.color.housheng_text))
        setTypeface(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        setLineSpacing(dp(context, 3).toFloat(), 1.08f)
        setPadding(0, dp(context, 8), 0, dp(context, 8))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    fun button(context: Context, label: String, primary: Boolean = false, action: () -> Unit): Button = Button(context).apply {
        text = label
        textSize = 18f
        isAllCaps = false
        minHeight = dp(context, 56)
        minimumHeight = dp(context, 56)
        setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(context.getColor(R.color.housheng_secondary), context.getColor(if (primary) R.color.housheng_on_accent else R.color.housheng_accent))))
        val shape = GradientDrawable().apply {
            cornerRadius = dp(context, 16).toFloat()
            setColor(context.getColor(if (primary) R.color.housheng_accent else R.color.housheng_surface))
            if (!primary) setStroke(dp(context, 1), context.getColor(R.color.housheng_outline))
        }
        background = RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.housheng_ripple)), shape, null)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 8); bottomMargin = dp(context, 8) }
        setOnClickListener { action() }
    }

    fun card(context: Context, title: String, description: String = ""): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 16))
        background = GradientDrawable().apply { cornerRadius = dp(context, 24).toFloat(); setColor(context.getColor(R.color.housheng_surface)) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 12); bottomMargin = dp(context, 4) }
        addView(text(context, title, 22f, true).apply { isAccessibilityHeading = true })
        if (description.isNotEmpty()) addView(text(context, description))
    }
}
