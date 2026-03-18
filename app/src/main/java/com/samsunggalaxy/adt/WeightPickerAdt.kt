package com.samsunggalaxy.adt

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.samsunggalaxy.R

class WeightPickerAdt(
    private val context: Context,
    private var dataList: List<String>,
) :
    RecyclerView.Adapter<WeightPickerAdt.TextVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextVH {
        val view: View
        val inflater = LayoutInflater.from(context)
        view = inflater.inflate(R.layout.v_weight_picker, parent, false)
        return TextVH(view)
    }

    override fun onBindViewHolder(holder: TextVH, position: Int) {
        holder.pickerTxt.text = dataList[position]
        // ML-04: Resolve RecyclerView from parent to avoid holding direct reference
        holder.pickerTxt.setOnClickListener {
            (holder.itemView.parent as? RecyclerView)?.smoothScrollToPosition(position)
        }
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun swapData(newData: List<String>) {
        dataList = newData
        this.notifyDataSetChanged()
    }

    class TextVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var pickerTxt: TextView = itemView.findViewById(R.id.weight_picker_item)
    }

}
