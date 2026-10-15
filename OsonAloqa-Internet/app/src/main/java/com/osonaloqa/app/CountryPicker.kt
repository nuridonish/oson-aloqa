package com.osonaloqa.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osonaloqa.app.databinding.DialogCountryBinding
import com.osonaloqa.app.databinding.ItemCountryBinding

class CountryPicker(private val context: android.content.Context, private val onPick: (Country) -> Unit) {

    private var dialog: AlertDialog? = null

    fun show() {
        val binding = DialogCountryBinding.inflate(LayoutInflater.from(context))
        val adapter = Adapter(Countries.all)
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapter
        dialog = AlertDialog.Builder(context)
            .setTitle(R.string.select_country)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .create()
        binding.search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                adapter.submit(Countries.search(s?.toString() ?: ""))
            }
        })
        dialog?.show()
    }

    private inner class Adapter(private var items: List<Country>) : RecyclerView.Adapter<Adapter.VH>() {

        fun submit(list: List<Country>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemCountryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            holder.b.flag.text = c.flag
            holder.b.name.text = c.name
            holder.b.code.text = "+${c.code}"
            holder.b.root.setOnClickListener {
                onPick(c)
                dialog?.dismiss()
            }
        }

        inner class VH(val b: ItemCountryBinding) : RecyclerView.ViewHolder(b.root)
    }
}
