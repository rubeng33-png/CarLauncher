package com.example.carlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.carlauncher.data.PhotoLoader
import com.example.carlauncher.data.PrefsManager
import com.example.carlauncher.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            prefs.vehiclePhotoUri = uri.toString()
            PhotoLoader.clearCache()
            showPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)

        b.btnPickPhoto.setOnClickListener { pickImage.launch(arrayOf("image/*")) }
        b.btnRemovePhoto.setOnClickListener {
            prefs.vehiclePhotoUri = null
            PhotoLoader.clearCache()
            b.imgPreview.setImageResource(android.R.drawable.ic_menu_camera)
        }
        showPreview()
    }

    private fun showPreview() {
        val uri = prefs.vehiclePhotoUri ?: return b.imgPreview.setImageResource(android.R.drawable.ic_menu_camera)
        lifecycleScope.launch {
            val bmp = PhotoLoader.load(this@SettingsActivity, Uri.parse(uri))
            if (bmp != null) b.imgPreview.setImageBitmap(bmp) else b.imgPreview.setImageResource(android.R.drawable.ic_menu_camera)
        }
    }
}
