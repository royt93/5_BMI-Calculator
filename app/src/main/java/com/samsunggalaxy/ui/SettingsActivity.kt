package com.samsunggalaxy.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.samsunggalaxy.BaseActivity
import com.samsunggalaxy.R
import com.samsunggalaxy.data.AppDatabase
import com.samsunggalaxy.data.BmiRepository
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

    // Guards against the listener re-persisting the value it just read while
    // programmatically checking a button to reflect the stored preference.
    private var suppressUnitListener = false
    private var suppressThemeListener = false

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
        repository = BmiRepository(database.bmiDao(), database.profileDao())

        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage)
        toggleUnitSystem = findViewById(R.id.toggleUnitSystem)
        toggleTheme = findViewById(R.id.toggleTheme)

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
