package com.rpgmvpviewer.app

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * Простой ImageView с поддержкой pinch-to-zoom, панорамирования и двойного тапа для увеличения.
 * Когда изображение не увеличено, горизонтальные жесты не перехватываются —
 * это позволяет ViewPager2 листать изображения свайпом.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrix2 = Matrix()
    private var minScale = 1f
    private var maxScale = 5f
    private var currentScale = 1f

    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        resetMatrix()
    }

    private fun resetMatrix() {
        post {
            val d = drawable ?: return@post
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            val dWidth = d.intrinsicWidth.toFloat()
            val dHeight = d.intrinsicHeight.toFloat()
            if (viewWidth <= 0f || viewHeight <= 0f || dWidth <= 0f || dHeight <= 0f) return@post

            val scale = minOf(viewWidth / dWidth, viewHeight / dHeight)
            minScale = scale
            currentScale = scale
            val dx = (viewWidth - dWidth * scale) / 2f
            val dy = (viewHeight - dHeight * scale) / 2f

            matrix2.reset()
            matrix2.postScale(scale, scale)
            matrix2.postTranslate(dx, dy)
            imageMatrix = matrix2
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
            val factor = if (currentScale != 0f) newScale / currentScale else 1f
            currentScale = newScale
            matrix2.postScale(factor, factor, detector.focusX, detector.focusY)
            imageMatrix = matrix2
            fixTranslation()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > minScale * 1.1f) {
                resetMatrix()
            } else {
                val targetScale = (minScale * 2.5f).coerceAtMost(maxScale)
                val factor = targetScale / currentScale
                currentScale = targetScale
                matrix2.postScale(factor, factor, e.x, e.y)
                imageMatrix = matrix2
                fixTranslation()
            }
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (currentScale > minScale * 1.02f) {
                matrix2.postTranslate(-distanceX, -distanceY)
                imageMatrix = matrix2
                fixTranslation()
                return true
            }
            return false
        }
    }

    private fun fixTranslation() {
        val d = drawable ?: return
        val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        matrix2.mapRect(rect)

        var dx = 0f
        var dy = 0f
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (rect.width() <= viewWidth) {
            dx = (viewWidth - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0) dx = -rect.left
            else if (rect.right < viewWidth) dx = viewWidth - rect.right
        }

        if (rect.height() <= viewHeight) {
            dy = (viewHeight - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0) dy = -rect.top
            else if (rect.bottom < viewHeight) dy = viewHeight - rect.bottom
        }

        matrix2.postTranslate(dx, dy)
        imageMatrix = matrix2
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleGestureDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled || super.onTouchEvent(event)
    }
}
