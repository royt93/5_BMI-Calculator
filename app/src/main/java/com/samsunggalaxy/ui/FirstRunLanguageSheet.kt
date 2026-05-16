package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.samsunggalaxy.R
import com.samsunggalaxy.adt.LanguageAdapter
import com.samsunggalaxy.utils.LocaleHelper

/**
 * First-run language selector shown once when the app is opened for the first time.
 *
 * Memory-leak safe design:
 * - Communicates back to SplashAct via FragmentResult API (no direct Activity reference).
 * - Does NOT hold any reference to the host Activity.
 * - All coroutines are in SplashAct's lifecycleScope, not here.
 */
class FirstRunLanguageSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "FirstRunLanguageSheet"
        const val REQUEST_KEY = "first_run_language"
        const val RESULT_LANGUAGE = "selected_language"

        fun newInstance() = FirstRunLanguageSheet()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.bottom_sheet_language, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Expand to full height on first-run for better UX
        (dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet))?.let { sheet ->
            BottomSheetBehavior.from(sheet).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }

        // Non-cancellable: user must select a language
        isCancelable = false
        dialog?.setCanceledOnTouchOutside(false)

        val currentLang = LocaleHelper.getLanguage(requireContext())
        val recyclerView = view.findViewById<RecyclerView>(R.id.languageRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = LanguageAdapter(Languages.ALL, currentLang) { language ->
            // Apply locale immediately so UI updates
            LocaleHelper.setLanguage(requireContext(), language.code)
            // Report result to SplashAct via FragmentResult — NO Activity reference held
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_LANGUAGE to language.code))
            dismiss()
        }
    }

    override fun onDestroyView() {
        // Clear RecyclerView adapter to prevent view leak
        view?.findViewById<RecyclerView>(R.id.languageRecyclerView)?.adapter = null
        super.onDestroyView()
    }
}
