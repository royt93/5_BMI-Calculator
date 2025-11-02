package com.samsunggalaxy.adt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.samsunggalaxy.R
import com.samsunggalaxy.ui.Language

class LanguageAdapter(
    private val languages: List<Language>,
    private val currentLanguage: String,
    private val onLanguageClick: (Language) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    inner class LanguageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLanguageName: TextView = itemView.findViewById(R.id.tvLanguageName)
        val ivCheck: ImageView = itemView.findViewById(R.id.ivCheck)

        fun bind(language: Language) {
            tvLanguageName.text = language.name
            ivCheck.visibility = if (language.code == currentLanguage) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onLanguageClick(language)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_language, parent, false)
        return LanguageViewHolder(view)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(languages[position])
    }

    override fun getItemCount() = languages.size
}
