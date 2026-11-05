package com.ghajni.smsforwarder.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ghajni.smsforwarder.R
import com.ghajni.smsforwarder.data.SmsLogItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter(private var items: List<SmsLogItem>) :
    RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault())

    fun updateData(newItems: List<SmsLogItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, dateFormat)
    }

    override fun getItemCount(): Int = items.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView = itemView.findViewById(R.id.tvLogSender)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvLogStatus)
        private val tvTime: TextView = itemView.findViewById(R.id.tvLogTime)
        private val tvBody: TextView = itemView.findViewById(R.id.tvLogBody)
        private val tvForwardedTo: TextView = itemView.findViewById(R.id.tvLogForwardedTo)

        fun bind(item: SmsLogItem, dateFormat: SimpleDateFormat) {
            tvSender.text = "From: ${item.sender}"
            tvTime.text = dateFormat.format(Date(item.timestamp))
            tvBody.text = item.body
            tvForwardedTo.text = "Forwarded to: ${item.forwardedTo}"

            tvStatus.text = item.status
            when {
                item.status.contains("SENT", ignoreCase = true) -> {
                    tvStatus.setTextColor(Color.parseColor("#22C55E")) // Green
                }
                item.status.contains("FILTERED", ignoreCase = true) -> {
                    tvStatus.setTextColor(Color.parseColor("#F59E0B")) // Yellow/Orange
                }
                else -> {
                    tvStatus.setTextColor(Color.parseColor("#EF4444")) // Red
                }
            }
        }
    }
}

