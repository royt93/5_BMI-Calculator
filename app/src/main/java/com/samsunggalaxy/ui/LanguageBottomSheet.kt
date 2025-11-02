package com.samsunggalaxy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.samsunggalaxy.R
import com.samsunggalaxy.adt.LanguageAdapter
import com.samsunggalaxy.utils.LocaleHelper

data class Language(val code: String, val name: String)

class LanguageBottomSheet(
    private val onLanguageSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    private val languages = listOf(
        Language("en", "English"),
        Language("vi", "Vietnamese"),
        Language("fr", "French"),
        Language("de", "German"),
        Language("ja", "Japanese"),
        Language("ko", "Korean"),
        Language("th", "Thai")
    )

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
        recyclerView.adapter = LanguageAdapter(languages, currentLanguage) { language ->
            onLanguageSelected(language.code)
            dismiss()
        }
    }
}
