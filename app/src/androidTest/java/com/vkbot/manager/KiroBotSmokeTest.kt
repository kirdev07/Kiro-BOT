package com.vkbot.manager

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vkbot.manager.utils.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Android device/emulator smoke tests. No real Telegram token or network calls. */
@RunWith(AndroidJUnit4::class)
class KiroBotSmokeTest {
    @Test
    fun applicationStartsAndInitializesSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.applicationContext is KiroBotApplication)
        assertEquals("Kiro Bot", context.getString(R.string.app_name))
        // The application must initialize SettingsManager before screens/services use it.
        assertNotNull(SettingsManager.chatPrefix)
    }

    @Test
    fun settingsPersistUsingExistingPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("vkbot_global_settings", Context.MODE_PRIVATE)
        val key = "pref_chats_enabled"
        val existed = prefs.contains(key)
        val old = prefs.getBoolean(key, false)
        try {
            SettingsManager.isChatsEnabled = !old
            assertEquals(!old, prefs.getBoolean(key, old))
            SettingsManager.init(context)
            assertEquals(!old, SettingsManager.isChatsEnabled)
        } finally {
            if (existed) prefs.edit().putBoolean(key, old).commit()
            else prefs.edit().remove(key).commit()
            SettingsManager.init(context)
        }
    }

    @Test
    fun mainActivityCanBeCreated() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(!activity.isFinishing)
                assertNotNull(activity.window.decorView)
            }
        }
    }
}
