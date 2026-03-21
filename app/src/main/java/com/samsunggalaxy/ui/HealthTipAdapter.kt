package com.samsunggalaxy.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.samsunggalaxy.R

class HealthTipAdapter(
    private val tips: List<String>,
    private val categoryName: String,
    private val categoryColorRes: Int
) : RecyclerView.Adapter<HealthTipAdapter.TipViewHolder>() {

    class TipViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContent: TextView? = view.findViewById(R.id.tvTipContent)
        val tvCategory: TextView? = view.findViewById(R.id.tvTipCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_health_tip, parent, false)
        // ViewPager2 requires pages to have match_parent width
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return TipViewHolder(view)
    }

    override fun onBindViewHolder(holder: TipViewHolder, position: Int) {
        holder.tvContent?.text = tips[position]
        holder.tvCategory?.text = categoryName
        holder.tvCategory?.setTextColor(
            ContextCompat.getColor(holder.itemView.context, categoryColorRes)
        )
    }

    override fun getItemCount(): Int = tips.size
}
