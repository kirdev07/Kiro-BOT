package com.vkbot.manager

import org.junit.Assert.assertEquals
import org.junit.Test

class TabSwipeTest {

    /** Плотность экрана POCO X3 Pro: 1 dp ≈ 2.75 px. */
    private val density = 2.75f
    private fun dp(v: Float) = v * density

    @Test fun confidentHorizontalFlingSwitchesTab() {
        assertEquals("палец влево — следующая вкладка", 1, TabSwipe.direction(-dp(150f), dp(10f), -dp(1500f), density))
        assertEquals("палец вправо — предыдущая", -1, TabSwipe.direction(dp(150f), -dp(20f), dp(1500f), density))
    }

    @Test fun shortSlowOrDiagonalGesturesAreIgnored() {
        assertEquals("короткий", 0, TabSwipe.direction(-dp(40f), 0f, -dp(2000f), density))
        assertEquals("медленный", 0, TabSwipe.direction(-dp(200f), 0f, -dp(200f), density))
        assertEquals("по диагонали — это прокрутка", 0, TabSwipe.direction(-dp(150f), dp(120f), -dp(1500f), density))
        assertEquals("вертикальная прокрутка", 0, TabSwipe.direction(dp(5f), dp(400f), dp(50f), density))
    }
}
