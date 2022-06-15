package com.arduinoworld.smartdoorbell

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.arduinoworld.smartdoorbell.databinding.RecyclerViewItemBinding
import com.bumptech.glide.Glide

class ImageRecyclerAdapter(
        private var imageRecyclerAdapterList : List<Image>
        ): RecyclerView.Adapter<ImageRecyclerAdapter.ViewHolder>() {

    private lateinit var clickListener : OnItemClickListener

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val binding = RecyclerViewItemBinding
                .inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
        return ViewHolder(binding, clickListener)
    }

    class ViewHolder(val binding: RecyclerViewItemBinding, listener: OnItemClickListener) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION)
                    listener.onItemClick(position)
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder) {
            with(imageRecyclerAdapterList[position]) {
                binding.imageName.text = imageName
                Glide.with(holder.itemView).load(imageUrl).into(binding.imageView)
            }
        }
    }

    override fun getItemCount() = imageRecyclerAdapterList.size

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        clickListener = listener
    }
}