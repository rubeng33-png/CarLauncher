package com.example.carlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.carlauncher.data.PhotoLoader
import com.example.carlauncher.data.PrefsManager
import com.example.carlauncher.databinding.ActivityMainBinding
import com.example.carlauncher.databinding.DialogAppsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private val clockHandler = Handler(Looper.getMainLooper())

    private val clockRunnable = object : Runnable {
        override fun run() { updateClock(); clockHandler.postDelayed(this, 1000L) }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            prefs.vehiclePhotoUri = uri.toString()
            PhotoLoader.clearCache()
            loadVehiclePhoto()
            Toast.makeText(this, "Foto guardada", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PrefsManager(this)
        if (prefs.isDarkMode) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPerms.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }

        setupListeners()
    }

    private fun setupListeners() {
        b.gaugeEngine.setOnClickListener { startActivity(Intent(this, MaintenanceActivity::class.java)) }
        b.gaugeGearbox.setOnClickListener { startActivity(Intent(this, MaintenanceActivity::class.java)) }
        b.btnAllApps.setOnClickListener { showAppDrawer() }

        b.btnMediaPrev.setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        b.btnMediaPlayPause.setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        b.btnMediaNext.setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }

        b.imgVehicle.setOnClickListener { pickImage.launch(arrayOf("image/*")) }
        b.txtGreeting.setOnClickListener { showEditNameDialog() }

        findViewById<View>(R.id.navMaps).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))) } catch (e: Exception) { Toast.makeText(this, "No hay mapas", Toast.LENGTH_SHORT).show() }
        }
        findViewById<View>(R.id.navBluetooth).setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (e: Exception) { Toast.makeText(this, "No hay Bluetooth", Toast.LENGTH_SHORT).show() }
        }
        findViewById<View>(R.id.navRadio).setOnClickListener {
            try { startActivity(Intent("com.android.fmradio.FMRadioMain")) } catch (e: Exception) { Toast.makeText(this, "No hay Radio", Toast.LENGTH_SHORT).show() }
        }
        findViewById<View>(R.id.navMusic).setOnClickListener {
            try { startActivity(Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER)) } catch (e: Exception) { Toast.makeText(this, "No hay Música", Toast.LENGTH_SHORT).show() }
        }
        findViewById<View>(R.id.navNightMode).setOnClickListener {
            prefs.isDarkMode = !prefs.isDarkMode
            if (prefs.isDarkMode) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            recreate()
        }
        findViewById<View>(R.id.navSettings).setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e: Exception) { Toast.makeText(this, "Error al abrir ajustes", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun showEditNameDialog() {
        val input = EditText(this)
        input.setText(prefs.userName)
        input.setPadding(50, 30, 50, 30)

        AlertDialog.Builder(this)
            .setTitle("¿Cómo te llamas?")
            .setView(input)
            .setPositiveButton("Guardar") { dialog, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    prefs.userName = newName
                    updateGreeting()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        b.txtSubGreeting.text = when (hour) {
            in 0..11 -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        b.txtGreeting.text = "Hola, ${prefs.userName}"
    }

    private fun showAppDrawer() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase(Locale.getDefault()) }

        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogAppsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        dialogBinding.recyclerAppsDialog.layoutManager = GridLayoutManager(this, 5)
        dialogBinding.recyclerAppsDialog.adapter = AppListAdapter(pm, apps) { info ->
            pm.getLaunchIntentForPackage(info.activityInfo.packageName)?.let { startActivity(it) }
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
        loadVehiclePhoto()
        updateMaintenanceGauges()
        clockHandler.post(clockRunnable)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockRunnable)
    }

    private fun updateClock() {
        val now = Calendar.getInstance()
        b.txtClock.text = String.format(Locale.getDefault(), "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        b.txtDate.text = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()).format(now.time)
    }

    private fun updateMaintenanceGauges() {
        val current = prefs.currentOdometer
        val engNext = prefs.engineLastChangeKm + prefs.engineIntervalKm
        val engRem = engNext - current
        b.gaugeEngine.setData(engRem.toFloat(), prefs.engineIntervalKm.toFloat(), engRem <= 500)
        b.txtEngineInterval.text = "Cada ${String.format("%,d", prefs.engineIntervalKm)} km"

        val gbxNext = prefs.gearboxLastChangeKm + prefs.gearboxIntervalKm
        val gbxRem = gbxNext - current
        b.gaugeGearbox.setData(gbxRem.toFloat(), prefs.gearboxIntervalKm.toFloat(), gbxRem <= 500)
        b.txtGearboxInterval.text = "Cada ${String.format("%,d", prefs.gearboxIntervalKm)} km"
    }

    private fun loadVehiclePhoto() {
        val uriString = prefs.vehiclePhotoUri
        if (uriString.isNullOrEmpty()) {
            b.imgVehicle.setImageResource(android.R.drawable.ic_menu_camera)
            return
        }
        lifecycleScope.launch {
            val bmp = PhotoLoader.load(this@MainActivity, Uri.parse(uriString))
            if (bmp != null) b.imgVehicle.setImageBitmap(bmp) else b.imgVehicle.setImageResource(android.R.drawable.ic_menu_camera)
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
