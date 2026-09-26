package com.vkbot.manager

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.Html
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vkbot.manager.databinding.ActivityMainBinding
import com.vkbot.manager.databinding.NavHeaderBinding
import com.vkbot.manager.utils.BlacklistManager
import com.vkbot.manager.utils.NotificationPermissionHelper
import com.vkbot.manager.utils.PermissionHelper

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var toggle: ActionBarDrawerToggle? = null
    
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREF_BOT_RUNNING) {
            runOnUiThread { updateDrawerHeaderStatus() }
        }
    }
    
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
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
        }
        
        BlacklistManager.init(this)
        
        setupSystemBars()
        setupNavigation()
        checkAndRequestPermissions()
    }
    
    private fun setupSystemBars() {
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg_main)
    }
    
    override fun onResume() {
        super.onResume()
        updateDrawerHeaderStatus()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun setupNavigation() {
        binding.drawerLayout.let { drawer ->
            toggle = ActionBarDrawerToggle(
                this, drawer, R.string.app_name, R.string.app_name
            )
            drawer.addDrawerListener(toggle!!)
            toggle?.syncState()
        }
        
        // Настройка цветов меню
        val navColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(ContextCompat.getColor(this, R.color.white), ContextCompat.getColor(this, R.color.text_medium))
        )
        
        binding.navigationView.apply {
            itemIconTintList = navColors
            itemTextColor = navColors
            
            setNavigationItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_dev_vk -> {
                        startActivity(Intent(Intent.ACTION_VIEW, "https://vk.com/kirdev_07".toUri()))
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    R.id.nav_dev_telegram -> {
                        startActivity(Intent(Intent.ACTION_VIEW, "https://t.me/kirdev_studio".toUri()))
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    else -> {
                        val fragment = when (item.itemId) {
                            R.id.nav_home -> HomeFragment()
                            R.id.nav_bots -> BotsFragment()
                            R.id.nav_logs -> LogsFragment()
                            R.id.nav_editor -> AnswersEditorFragment()
                            R.id.nav_ai -> AiFragment()
                            R.id.nav_blacklist -> BlacklistFragment()
                            R.id.nav_settings -> SettingsFragment()
                            else -> null
                        }
                        
                        fragment?.let {
                            loadFragment(it)
                            binding.drawerLayout.closeDrawer(GravityCompat.START)
                        }
                    }
                }
                true
            }

            // Инициализация первого экрана
            if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
                loadFragment(HomeFragment())
                setCheckedItem(R.id.nav_home)
            }
        }
            
        updateDrawerHeaderStatus()
    }

    /**
     * Обновление статуса в шапке Drawer через View Binding
     */
    private fun updateDrawerHeaderStatus() {
        val headerBinding = NavHeaderBinding.bind(binding.navigationView.getHeaderView(0))
        val isRunning = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_BOT_RUNNING, false)
        
        headerBinding.tvBotStatus.apply {
            text = if (isRunning) getString(R.string.status_running) else getString(R.string.status_stopped)
            val color = ContextCompat.getColor(context, if (isRunning) R.color.status_running_green_light else R.color.accent_error)
            setTextColor(color)
            headerBinding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
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
        if (toggle?.onOptionsItemSelected(item) == true) return true
        
        return when (item.itemId) {
            R.id.menu_instruction -> {
                showInstructionDialog()
                true
            }
            R.id.menu_about -> {
                showAboutDialog()
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
            .show()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "vk_bot_settings"
        private const val PREF_BOT_RUNNING = "bot_running"
        private const val PREF_PERM_NOTIFS = "perm_requested_notifications"
    }
}