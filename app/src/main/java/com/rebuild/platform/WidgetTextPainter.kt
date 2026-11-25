package com.rebuild.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import com.rebuild.R
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** The app's faces, as the Compose theme uses them: Archivo for words, Archivo Numerals for figures read at a glance. */
enum class WidgetFace { REGULAR, MEDIUM, BOLD, NUMERALS }

/**
 * How one piece of widget text is set: [size] in sp (or dp when [fixed], for marks that must not grow with text, whose
 * meaning is also spoken), its face, tracking in em, and whether it is shown in capitals.
 */
data class WidgetTextStyle(
    val size: Float,
    val face: WidgetFace = WidgetFace.REGULAR,
    val tracking: Float = 0f,
    val caps: Boolean = false,
    val fixed: Boolean = false
)

/**
 * Draws widget text in the app's own fonts.
 *
 * A launcher inflates widget layouts in a restricted context that ignores app font resources (an `@font/archivo`
 * TextView silently falls back to Roboto, as the notification shade does; see `design-council/app-fonts`). So each
 * string is set here, in the app's process, as a white alpha mask; the layout's ImageView tints it with a theme colour
 * resource. The launcher resolves that tint in its own light or dark configuration, so theme switches stay live without
 * a re-render. Masks are cached by content, so the same text shared by several size variants is one Bitmap instance and
 * travels once in the RemoteViews parcel.
 */
class WidgetTextPainter(private val context: Context) {
    private val metrics = context.resources.displayMetrics
    private val locale: Locale = context.resources.configuration.locales[0]
    private val cache = HashMap<Key, Bitmap>()

    private val faces: Map<WidgetFace, Typeface> = mapOf(
        WidgetFace.REGULAR to font(R.font.archivo_regular),
        WidgetFace.MEDIUM to font(R.font.archivo_medium),
        WidgetFace.BOLD to font(R.font.archivo_bold),
        WidgetFace.NUMERALS to font(R.font.archivo_numerals)
    )

    private fun font(id: Int): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.DEFAULT

    private data class Key(val text: String, val style: WidgetTextStyle, val maxWidthPx: Int, val maxLines: Int)

    fun px(dp: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics)

    fun dp(px: Int): Float = px / metrics.density

    private fun paint(style: WidgetTextStyle): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = faces.getValue(style.face)
        // Sizes in sp follow the user's text size, including Android 14's non-linear scaling.
        textSize = TypedValue.applyDimension(
            if (style.fixed) TypedValue.COMPLEX_UNIT_DIP else TypedValue.COMPLEX_UNIT_SP, style.size, metrics
        )
        letterSpacing = style.tracking
        fontFeatureSettings = "tnum"
        color = Color.WHITE
    }

    /** Width in dp that [text] needs on one line, without drawing it. */
    fun measure(text: String, style: WidgetTextStyle): Float =
        dp(ceil(paint(style).measureText(shown(text, style))).toInt())

    private fun shown(text: String, style: WidgetTextStyle) = if (style.caps) text.uppercase(locale) else text

    /**
     * [text] set in [style], wrapped to [maxWidthDp] over at most [maxLines] lines and ellipsized after that. The bitmap
     * is as wide as the longest line actually set, so wrap_content views keep their natural width.
     */
    fun render(text: String, style: WidgetTextStyle, maxWidthDp: Float, maxLines: Int = 1): Bitmap {
        val maxWidthPx = px(maxWidthDp).toInt().coerceAtLeast(1)
        return cache.getOrPut(Key(text, style, maxWidthPx, maxLines)) {
            val content = shown(text, style)
            val paint = paint(style)
            val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, maxWidthPx)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            var width = 1f
            for (line in 0 until layout.lineCount) width = max(width, layout.getLineWidth(line))
            val bitmap = Bitmap.createBitmap(
                ceil(width).toInt().coerceIn(1, maxWidthPx), layout.height.coerceAtLeast(1), Bitmap.Config.ALPHA_8
            )
            bitmap.density = metrics.densityDpi
            layout.draw(Canvas(bitmap))
            bitmap
        }
    }
}
