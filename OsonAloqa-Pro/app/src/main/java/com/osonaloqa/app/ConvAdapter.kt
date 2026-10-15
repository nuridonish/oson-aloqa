package com.osonaloqa.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.osonaloqa.app.databinding.ItemChatBinding

class ConvAdapter(
    var onClick: (ChatRow) -> Unit,
    var onLongClick: (ChatRow) -> Unit
) : ListAdapter<ChatRow, ConvAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChatRow>() {
            override fun areItemsTheSame(old: ChatRow, new: ChatRow) = old.address == new.address && old.gid == new.gid
            override fun areContentsTheSame(old: ChatRow, new: ChatRow) = old == new
        }
    }

    class VH(val b: ItemChatBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = currentList.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = currentList[position]
        holder.b.avatar.text = if (c.isGroup) "👥" else c.display.takeLast(1)
        holder.b.title.text = c.display + (if (c.muted) " 🔕" else "")
        holder.b.snippet.text = c.snippet
        holder.b.time.text = android.text.format.DateFormat.format("dd.MM HH:mm", c.ts)
        if (c.unread > 0) {
            holder.b.badge.visibility = android.view.View.VISIBLE
            holder.b.badge.text = if (c.unread > 99) "99+" else c.unread.toString()
        } else {
            holder.b.badge.visibility = android.view.View.GONE
        }
        holder.b.root.setOnClickListener { onClick(c) }
        holder.b.root.setOnLongClickListener { onLongClick(c); true }
    }
}
