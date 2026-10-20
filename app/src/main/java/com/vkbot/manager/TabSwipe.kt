package com.vkbot.manager

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.math.abs

/**
 * Смахивание влево / вправо между вкладками нижней панели.
 * Срабатывает только на уверенный горизонтальный жест и не мешает элементам со своими жестами.
 */
object TabSwipe {

    /** Минимальный сдвиг пальца, dp. */
    const val MIN_DISTANCE_DP = 90f
    /** Минимальная скорость, dp/с. */
    const val MIN_VELOCITY_DP = 500f
    /** Жест должен быть во столько раз горизонтальнее, чем вертикальнее (не мешать прокрутке). */
    const val HORIZONTAL_RATIO = 2f

    /** Тег для списков со своими смахиваниями (строки базы, чёрный список). */
    const val NO_TAB_SWIPE = "no_tab_swipe"

    /** +1 — следующая вкладка (палец влево), −1 — предыдущая (палец вправо), 0 — не жест. */
    fun direction(dx: Float, dy: Float, velocityX: Float, density: Float): Int {
        if (abs(dx) < MIN_DISTANCE_DP * density) return 0
        if (abs(dx) < abs(dy) * HORIZONTAL_RATIO) return 0
        if (abs(velocityX) < MIN_VELOCITY_DP * density) return 0
        return if (dx < 0) 1 else -1
    }

    /** Под пальцем элемент со своим горизонтальным жестом — вкладки не переключаем. */
    fun isBlockedAt(root: View, rawX: Int, rawY: Int): Boolean {
        var view: View? = root
        val rect = Rect()
        while (view != null) {
            if (view.tag == NO_TAB_SWIPE || view is EditText || view is CompoundButton || view is SeekBar ||
                view is HorizontalScrollView || view is MaterialButtonToggleGroup) return true
            view = (view as? ViewGroup)?.let { group ->
                (group.childCount - 1 downTo 0).map { group.getChildAt(it) }
                    .firstOrNull { it.isShown && it.getGlobalVisibleRect(rect) && rect.contains(rawX, rawY) }
            }
        }
        return false
    }
}
