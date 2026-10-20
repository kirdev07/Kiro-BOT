package com.vkbot.manager

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.Html
import android.util.Log
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vkbot.manager.databinding.ActivityMainBinding
import com.vkbot.manager.utils.BlacklistManager
import com.vkbot.manager.utils.NotificationPermissionHelper
import com.vkbot.manager.utils.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Блокировка ориентации на телефонах для стабильности UI
        requestedOrientation = if (!resources.getBoolean(R.bool.is_tablet)) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            show()
            title = getString(R.string.app_name)
        }

        BlacklistManager.init(this)

        setupSystemBars()
        setupNavigation()
        checkAndRequestPermissions()
    }

    private fun setupSystemBars() {
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg_card)
    }

    /** Порядок вкладок нижней панели — для смахивания и направления анимации. */
    private val tabs = listOf(R.id.nav_home, R.id.nav_editor, R.id.nav_ai, R.id.nav_logs, R.id.nav_settings)

    private val swipeDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                if (swipeBlocked) return false
                val dir = TabSwipe.direction(e2.rawX - start.rawX, e2.rawY - start.rawY, velocityX, resources.displayMetrics.density)
                return when {
                    dir == 0 -> false
                    // Во вложенном экране смахивание вправо — «назад»
                    supportFragmentManager.backStackEntryCount > 0 -> dir < 0 && supportFragmentManager.popBackStackImmediate()
                    else -> {
                        val next = tabs.indexOf(binding.bottomNavigation.selectedItemId) + dir
                        if (next in tabs.indices) binding.bottomNavigation.selectedItemId = tabs[next]
                        next in tabs.indices
                    }
                }
            }
        })
    }
    private var swipeBlocked = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            swipeBlocked = TabSwipe.isBlockedAt(binding.fragmentContainer, ev.rawX.toInt(), ev.rawY.toInt())
        }
        swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_editor -> AnswersEditorFragment()
                R.id.nav_ai -> AiFragment()
                R.id.nav_logs -> LogsFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> return@setOnItemSelectedListener false
            }
            // Вложенные экраны (чёрный список) закрываются при переходе в другой раздел
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            val forward = tabs.indexOf(item.itemId) > tabs.indexOf(binding.bottomNavigation.selectedItemId)
            loadFragment(fragment, if (forward) 1 else -1)
            true
        }
        binding.bottomNavigation.setOnItemReselectedListener { }
        supportFragmentManager.addOnBackStackChangedListener { updateUpButton() }

        // «Главная» уже выбрана в панели по умолчанию — повторный выбор слушателя не вызовет, грузим сами
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            loadFragment(HomeFragment())
        }
    }

    /** Открыть раздел нижней панели (например, с главной). */
    fun openTab(itemId: Int) {
        binding.bottomNavigation.selectedItemId = itemId
    }

    /** Вложенный экран поверх раздела — «назад» вернёт обратно. */
    fun openSubScreen(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun updateUpButton() {
        supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
    }

    /** [direction]: 1 — вкладка правее (въезжает справа), −1 — левее, 0 — без анимации. */
    private fun loadFragment(fragment: Fragment, direction: Int = 0) {
        val transaction = supportFragmentManager.beginTransaction()
        when (direction) {
            1 -> transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            -1 -> transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        transaction.replace(R.id.fragment_container, fragment).commit()
    }

    private fun checkAndRequestPermissions() {
        if (!PermissionHelper.hasStoragePermissions()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.storage_permission_title)
                .setMessage(getString(R.string.storage_permission_message, Environment.getExternalStorageDirectory().path))
                .setPositiveButton(R.string.next) { _, _ ->
                    PermissionHelper.requestStoragePermissions(this)
                }
                .setCancelable(false)
                .show()
            return
        }

        if (!NotificationPermissionHelper.areNotificationsEnabled(this)) {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val requestedBefore = prefs.getBoolean(PREF_PERM_NOTIFS, false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationPermissionHelper.hasNotificationPermission(this)) {
                if (NotificationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.notifications_permission_title)
                        .setMessage(NotificationPermissionHelper.getPermissionExplanation())
                        .setPositiveButton(R.string.allow) { _, _ ->
                            NotificationPermissionHelper.requestNotificationPermission(this)
                            prefs.edit { putBoolean(PREF_PERM_NOTIFS, true) }
                        }
                        .setNegativeButton(R.string.later, null)
                        .show()
                } else {
                    if (requestedBefore) {
                        showNotificationSettingsDialog()
                    } else {
                        NotificationPermissionHelper.requestNotificationPermission(this)
                        prefs.edit { putBoolean(PREF_PERM_NOTIFS, true) }
                    }
                }
            } else {
                showNotificationSettingsDialog()
            }
        }

        requestBatteryOptimizationExemption()
    }

    private fun showNotificationSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notifications_disabled_title)
            .setMessage(R.string.notifications_disabled_message)
            .setPositiveButton(R.string.settings) { _, _ ->
                NotificationPermissionHelper.openNotificationSettings(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            NotificationPermissionHelper.REQUEST_CODE_NOTIFICATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.notifications_permission_granted, Toast.LENGTH_SHORT).show()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (!shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                        showNotificationSettingsDialog()
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PermissionHelper.REQUEST_CODE_MANAGE_STORAGE) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, R.string.storage_access_granted, Toast.LENGTH_SHORT).show()
                checkAndRequestPermissions()
            } else {
                Toast.makeText(this, R.string.storage_access_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (ex: Exception) {
                    Log.e(TAG, "Could not request battery exemption", ex)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                supportFragmentManager.popBackStack()
                true
            }
            R.id.menu_contact_telegram -> {
                startActivity(Intent(Intent.ACTION_VIEW, "https://t.me/kirdev_studio".toUri()))
                true
            }
            R.id.menu_instruction -> {
                showInstructionDialog()
                true
            }
            R.id.menu_about -> {
                showAboutDialog()
                true
            }
            R.id.menu_privacy -> {
                showPrivacyDialog()
                true
            }
            R.id.menu_website -> {
                openWebsite()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showInstructionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.instruction_title)
            .setMessage(Html.fromHtml(getString(R.string.instruction_text), Html.FROM_HTML_MODE_COMPACT))
            .setPositiveButton(R.string.understood, null)
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bot_description_title)
            .setMessage(Html.fromHtml(getString(R.string.bot_description_text, BuildConfig.VERSION_NAME), Html.FROM_HTML_MODE_COMPACT))
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.action_website) { _, _ -> openWebsite() }
            .show()
    }

    private fun showPrivacyDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.privacy_title)
            .setMessage(Html.fromHtml(getString(R.string.privacy_text), Html.FROM_HTML_MODE_COMPACT))
            .setPositiveButton(R.string.understood, null)
            .setNeutralButton(R.string.action_website) { _, _ -> openWebsite() }
            .show()
    }

    /** Сайт разработчика — там же связь. */
    private fun openWebsite() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, WEBSITE_URL.toUri()))
        } catch (e: Exception) {
            Toast.makeText(this, WEBSITE_URL, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val WEBSITE_URL = "https://kirdev-studio.vercel.app/"
        private const val PREFS_NAME = "vk_bot_settings"
        private const val PREF_PERM_NOTIFS = "perm_requested_notifications"
    }
}