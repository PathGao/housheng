package io.github.pathgao.housheng

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Visual vocabulary from docs/DESIGN.md. Sizes are sp so text follows the system font scale. */
enum class Type(val sp: Float, val weight: Int, val color: Int) {
    HEADLINE(28f, 700, R.color.housheng_text),
    TITLE(22f, 700, R.color.housheng_text),
    NUMBER(30f, 700, R.color.housheng_text),
    LABEL(18f, 500, R.color.housheng_text),
    BODY(18f, 400, R.color.housheng_text),
    SECTION(16f, 500, R.color.housheng_primary),
    SUPPORT(16f, 400, R.color.housheng_text_secondary)
}

enum class ButtonKind { FILLED, TONAL, OUTLINED, TEXT, DANGER }

class Page(val root: LinearLayout, val scroll: ScrollView, val body: LinearLayout)

/** Side margin in dp. Wide screens cap content at 840dp so rows do not stretch across a tablet. */
fun gutter(widthDp: Int) = if (widthDp < 600) 16 else maxOf(48, (widthDp - 840) / 2)

object ProductUi {
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    /** Above this font scale, side-by-side label/value pairs stack so labels keep whole words on one line. */
    fun largeText(context: Context) = context.resources.configuration.fontScale > 1.3f
    private fun color(context: Context, id: Int) = context.getColor(id)

