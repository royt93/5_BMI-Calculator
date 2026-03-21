package com.samsunggalaxy.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.samsunggalaxy.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BadgeAdapter(
    private val badges: List<BadgeItem>
) : RecyclerView.Adapter<BadgeAdapter.BadgeViewHolder>() {

    data class BadgeItem(
        val badge: BadgeManager.Badge,
        val earned: Boolean,
        val earnedDate: Long
    )

    class BadgeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView? = view.findViewById(R.id.ivBadgeIcon)
        val tvName: TextView? = view.findViewById(R.id.tvBadgeName)
        val tvDesc: TextView? = view.findViewById(R.id.tvBadgeDesc)
        val tvDate: TextView? = view.findViewById(R.id.tvBadgeDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_badge, parent, false)
        return BadgeViewHolder(view)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        val item = badges[position]
        val ctx = holder.itemView.context

        holder.ivIcon?.setImageResource(item.badge.iconRes)
        holder.tvName?.text = ctx.getString(item.badge.titleRes)
        holder.tvDesc?.text = ctx.getString(item.badge.descRes)

        if (item.earned) {
            holder.ivIcon?.alpha = 1.0f
            holder.tvName?.setTextColor(ContextCompat.getColor(ctx, R.color.textColor))
            holder.tvDate?.visibility = View.VISIBLE
            holder.tvDate?.text = "✅ ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(item.earnedDate))}"
            holder.tvDate?.setTextColor(ContextCompat.getColor(ctx, R.color.bmi_healthy))
        } else {
            holder.ivIcon?.alpha = 0.3f
            holder.tvName?.setTextColor(ContextCompat.getColor(ctx, R.color.textColorAdditional))
            holder.tvDate?.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = badges.size
}
