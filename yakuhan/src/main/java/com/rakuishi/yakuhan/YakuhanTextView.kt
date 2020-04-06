package com.rakuishi.yakuhan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import kotlin.math.floor

/**
 * Todo
 * - Support padding(s)
 * - Support marginStart and marginEnd
 * - Expose spacingAddition and spacingMultiplier
 */
class YakuhanTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var text: String = ""
        set(value) {
            field = value
            parseTokens(value)
            requestLayout()
        }
    var textColor: Int = Color.parseColor("#000000")
        set(value) {
            textPaint.color = value
            field = value
        }
    var textSize: Float = sp2px(context, 12f)
        set(value) {
            textPaint.textSize = value
            field = value
        }
    var fontFamily: Typeface = Typeface.DEFAULT
        set(value) {
            textPaint.typeface = Typeface.create(value, textStyle)
            field = value
        }
    var textStyle: Int = Typeface.NORMAL
        set(value) {
            textPaint.typeface = Typeface.create(fontFamily, value)
            field = value
        }
    var maxLines: Int = Int.MAX_VALUE
    var kerningOnlyFirstChar: Boolean = false

    private val textPaint = TextPaint()
    private val leftSpaceStrings = arrayOf("（", "「", "『", "【")
    private val rightSpaceStrings = arrayOf("）", "」", "』", "】", "、", "。")
    private var spacingMultiplier = 1.0f
    private var spacingAddition = 0f
    private var tokens: List<String> = arrayListOf()

    init {
        attrs?.let {
            val array = context.obtainStyledAttributes(it, R.styleable.YakuhanTextView)

            if (array.hasValue(R.styleable.YakuhanTextView_android_text)) {
                text = array.getText(R.styleable.YakuhanTextView_android_text).toString()
                parseTokens(text)
            }
            textSize = array.getDimension(
                R.styleable.YakuhanTextView_android_textSize,
                sp2px(context, 12f)
            )
            textColor = array.getColor(
                R.styleable.YakuhanTextView_android_textColor,
                Color.parseColor("#000000")
            )
            textStyle = array.getInt(R.styleable.YakuhanTextView_android_textStyle, Typeface.NORMAL)

            if (array.hasValue(R.styleable.YakuhanTextView_fontFamily) && !isInEditMode) {
                val fontFamilyId = array.getResourceId(R.styleable.YakuhanTextView_fontFamily, -1)
                fontFamily = ResourcesCompat.getFont(context, fontFamilyId) ?: Typeface.DEFAULT
            }

            if (array.hasValue(R.styleable.YakuhanTextView_android_maxLines)) {
                maxLines = array.getInt(R.styleable.YakuhanTextView_android_maxLines, Int.MAX_VALUE)
            }

            kerningOnlyFirstChar =
                array.getBoolean(R.styleable.YakuhanTextView_kerningOnlyFirstChar, false)

            array.recycle()
        }

        textPaint.also {
            it.isAntiAlias = true
            it.color = textColor
            it.textSize = textSize
            it.typeface = Typeface.create(fontFamily, textStyle)
            it.textAlign = Paint.Align.LEFT
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        readToken(measuredWidth) { x, y, string ->
            canvas.drawText(string, x, y, textPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = measureMaxWidth(widthMeasureSpec)
        setMeasuredDimension(width, measureTextHeight(width))
    }

    private fun measureMaxWidth(widthMeasureSpec: Int): Int {
        return if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(widthMeasureSpec)
        } else {
            val parentView = parent as View
            parentView.measuredWidth - parentView.paddingLeft - paddingRight
        }
    }

    private fun measureTextHeight(maxWidth: Int): Int {
        if (tokens.isEmpty()) return 0
        val numberOfLines = readToken(maxWidth, null)
        val lineHeight = getLineHeight()
        val adjustmentHeight = floor(lineHeight / 10f)
        return (numberOfLines * getLineHeight() + (numberOfLines - 1) * adjustmentHeight).toInt()
    }

    private fun getLineHeight(): Int {
        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain("A", 0, "A".length, textPaint, measuredWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(spacingAddition, spacingMultiplier)
                .setIncludePad(true)
                .build()
        } else {
            StaticLayout(
                "A",
                textPaint,
                measuredWidth,
                Layout.Alignment.ALIGN_NORMAL,
                spacingMultiplier,
                spacingAddition,
                true
            )
        }
        return layout.height
    }

    private fun readToken(
        maxWidth: Int,
        onDrawText: ((Float, Float, String) -> Unit)?
    ): Int {
        var numberOfLines = 1
        var x = 0f
        var y = textSize
        val lineHeight = getLineHeight()
        val adjustmentHeight = floor(lineHeight / 10f)

        tokens.forEachIndexed { index, token ->
            val tokenWidth = textPaint.measureText(token)

            if (x + tokenWidth > maxWidth) {
                if (numberOfLines + 1 > maxLines) return numberOfLines
                numberOfLines++
                x = 0f
                y += (lineHeight + adjustmentHeight)
            }

            var offset = 0
            val maxLength = token.codePointCount(0, token.length)
            while (offset < maxLength) {
                val string = codePointOffsetToString(token, offset)
                val textWidth = textPaint.measureText(string)
                val isLeftSpaceString = leftSpaceStrings.contains(string)
                val isRightSpaceString = rightSpaceStrings.contains(string)
                val kerningWidth =
                    if (isLeftSpaceString || isRightSpaceString) textWidth / 2f else 0f
                val applyKerning =
                    !kerningOnlyFirstChar || (kerningOnlyFirstChar && index == 0 && offset == 0)

                if (applyKerning && isLeftSpaceString) {
                    x -= kerningWidth
                }

                onDrawText?.invoke(x, y, string)

                if (applyKerning && isRightSpaceString) {
                    x -= kerningWidth
                }

                x += textWidth
                offset++
            }
        }

        return numberOfLines
    }

    private fun parseTokens(text: String) {
        val tokens = arrayListOf<String>()

        var offset = 0
        val maxLength = text.codePointCount(0, text.length)
        while (offset < maxLength) {
            if (isLetter(text[offset])) {
                // Save Alphabet words
                var token = ""
                do {
                    token += codePointOffsetToString(text, offset)
                    offset++
                } while (offset < maxLength && isLetter(text[offset]))
                tokens.add(token)
            } else if (offset + 1 < maxLength && isSpaceString(text[offset + 1])) {
                // "「こんにちは。」" to "「", "こ", "ん", "に", "ち", "は。」"
                var token = ""
                do {
                    token += codePointOffsetToString(text, offset)
                    offset++
                } while (offset < maxLength && isSpaceString(text[offset]))
                tokens.add(token)
            } else {
                tokens.add(codePointOffsetToString(text, offset))
                offset++
            }
        }

        this.tokens = tokens
    }

    private fun codePointOffsetToString(text: String, codePointOffset: Int): String {
        val codePoint = text.codePointAt(text.offsetByCodePoints(0, codePointOffset))
        return String(Character.toChars(codePoint))
    }

    private fun isLetter(char: Char): Boolean {
        return char in 'a'..'z' || char in 'A'..'Z' || char == '_'
    }

    private fun isSpaceString(char: Char): Boolean {
        val string = char.toString()
        return leftSpaceStrings.contains(string) || rightSpaceStrings.contains(string)
    }

    @Suppress("SameParameterValue")
    private fun sp2px(context: Context, sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }
}