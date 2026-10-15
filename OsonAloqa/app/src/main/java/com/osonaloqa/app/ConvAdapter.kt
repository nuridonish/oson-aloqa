package com.osonaloqa.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.osonaloqa.app.databinding.ItemChatBinding

class ConvAdapter(var onClick: (Conv) -> Unit) :
    RecyclerView.Adapter<ConvAdapter.VH>() {

    private var items: List<Conv> = emptyList()

    fun submit(list: List<Conv>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemChatBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.b.avatar.text = c.address.takeLast(1)
        holder.b.title.text = c.address
        holder.b.snippet.text = if (c.out) "→ ${c.lastBody}" else c.lastBody
        holder.b.time.text = android.text.format.DateFormat.format("dd.MM HH:mm", c.ts)
        holder.b.root.setOnClickListener { onClick(c) }
    }
}
