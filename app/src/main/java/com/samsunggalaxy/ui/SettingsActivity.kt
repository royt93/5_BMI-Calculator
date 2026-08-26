package com.samsunggalaxy.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
import com.samsunggalaxy.health.HealthConnectManager
import com.samsunggalaxy.health.HealthConnectSyncScheduler
import com.samsunggalaxy.widget.WidgetUpdateHelper
import com.samsunggalaxy.notification.ReminderScheduler
import com.samsunggalaxy.sdkadbmob.UIUtils
import com.samsunggalaxy.utils.LocaleHelper
import com.samsunggalaxy.utils.PreferencesManager
import com.samsunggalaxy.utils.ThemeHelper
import com.samsunggalaxy.utils.UnitFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

class SettingsActivity : BaseActivity() {
    private lateinit var tvCurrentLanguage: TextView
    private lateinit var prefs: PreferencesManager
    private lateinit var repository: BmiRepository
    private lateinit var toggleUnitSystem: MaterialButtonToggleGroup
    private lateinit var toggleTheme: MaterialButtonToggleGroup
    private lateinit var switchReminder: MaterialSwitch
    private lateinit var reminderTimeContainer: View
    private lateinit var tvReminderTime: TextView
    private lateinit var switchHealthConnect: MaterialSwitch
    private lateinit var tvHealthConnectStatus: TextView
    private lateinit var healthConnectInstallContainer: View
    private lateinit var healthConnectSyncNowContainer: View

    // Guards against the listener re-persisting the value it just read while
    // programmatically checking a button to reflect the stored preference.
    private var suppressUnitListener = false
    private var suppressThemeListener = false
    private var suppressReminderListener = false
    private var suppressHealthConnectListener = false
    private var reminderHour = 8
    private var reminderMinute = 0

