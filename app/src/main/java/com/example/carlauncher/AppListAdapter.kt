package com.example.carlauncher

import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.carlauncher.databinding.ItemAppBinding

class AppListAdapter(
    private val pm: PackageManager,
    private val apps: List<ResolveInfo>,
    private val onClick: (ResolveInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.VH>() {
    class VH(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val info = apps[position]
        holder.b.txtAppName.text = info.loadLabel(pm)
        holder.b.imgAppIcon.setImageDrawable(info.loadIcon(pm))
        holder.b.root.setOnClickListener { onClick(info) }
    }

    override fun getItemCount() = apps.size
}
