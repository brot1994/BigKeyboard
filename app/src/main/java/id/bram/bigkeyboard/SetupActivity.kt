package id.bram.bigkeyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class SetupActivity : Activity() {

    companion object {
        const val EXTRA_REQUEST_MIC = "request_mic"
        private const val MIC_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()

        // Kalau activity ini dibuka otomatis dari keyboard karena mic belum diizinkan,
        // langsung munculkan dialog izinnya.
        if (intent?.getBooleanExtra(EXTRA_REQUEST_MIC, false) == true) {
            requestMicPermission()
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        if (hasMicPermission()) {
            Toast.makeText(this, "Mikrofon sudah diizinkan", Toast.LENGTH_SHORT).show()
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            MIC_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            val message = if (granted) {
                "Mikrofon diizinkan. Silakan coba tombol 🎤 di keyboard."
            } else {
                "Izin mikrofon ditolak. Fitur bicara-jadi-teks tidak akan berfungsi."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun buildUi() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Big Keyboard"
            textSize = 22f
            gravity = Gravity.CENTER
        }

        val desc = TextView(this).apply {
            text = "1) Aktifkan keyboard ini di Pengaturan.\n" +
                "2) Pilih Big Keyboard sebagai keyboard aktif.\n" +
                "3) Izinkan mikrofon untuk pakai fitur bicara-jadi-teks (tombol 🎤)."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }

        val enableButton = Button(this).apply {
            text = "1. Aktifkan Keyboard"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val chooseButton = Button(this).apply {
            text = "2. Pilih Keyboard Ini"
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        val micButton = Button(this).apply {
            text = "3. Izinkan Mikrofon"
            setOnClickListener { requestMicPermission() }
        }

        layout.addView(title)
        layout.addView(desc)
        layout.addView(enableButton)
        layout.addView(chooseButton)
        layout.addView(micButton)

        // Kalau ada catatan error dari crash sebelumnya, tampilkan di sini
        // supaya bisa di-screenshot tanpa perlu logcat/tools tambahan.
        val crashFile = File(filesDir, "last_crash.txt")
        if (crashFile.exists()) {
            val crashLabel = TextView(this).apply {
                text = "\n⚠️ Catatan error terakhir (screenshot ini untuk dikirim):"
                textSize = 14f
                setPadding(0, 48, 0, 8)
            }
            val crashText = TextView(this).apply {
                text = crashFile.readText()
                textSize = 11f
                setTextIsSelectable(true)
                setPadding(16, 16, 16, 16)
                setBackgroundColor(0xFFEFEFEF.toInt())
            }
            val clearButton = Button(this).apply {
                text = "Hapus catatan error ini"
                setOnClickListener {
                    crashFile.delete()
                    recreate()
                }
            }
            layout.addView(crashLabel)
            layout.addView(crashText)
            layout.addView(clearButton)
        }

        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)
    }
}