    fun page(activity: Activity, title: String? = null): Page {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(activity, R.color.housheng_background))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            }
        }
        val gutter = gutter(activity.resources.configuration.screenWidthDp)
        if (title != null) root.addView(LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(activity, 64)
            setPadding(dp(activity, gutter - 4), 0, dp(activity, gutter), 0)
            addView(ImageButton(activity).apply {
                setImageResource(R.drawable.ic_arrow_back)
                imageTintList = ColorStateList.valueOf(color(activity, R.color.housheng_text))
                contentDescription = "返回"
                background = RippleDrawable(ColorStateList.valueOf(color(activity, R.color.housheng_ripple)), null, null)
                layoutParams = LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48))
                setOnClickListener { activity.finish() }
            })
            addView(text(activity, title, Type.TITLE).apply {
                isAccessibilityHeading = true
                setPadding(dp(activity, 8), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
        })
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, gutter), 0, dp(activity, gutter), dp(activity, 24))
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(body)
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(scroll)
        activity.window.setDecorFitsSystemWindows(false)
        activity.setContentView(root)
        return Page(root, scroll, body)
    }

    fun appBarAction(page: Page, label: String, action: () -> Unit): Button =
        button(page.root.context, label, ButtonKind.TEXT, action).also {
            it.layoutParams = LinearLayout.LayoutParams(-2, -2)
            (page.root.getChildAt(0) as LinearLayout).addView(it)
        }

    fun bottomBar(activity: Activity, page: Page, vararg actions: Pair<Button, Float>) {
        page.root.addView(View(activity).apply { setBackgroundColor(color(activity, R.color.housheng_divider)); layoutParams = LinearLayout.LayoutParams(-1, dp(activity, 1)) })
        page.root.addView(LinearLayout(activity).apply {
            setBackgroundColor(color(activity, R.color.housheng_surface))
            val side = dp(activity, gutter(activity.resources.configuration.screenWidthDp))
            setPadding(side, dp(activity, 12), side, dp(activity, 12))
            // Side by side, large text breaks short labels one character per line; stack them instead.
            val stacked = largeText(activity)
            if (stacked) orientation = LinearLayout.VERTICAL
            actions.forEachIndexed { index, (button, weight) ->
                button.layoutParams = if (stacked) LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(activity, 12) }
                else LinearLayout.LayoutParams(0, -2, weight).apply { if (index > 0) marginStart = dp(activity, 12) }
                addView(button)
            }
        })
        activity.window.navigationBarColor = color(activity, R.color.housheng_surface)
    }

    fun brand(activity: Activity): View = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(activity, 72)
        addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_housheng)
            clipToOutline = true
            background = rounded(activity, R.color.housheng_icon_background, 10)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40))
        })
        addView(text(activity, "后生", Type.HEADLINE).apply { isAccessibilityHeading = true; setPadding(dp(activity, 12), 0, 0, 0) })
    }

    fun text(context: Context, value: CharSequence, type: Type = Type.BODY): TextView = TextView(context).apply {
        text = value
        textSize = type.sp
        setTextColor(color(context, type.color))
        typeface = Typeface.create(Typeface.DEFAULT, type.weight, false)
        if (type == Type.NUMBER) fontFeatureSettings = "tnum"
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    fun section(context: Context, value: String): TextView = text(context, value, Type.SECTION).apply {
        isAccessibilityHeading = true
        setPadding(dp(context, 4), dp(context, 20), dp(context, 4), dp(context, 8))
    }

    fun helper(context: Context, value: String): TextView = text(context, value, Type.SUPPORT).apply {
        setPadding(dp(context, 4), dp(context, 8), dp(context, 4), 0)
    }

    fun spacer(context: Context, height: Int = 16) = View(context).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(context, height)) }

    fun rounded(context: Context, fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(context, radius).toFloat()
        setColor(color(context, fill))
        if (stroke != null) setStroke(dp(context, 1), color(context, stroke))
    }

    private fun ripple(context: Context, content: Drawable?) =
        RippleDrawable(ColorStateList.valueOf(color(context, R.color.housheng_ripple)), content, content ?: GradientDrawable().apply { setColor(-1) })

    /** White rounded container. Rows with a 40dp leading icon pass inset = true so dividers start after the icon. */
    fun group(context: Context, inset: Boolean = false): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(context, R.color.housheng_surface, 16)
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        dividerDrawable = InsetDrawable(GradientDrawable().apply {
            setColor(color(context, R.color.housheng_divider)); setSize(1, dp(context, 1))
        }, dp(context, if (inset) 72 else 16), 0, 0, 0)
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    fun twoLine(context: Context, label: String, support: String?): CharSequence = SpannableStringBuilder(label).apply {
        if (!support.isNullOrEmpty()) {
            val start = length
            append("\n").append(support)
            setSpan(RelativeSizeSpan(Type.SUPPORT.sp / Type.LABEL.sp), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(color(context, R.color.housheng_text_secondary)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(TypefaceSpan(Typeface.create(Typeface.DEFAULT, Type.SUPPORT.weight, false)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun leadingIcon(context: Context, icon: Int): ImageView = ImageView(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(color(context, R.color.housheng_primary))
        scaleType = ImageView.ScaleType.CENTER
        background = rounded(context, R.color.housheng_surface_variant, 12)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)).apply { marginEnd = dp(context, 16) }
    }

    fun appIcon(context: Context, packageName: String): Drawable =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrElse {
            android.graphics.drawable.LayerDrawable(arrayOf(rounded(context, R.color.housheng_surface_variant, 10),
                context.getDrawable(R.drawable.ic_apps)!!.mutate().apply { setTint(color(context, R.color.housheng_text_secondary)) })).apply {
                setLayerInset(1, dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            }
        }

    /** Navigation row: [icon] label [value] ›. The whole row is one button for TalkBack. */
    class NavRow(val view: LinearLayout, val label: TextView, val value: TextView)

    fun navRow(context: Context, label: String, icon: Int? = null, value: String = "", support: String? = null, leading: Drawable? = null, action: () -> Unit): NavRow {
        val labelView = text(context, twoLine(context, label, support), Type.LABEL).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val stacked = largeText(context)
        val valueView = text(context, value, Type.SUPPORT).apply {
            if (!stacked) { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { if (!stacked) marginStart = dp(context, 12) }
        }
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 64)
            setPadding(dp(context, 16), dp(context, 12), dp(context, 12), dp(context, 12))
            background = ripple(context, null)
            isClickable = true; isFocusable = true
            accessibilityDelegate = asButton
            if (icon != null) addView(leadingIcon(context, icon))
            if (leading != null) addView(ImageView(context).apply {
                setImageDrawable(leading)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)).apply { marginEnd = dp(context, 16) }
            })
            if (stacked) addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                labelView.layoutParams = LinearLayout.LayoutParams(-1, -2)
                addView(labelView); addView(valueView)
            }) else { addView(labelView); addView(valueView) }
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right)
                imageTintList = ColorStateList.valueOf(color(context, R.color.housheng_text_secondary))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)).apply { marginStart = dp(context, 4) }
            })
            setOnClickListener { action() }
        }
        return NavRow(row, labelView, valueView)
    }

    private val asButton = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = Button::class.java.name
        }
    }

    /** Switch row. The framework Switch spans the row, so the label is its accessible name and the whole row toggles. */
    fun switchRow(context: Context, label: String, support: String? = null, leading: Drawable? = null): Switch = Switch(context).apply {
        text = twoLine(context, label, support)
        textSize = Type.LABEL.sp
        setTextColor(color(context, R.color.housheng_text))
        typeface = Typeface.create(Typeface.DEFAULT, Type.LABEL.weight, false)
        minHeight = dp(context, 64)
        setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
        setTrackResource(R.drawable.switch_track)
        setThumbResource(R.drawable.switch_thumb)
        trackTintList = null; thumbTintList = null
        switchMinWidth = dp(context, 52)
        switchPadding = dp(context, 16)
        showText = false
        background = ripple(context, null)
        if (leading != null) {
            leading.setBounds(0, 0, dp(context, 40), dp(context, 40))
            setCompoundDrawablesRelative(leading, null, null, null)
            compoundDrawablePadding = dp(context, 16)
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    fun button(context: Context, label: String, kind: ButtonKind = ButtonKind.OUTLINED, action: () -> Unit): Button = Button(context).apply {
        text = label
        textSize = Type.LABEL.sp
        typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        isAllCaps = false
        stateListAnimator = null
        minHeight = dp(context, 60); minimumHeight = dp(context, 60)
        setPadding(dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 8))
        val content = when (kind) {
            ButtonKind.FILLED -> R.color.housheng_on_primary
            ButtonKind.DANGER -> R.color.housheng_error
            else -> R.color.housheng_primary
        }
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(color(context, R.color.housheng_disabled), color(context, content))))
        val shape = when (kind) {
            ButtonKind.FILLED -> rounded(context, R.color.housheng_primary, 30)
            ButtonKind.TONAL -> rounded(context, R.color.housheng_primary_container, 30)
            ButtonKind.OUTLINED -> rounded(context, android.R.color.transparent, 30, R.color.housheng_outline)
            ButtonKind.TEXT, ButtonKind.DANGER -> null
        }
        background = ripple(context, shape)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        setOnClickListener { action() }
    }

    /** Single-select segmented control. RadioButtons keep native selection semantics; the checked one also shows ✓. */
    fun segmented(context: Context, options: List<String>, selected: Int, onSelect: (Int) -> Unit): RadioGroup = RadioGroup(context).apply {
        orientation = RadioGroup.HORIZONTAL
        background = rounded(context, android.R.color.transparent, 30, R.color.housheng_outline)
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        dividerDrawable = GradientDrawable().apply { setColor(color(context, R.color.housheng_outline)); setSize(dp(context, 1), 1) }
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        val check = context.getDrawable(R.drawable.ic_check)!!.mutate().apply {
            setTint(color(context, R.color.housheng_primary)); setBounds(0, 0, dp(context, 20), dp(context, 20))
        }
        fun mark(button: RadioButton, label: String) {
            button.text = if (button.isChecked) SpannableStringBuilder("  $label").apply {
                setSpan(ImageSpan(check, ImageSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else label
            button.setTextColor(color(context, if (button.isChecked) R.color.housheng_primary else R.color.housheng_text))
            button.setBackgroundColor(if (button.isChecked) color(context, R.color.housheng_primary_container) else 0)
        }
        options.forEachIndexed { index, option ->
            addView(RadioButton(context).apply {
                id = View.generateViewId()
                text = option
                textSize = Type.LABEL.sp
                typeface = Typeface.create(Typeface.DEFAULT, 500, false)
                buttonDrawable = null
                gravity = Gravity.CENTER
                minHeight = dp(context, 60)
                setPadding(dp(context, 12), 0, dp(context, 12), 0)
                layoutParams = RadioGroup.LayoutParams(0, -2, 1f)
                isChecked = index == selected
                contentDescription = option
                setOnCheckedChangeListener { button, checked -> mark(button as RadioButton, option); if (checked) onSelect(index) }
                mark(this, option)
            })
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    /** Status label: icon + word + tinted container. Never color alone. */
    fun chip(context: Context): TextView = text(context, "", Type.SUPPORT).apply {
        typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        gravity = Gravity.CENTER_VERTICAL
        compoundDrawablePadding = dp(context, 6)
        minHeight = dp(context, 32)
        setPadding(dp(context, 8), dp(context, 4), dp(context, 12), dp(context, 4))
        layoutParams = LinearLayout.LayoutParams(-2, -2)
    }

    fun setChip(chip: TextView, label: String, tone: Tone, icon: Int) {
        val context = chip.context
        chip.text = label
        chip.setTextColor(color(context, tone.content))
        chip.background = rounded(context, tone.container, 8)
        val drawable = context.getDrawable(icon)!!.mutate().apply { setTint(color(context, tone.content)); setBounds(0, 0, dp(context, 20), dp(context, 20)) }
        chip.setCompoundDrawablesRelative(drawable, null, null, null)
    }

    fun metrics(context: Context, items: List<Pair<String, String>>): LinearLayout = if (largeText(context)) LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        items.forEach { (value, label) ->
            addView(LinearLayout(context).apply {
                isBaselineAligned = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = "$label $value"
                addView(text(context, value, Type.NUMBER).apply { layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(context, 12) } })
                addView(text(context, label, Type.SUPPORT).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            })
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    } else LinearLayout(context).apply {
        dividerDrawable = GradientDrawable().apply { setColor(color(context, R.color.housheng_divider)); setSize(dp(context, 1), 1) }
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerPadding = 0
        items.forEachIndexed { index, (value, label) ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                if (index > 0) setPadding(dp(context, 16), 0, 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = "$label $value"
                addView(text(context, value, Type.NUMBER).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
                addView(text(context, label, Type.SUPPORT).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    fun panel(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(context, R.color.housheng_surface, 16)
        setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
}

class StatusPanel(context: Context, private val onAction: () -> Unit) {
    val view = ProductUi.panel(context)
    private val chip = ProductUi.chip(context)
    private val headline = ProductUi.text(context, "", Type.TITLE).apply { setPadding(0, ProductUi.dp(context, 12), 0, ProductUi.dp(context, 4)) }
    private val detail = ProductUi.text(context, "", Type.SUPPORT)
    private val action = ProductUi.button(context, "", ButtonKind.FILLED) { onAction() }.apply {
        (layoutParams as LinearLayout.LayoutParams).topMargin = ProductUi.dp(context, 16)
    }
    /** Extra content under the text, such as metrics. Hidden while an action is required. */
    val footer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        view.addView(chip); view.addView(headline); view.addView(detail); view.addView(action); view.addView(footer)
        view.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    fun show(status: ServiceStatus) {
        val copy = notificationCopy(status)
        ProductUi.setChip(chip, copy.label, status.tone, status.icon)
        headline.text = copy.headline
        detail.text = copy.detail
        action.text = copy.action.orEmpty()
        action.visibility = if (copy.action == null) View.GONE else View.VISIBLE
        footer.visibility = if (copy.action == null) View.VISIBLE else View.GONE
    }
}
