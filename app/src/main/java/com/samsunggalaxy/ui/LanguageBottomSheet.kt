package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.samsunggalaxy.R
import com.samsunggalaxy.adt.LanguageAdapter
import com.samsunggalaxy.utils.LocaleHelper

class LanguageBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val REQUEST_KEY = "language_selection_settings"
        const val RESULT_LANGUAGE = "selected_language"

        fun newInstance() = LanguageBottomSheet()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_language, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.languageRecyclerView)
        val currentLanguage = LocaleHelper.getLanguage(requireContext())

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = LanguageAdapter(Languages.ALL, currentLanguage) { language ->
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_LANGUAGE to language.code))
            dismiss()
        }
    }

    override fun onDestroyView() {
        view?.findViewById<RecyclerView>(R.id.languageRecyclerView)?.adapter = null
        super.onDestroyView()
    }
}
