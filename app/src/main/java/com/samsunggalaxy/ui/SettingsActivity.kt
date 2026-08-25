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

    // Guards against the listener re-persisting the value it just read while
    // programmatically checking a button to reflect the stored preference.
    private var suppressUnitListener = false
    private var suppressThemeListener = false
    private var suppressReminderListener = false
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
            repository.clearHistory(profileId)
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