    // EPIC-08 T08.1 — must be registered before STARTED (class-body init, not inside a click
    // handler) per ActivityResultContracts contract.
    private val requestNotificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableReminder()
        } else {
            suppressReminderListener = true
            switchReminder.isChecked = false
            suppressReminderListener = false
            Toast.makeText(this, getString(R.string.reminder_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    // EPIC-09 T09.2 — must be registered before STARTED, same constraint as the notification
    // permission request above.
    private val requestHealthConnectPermissions = registerForActivityResult(
        HealthConnectManager.requestPermissionsContract()
    ) { granted ->
        if (granted.containsAll(HealthConnectManager.requiredPermissions())) {
            enableHealthConnectSync()
        } else {
            suppressHealthConnectListener = true
            switchHealthConnect.isChecked = false
            suppressHealthConnectListener = false
            Toast.makeText(this, getString(R.string.health_connect_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIUtils.setupEdgeToEdge1(window)
        setContentView(R.layout.a_settings)
        UIUtils.setupEdgeToEdge2(
            rootView = findViewById(R.id.layoutRoot),
            paddingTop = true,
            paddingBottom = true
        )

        prefs = PreferencesManager(this)
        val database = AppDatabase.getDatabase(this)
        repository = BmiRepository(database.bmiDao(), database.profileDao(), database.bodyMeasurementDao())

        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage)
        toggleUnitSystem = findViewById(R.id.toggleUnitSystem)
        toggleTheme = findViewById(R.id.toggleTheme)
        switchReminder = findViewById(R.id.switchReminder)
        reminderTimeContainer = findViewById(R.id.reminderTimeContainer)
        tvReminderTime = findViewById(R.id.tvReminderTime)
        switchHealthConnect = findViewById(R.id.switchHealthConnect)
        tvHealthConnectStatus = findViewById(R.id.tvHealthConnectStatus)
        healthConnectInstallContainer = findViewById(R.id.healthConnectInstallContainer)
        healthConnectSyncNowContainer = findViewById(R.id.healthConnectSyncNowContainer)

        findViewById<View>(R.id.ivBack)?.setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.languageContainer)?.setOnClickListener {
            showLanguageBottomSheet()
        }

        supportFragmentManager.setFragmentResultListener(
            LanguageBottomSheet.REQUEST_KEY,
            this
        ) { _, bundle ->
            val langCode = bundle.getString(LanguageBottomSheet.RESULT_LANGUAGE) ?: "en"
            LocaleHelper.setLanguage(this, langCode)
            restartApp(this)
        }

        setupUnitToggle()
        setupThemeToggle()
        setupClearHistory()
        setupReminder()
        setupHealthConnect()
        updateLanguageDisplay()
        loadPersistedToggleStates()
    }

    override fun onResume() {
        super.onResume()
        updateLanguageDisplay()
    }

    private fun updateLanguageDisplay() {
        val currentLanguage = LocaleHelper.getLanguage(this)
        tvCurrentLanguage.text = Languages.displayName(currentLanguage)
    }

    private fun showLanguageBottomSheet() {
        val bottomSheet = LanguageBottomSheet.newInstance()
        bottomSheet.show(supportFragmentManager, LanguageBottomSheet.TAG)
    }

    private fun loadPersistedToggleStates() {
        lifecycleScope.launch(Dispatchers.IO) {
            val unitSystem = prefs.unitSystem.first()
            val themeMode = prefs.themeMode.first()
            val reminderEnabled = prefs.reminderEnabled.first()
            reminderHour = prefs.reminderHour.first()
            reminderMinute = prefs.reminderMinute.first()
            val healthConnectEnabled = prefs.healthConnectSyncEnabled.first()
            val lastSync = prefs.lastHealthConnectSyncTimestamp.first()
            withContext(Dispatchers.Main) {
                suppressUnitListener = true
                toggleUnitSystem.check(
                    if (unitSystem == UnitFormatter.IMPERIAL) R.id.btnUnitImperial else R.id.btnUnitMetric
                )
                suppressUnitListener = false

                suppressThemeListener = true
                toggleTheme.check(
                    when (themeMode) {
                        ThemeHelper.LIGHT -> R.id.btnThemeLight
                        ThemeHelper.DARK -> R.id.btnThemeDark
                        else -> R.id.btnThemeSystem
                    }
                )
                suppressThemeListener = false

                suppressReminderListener = true
                switchReminder.isChecked = reminderEnabled
                suppressReminderListener = false
                reminderTimeContainer.visibility = if (reminderEnabled) View.VISIBLE else View.GONE
                updateReminderTimeText()

                suppressHealthConnectListener = true
                switchHealthConnect.isChecked = healthConnectEnabled
                suppressHealthConnectListener = false
                updateHealthConnectUi(healthConnectEnabled, lastSync)
            }
        }
    }

    private fun updateReminderTimeText() {
        tvReminderTime.text = String.format(java.util.Locale.US, "%02d:%02d", reminderHour, reminderMinute)
    }

    /** EPIC-08 T08.1 — opt-in daily reminder; off by default to avoid the "notification spam
     * → uninstall" risk the epic doc flags, with an easy toggle-off in this same screen. */
    private fun setupReminder() {
        switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (suppressReminderListener) return@setOnCheckedChangeListener
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    enableReminder()
                }
            } else {
                reminderTimeContainer.visibility = View.GONE
                lifecycleScope.launch(Dispatchers.IO) {
                    prefs.setReminderEnabled(false)
                    ReminderScheduler.cancel(this@SettingsActivity)
                }
            }
        }

        reminderTimeContainer.setOnClickListener {
            android.app.TimePickerDialog(
                this,
                { _: TimePicker, hour: Int, minute: Int ->
                    reminderHour = hour
                    reminderMinute = minute
                    updateReminderTimeText()
                    lifecycleScope.launch(Dispatchers.IO) {
                        prefs.setReminderTime(hour, minute)
                        ReminderScheduler.schedule(this@SettingsActivity, hour, minute)
                    }
                },
                reminderHour,
                reminderMinute,
                true
            ).show()
        }
    }

    private fun enableReminder() {
        reminderTimeContainer.visibility = View.VISIBLE
        updateReminderTimeText()
        lifecycleScope.launch(Dispatchers.IO) {
            prefs.setReminderEnabled(true)
            ReminderScheduler.schedule(this@SettingsActivity, reminderHour, reminderMinute)
        }
    }

    /**
     * EPIC-09 T09.2 — per the resolved UX decision, the toggle+row always shows (never hidden)
     * even when Health Connect isn't installed; that state instead disables the switch and
     * surfaces an "Install Health Connect" row that deep-links to the Play Store listing.
     */
    private fun setupHealthConnect() {
        val available = HealthConnectManager.isAvailable(this)
        switchHealthConnect.isEnabled = available
        healthConnectInstallContainer.visibility = if (available) View.GONE else View.VISIBLE

        switchHealthConnect.setOnCheckedChangeListener { _, isChecked ->
            if (suppressHealthConnectListener) return@setOnCheckedChangeListener
            if (isChecked) {
                lifecycleScope.launch {
                    if (HealthConnectManager.hasAllPermissions(this@SettingsActivity)) {
                        enableHealthConnectSync()
                    } else {
                        requestHealthConnectPermissions.launch(HealthConnectManager.requiredPermissions())
                    }
                }
            } else {
                healthConnectSyncNowContainer.visibility = View.GONE
                lifecycleScope.launch(Dispatchers.IO) {
                    prefs.setHealthConnectSyncEnabled(false)
                    HealthConnectSyncScheduler.cancel(this@SettingsActivity)
                }
            }
        }

        healthConnectInstallContainer.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, HealthConnectManager.installProviderIntentUri()))
        }

        healthConnectSyncNowContainer.setOnClickListener { syncHealthConnectNow() }
    }

    private fun enableHealthConnectSync() {
        healthConnectSyncNowContainer.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            prefs.setHealthConnectSyncEnabled(true)
            HealthConnectSyncScheduler.schedule(this@SettingsActivity)
        }
        syncHealthConnectNow()
    }

    private fun syncHealthConnectNow() {
        lifecycleScope.launch(Dispatchers.IO) {
            val profileId = repository.getCurrentProfile()?.id ?: return@launch
            val result = HealthConnectManager.syncNow(this@SettingsActivity, repository, profileId)
            // Only a real Success counts as "synced" — recording the timestamp/showing the
            // "Last synced" state for Unavailable/MissingPermissions/Failed would tell the user
            // sync is healthy when it silently didn't run (e.g. permission revoked from system
            // Settings without the in-app toggle being touched).
            var lastSyncMs: Long? = prefs.lastHealthConnectSyncTimestamp.first()
            if (result is HealthConnectManager.SyncResult.Success) {
                lastSyncMs = System.currentTimeMillis()
                prefs.setLastHealthConnectSyncTimestamp(lastSyncMs)
            }
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                updateHealthConnectUi(enabled = true, lastSyncMs = lastSyncMs)
                when (result) {
                    is HealthConnectManager.SyncResult.Success -> Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.health_connect_sync_success, result.imported, result.exported, result.updated),
                        Toast.LENGTH_SHORT
                    ).show()
                    is HealthConnectManager.SyncResult.Failed -> Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.health_connect_sync_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    HealthConnectManager.SyncResult.MissingPermissions -> Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.health_connect_permission_denied),
                        Toast.LENGTH_SHORT
                    ).show()
                    else -> Unit
                }
            }
        }
    }

    private fun updateHealthConnectUi(enabled: Boolean, lastSyncMs: Long?) {
        healthConnectSyncNowContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        tvHealthConnectStatus.text = when {
            !HealthConnectManager.isAvailable(this) -> getString(R.string.health_connect_unavailable_message)
            lastSyncMs == null -> getString(R.string.health_connect_never_synced)
            else -> {
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSyncMs))
                getString(R.string.health_connect_last_sync, time)
            }
        }
    }

    private fun setupUnitToggle() {
        toggleUnitSystem.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (suppressUnitListener || !isChecked) return@addOnButtonCheckedListener
            val system = if (checkedId == R.id.btnUnitImperial) UnitFormatter.IMPERIAL else UnitFormatter.METRIC
            lifecycleScope.launch(Dispatchers.IO) { prefs.setUnitSystem(system) }
        }
    }

    private fun setupThemeToggle() {
        toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (suppressThemeListener || !isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.btnThemeLight -> ThemeHelper.LIGHT
                R.id.btnThemeDark -> ThemeHelper.DARK
                else -> ThemeHelper.SYSTEM
            }
            ThemeHelper.apply(mode) // takes effect immediately, no restart needed
            lifecycleScope.launch(Dispatchers.IO) { prefs.setThemeMode(mode) }
        }
    }

    private fun setupClearHistory() {
        findViewById<View>(R.id.clearHistoryContainer)?.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.clear_history_confirm_title))
                .setMessage(getString(R.string.clear_history_confirm_message))
                .setPositiveButton(getString(R.string.clear_history)) { _, _ -> clearHistory() }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun clearHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val profileId = repository.getCurrentProfile()?.id ?: 1L
            // EPIC-09 T09.2 — collect before deleting locally, else the ids are gone by the
            // time we'd need them; without this cleanup the next sync would re-import every
            // linked record right back, silently undoing "Clear History".
            val healthConnectRecordIds = repository.getHealthConnectRecordIds(profileId)
            repository.clearHistory(profileId)
            WidgetUpdateHelper.updateAllWidgets(applicationContext)
            HealthConnectManager.deleteRecords(applicationContext, healthConnectRecordIds)
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.clear_history_done), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var isRestarting = false

    private fun restartApp(context: Context) {
        if (isRestarting) return
        isRestarting = true

        val appCtx = context.applicationContext
        // Give a small delay to ensure SharedPreferences are fully written to disk
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val intent = appCtx.packageManager.getLaunchIntentForPackage(appCtx.packageName)
            intent?.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            appCtx.startActivity(intent)

            // Kill the current process to ensure clean restart
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }, 200) // 200ms delay to ensure SharedPreferences are flushed
    }
}
