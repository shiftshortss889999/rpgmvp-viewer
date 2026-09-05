package com.rpgmvpviewer.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.rpgmvpviewer.app.databinding.ActivityViewerBinding
import com.rpgmvpviewer.app.databinding.ItemViewerPageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uris: List<Uri> = intent.getParcelableArrayListExtra<Uri>(EXTRA_URIS) ?: emptyList()
        val key: ByteArray? = intent.getByteArrayExtra(EXTRA_KEY)
        val startPos = intent.getIntExtra(EXTRA_START_POS, 0)

        val adapter = ViewerPagerAdapter(uris, key) { position -> updateTitle(uris, position) }
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(startPos, false)
        updateTitle(uris, startPos)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTitle(uris, position)
            }
        })

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun updateTitle(uris: List<Uri>, position: Int) {
        val name = uris.getOrNull(position)?.lastPathSegment?.substringAfterLast('/') ?: ""
        binding.titleText.text = getString(R.string.viewer_title_format, position + 1, uris.size, name)
    }

    companion object {
        const val EXTRA_URIS = "extra_uris"
        const val EXTRA_KEY = "extra_key"
        const val EXTRA_START_POS = "extra_start_pos"
    }
}

class ViewerPagerAdapter(
    private val uris: List<Uri>,
    private val key: ByteArray?,
    private val onBind: (Int) -> Unit
) : RecyclerView.Adapter<ViewerPagerAdapter.PageVH>() {

    inner class PageVH(val binding: ItemViewerPageBinding) : RecyclerView.ViewHolder(binding.root) {
        var job: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val binding = ItemViewerPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageVH(binding)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        onBind(position)
        holder.job?.cancel()
        holder.binding.progress.visibility = View.VISIBLE
        holder.binding.zoomImageView.setImageBitmap(null)

        val uri = uris[position]
        val context = holder.itemView.context
        val owner = context as? LifecycleOwner

        holder.job = owner?.lifecycleScope?.launch {
            val bmp = withContext(Dispatchers.IO) { decodeFull(context, uri, key) }
            holder.binding.progress.visibility = View.GONE
            if (bmp != null) {
                holder.binding.zoomImageView.setImageBitmap(bmp)
            }
        }
    }

    override fun getItemCount(): Int = uris.size

    private fun decodeFull(context: Context, uri: Uri, key: ByteArray?): Bitmap? {
        if (key == null) return null
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val decrypted = RpgMakerDecryptor.decryptImage(bytes, key) ?: return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val maxDimension = 2048
            var sample = 1
            while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size, opts)
        } catch (e: Exception) {
            null
        }
    }
}
