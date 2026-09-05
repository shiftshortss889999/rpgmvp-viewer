package com.rpgmvpviewer.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rpgmvpviewer.app.databinding.ItemImageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageAdapter(
    private var items: List<DocFileEntry>,
    private var key: ByteArray?,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ImageAdapter.VH>() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val cache = LruCache<String, Bitmap>(40)

    fun updateData(newItems: List<DocFileEntry>, newKey: ByteArray?) {
        items = newItems
        key = newKey
        cache.evictAll()
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemImageBinding) : RecyclerView.ViewHolder(binding.root) {
        var job: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = items[position]
        holder.job?.cancel()
        holder.binding.fileName.text = entry.name

        val cached = cache.get(entry.uri.toString())
        if (cached != null) {
            holder.binding.imageView.setImageBitmap(cached)
        } else {
            holder.binding.imageView.setImageBitmap(null)
            val context = holder.itemView.context
            holder.job = scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeThumbnail(context, entry.uri, key) }
                if (bmp != null) {
                    cache.put(entry.uri.toString(), bmp)
                    holder.binding.imageView.setImageBitmap(bmp)
                }
            }
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(pos)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun decodeThumbnail(context: Context, uri: Uri, key: ByteArray?): Bitmap? {
        if (key == null) return null
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val decrypted = RpgMakerDecryptor.decryptImage(bytes, key) ?: return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val targetSize = 200
            var sample = 1
            while (bounds.outWidth / sample > targetSize * 2 || bounds.outHeight / sample > targetSize * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size, opts)
        } catch (e: Exception) {
            null
        }
    }
}
