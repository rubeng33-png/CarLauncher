package com.example.carlauncher

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.carlauncher.data.PrefsManager
import com.example.carlauncher.databinding.ActivityMaintenanceBinding

class MaintenanceActivity : AppCompatActivity() {
    private lateinit var b: ActivityMaintenanceBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMaintenanceBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)

        b.btnSaveOdometer.setOnClickListener {
            val v = b.edtOdometer.text.toString().toIntOrNull()
            if (v != null && v >= 0) {
                prefs.currentOdometer = v
                refreshUI()
                Toast.makeText(this, "Odómetro guardado", Toast.LENGTH_SHORT).show()
            }
        }

        b.btnEngineChange.setOnClickListener { confirmChange(true) }
        b.btnEngineEdit.setOnClickListener { editItem(true) }
        b.btnGearboxChange.setOnClickListener { confirmChange(false) }
        b.btnGearboxEdit.setOnClickListener { editItem(false) }

        refreshUI()
    }

    private fun refreshUI() {
        b.edtOdometer.setText(prefs.currentOdometer.toString())
        b.txtEngineLast.text = "Último: ${prefs.engineLastChangeKm} km"
        b.txtEngineNext.text = "Próximo: ${prefs.engineLastChangeKm + prefs.engineIntervalKm} km"
        b.txtGearboxLast.text = "Último: ${prefs.gearboxLastChangeKm} km"
        b.txtGearboxNext.text = "Próximo: ${prefs.gearboxLastChangeKm + prefs.gearboxIntervalKm} km"
    }

    private fun confirmChange(isEngine: Boolean) {
        if (isEngine) prefs.engineLastChangeKm = prefs.currentOdometer
        else prefs.gearboxLastChangeKm = prefs.currentOdometer
        refreshUI()
    }

    private fun editItem(isEngine: Boolean) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_maintenance, null)
        val edtInterval = view.findViewById<EditText>(R.id.edtInterval)
        val edtLast = view.findViewById<EditText>(R.id.edtLastChange)

        if (isEngine) {
            edtInterval.setText(prefs.engineIntervalKm.toString())
            edtLast.setText(prefs.engineLastChangeKm.toString())
        } else {
            edtInterval.setText(prefs.gearboxIntervalKm.toString())
            edtLast.setText(prefs.gearboxLastChangeKm.toString())
        }

        AlertDialog.Builder(this)
            .setTitle(if (isEngine) "Motor" else "Caja")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val intv = edtInterval.text.toString().toIntOrNull() ?: return@setPositiveButton
                val last = edtLast.text.toString().toIntOrNull() ?: return@setPositiveButton
                if (isEngine) { prefs.engineIntervalKm = intv; prefs.engineLastChangeKm = last }
                else { prefs.gearboxIntervalKm = intv; prefs.gearboxLastChangeKm = last }
                refreshUI()
            }
            .setNegativeButton("Cancelar", null).show()
    }
}
