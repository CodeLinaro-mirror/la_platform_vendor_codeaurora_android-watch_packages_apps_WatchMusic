/**
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.music

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.WindowManager

import androidx.core.content.withStyledAttributes

import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A custom circular progress bar view for Android Wear devices, designed to visually represent
 * elapsed and total time in a circular arc format.
 *
 *
 * This view draws:
 * - A circular track and progress arc.
 * - Elapsed and total time labels positioned along the arc.
 *
 *
 * Custom attributes (defined in attrs.xml):
 * - `startAngle` (float): Starting angle of the arc (0–180 degrees).
 * - `stroke` (dimension): Width of the arc stroke.
 * - `trackColor` (color): Color of the background track.
 * - `progressColor` (color): Color of the progress arc.
 * - `textSize` (dimension): Size of the time labels.
 * - `textMarginX`, `textMarginY` (dimension): Margins to position the time labels.
 * - `textColor` (color): Color of the time labels.
 *
 *
 * <Usage:
 * - Call `setDuration(durationMillis)` to set the total duration.
 * - Call `setElapsedTime(elapsedMillis)` to update progress.
 *
 *
 * Note:
 * - This view will be drawn in bounds of current window.
 * -    requests measurements of current window size.
 * -    layouts itself to occupy full window.
 * -    if parent view's layout bounds must be same as current window bounds, otherwise progress bar may get cropped.
 *
 *
 * Imp Note:
 * - Any view drawn before this will hide behind this unless transparent background.
 */

class CircularTimeBarView(
    context: Context,
    attributeSet: AttributeSet?
) : View(context, attributeSet) {

    // Attributes
    private var startAngle = DEFAULT_START_ANGLE
    private var arcStrokeWidth = 60f
    private var trackColor = Color.LTGRAY
    private var progressColor = Color.BLACK
    private var innerTextSize = 10f
    private var textMarginX = 0f
    private var textMarginY = 0f
    private var textColor = Color.BLACK

    // Coordinates and bounds
    private var rectF: RectF = RectF()
    private var centerF = PointF()
    private var radiusF = 0f
    private var sweepAngle = 360f - 2 * startAngle
    private val elapsedTimePointF = PointF()
    private val totalTimePointF = PointF()
    private var currentWindowBounds = Rect()

    // Graphics
    private var trackPaint: Paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var progressPaint: Paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Resources
    private val blankText = context.getString(R.string.blank)

    // Data
    private var progress = 0f
    private var elapsedDuration = Duration.ZERO
    private var totalDuration = Duration.ZERO
    private var elapsedTimeText = blankText
    private var totalTimeText = blankText

    init {
        var attrStartAngle = startAngle
        attributeSet?.let {
            context.withStyledAttributes(attributeSet, R.styleable.CircularTimeBarView) {
                attrStartAngle =
                    getFloat(R.styleable.CircularTimeBarView_startAngle, attrStartAngle)
                arcStrokeWidth =
                    getDimension(R.styleable.CircularTimeBarView_stroke, arcStrokeWidth)
                trackColor = getColor(R.styleable.CircularTimeBarView_trackColor, trackColor)
                progressColor =
                    getColor(R.styleable.CircularTimeBarView_progressColor, progressColor)
                innerTextSize =
                    getDimension(R.styleable.CircularTimeBarView_textSize, innerTextSize)
                textMarginX = getDimension(R.styleable.CircularTimeBarView_textMarginX, textMarginX)
                textMarginY = getDimension(R.styleable.CircularTimeBarView_textMarginY, textMarginY)
                textColor = getColor(R.styleable.CircularTimeBarView_textColor, textColor)
            }
        }
        startAngle = (OFFSET_ANGLE + attrStartAngle) % 360f
        sweepAngle = 360f - (2 * attrStartAngle)
        if(sweepAngle <= 0f) {
            throw IllegalStateException("startAngle is out of bounds, limit: {0f, 180f}")
        }
        trackPaint.apply {
            color = trackColor
            strokeWidth = arcStrokeWidth
        }
        progressPaint.apply {
            color = progressColor
            strokeWidth = arcStrokeWidth
        }
        textPaint.apply {
            color = textColor
            textSize = innerTextSize
        }
        val windowMetrics =
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics
        currentWindowBounds = windowMetrics.bounds
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Set this view's size to match the current window size
        setMeasuredDimension(currentWindowBounds.width(), currentWindowBounds.height())
    }

    override fun layout(l: Int, t: Int, r: Int, b: Int) {
        super.layout(
            currentWindowBounds.left,
            currentWindowBounds.top,
            currentWindowBounds.right,
            currentWindowBounds.bottom,
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        val availableWidth = width - paddingRight - paddingLeft
        val availableHeight = height - paddingBottom - paddingTop

        radiusF = availableHeight.coerceAtMost(availableWidth) / 2f - arcStrokeWidth
        centerF.apply {
            x = paddingLeft + (availableWidth / 2f)
            y = paddingTop + (availableHeight / 2f)
        }
        rectF.apply {
            this.left = centerF.x - radiusF
            this.top = centerF.y - radiusF
            this.right = centerF.x + radiusF
            this.bottom = centerF.y + radiusF
        }

        val firstTextRadian = Math.toRadians(startAngle.toDouble())
        val secondTextRadian = Math.toRadians((startAngle + sweepAngle).toDouble())
        elapsedTimePointF.apply {
            x = (centerF.x + (radiusF * cos(firstTextRadian)) + textMarginX).toFloat() // Margin to Move right
            y = (centerF.y + (radiusF * sin(firstTextRadian)) + textMarginY).toFloat() // Margin to Move down
        }

        totalTimePointF.apply {
            x = (centerF.x + (radiusF * cos(secondTextRadian)) - textMarginX).toFloat() // Margin to Move left
            y = (centerF.y + (radiusF * sin(secondTextRadian)) + textMarginY).toFloat() // Margin to Move down
        }
    }

    override fun onDraw(canvas: Canvas) {

        Log.d(TAG, "onDraw: $progress")
        // Draw track
        canvas.drawArc(rectF, startAngle, sweepAngle, false, trackPaint)

        // Draw progress
        val progressAngle = (progress / PROGRESS_MAX_VALUE) * sweepAngle
        canvas.drawArc(rectF, startAngle, progressAngle, false, progressPaint)

        // Draw time texts labels
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(elapsedTimeText, elapsedTimePointF.x, elapsedTimePointF.y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(totalTimeText, totalTimePointF.x, totalTimePointF.y, textPaint)
    }

    fun setDuration(duration: Long) {
        if(duration != totalDuration.inWholeMilliseconds) { // Avoid unnecessary redraws
            elapsedDuration = 0L.milliseconds
            totalDuration = 0L.coerceAtLeast(duration).milliseconds
            elapsedTimeText = formatDuration(elapsedDuration)
            totalTimeText = formatDuration(totalDuration)
            invalidate()
        }
    }

    fun setElapsedTime(time: Long) {
        if(time != elapsedDuration.inWholeMilliseconds) {   // Avoid unnecessary redraws
            elapsedDuration = time.coerceIn(0L, totalDuration.inWholeMilliseconds).milliseconds
            elapsedTimeText = formatDuration(elapsedDuration)
            progress = (elapsedDuration.inWholeMilliseconds.toFloat() / totalDuration.inWholeMilliseconds.toFloat()) * PROGRESS_MAX_VALUE
            invalidate()
        }
    }

    private fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.inWholeSeconds
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        const val TAG = "PROGRESS"

        private const val PROGRESS_MAX_VALUE = 100f
        private const val OFFSET_ANGLE = 90f
        private const val DEFAULT_START_ANGLE = 90f
    }
}
