package id.bram.bigkeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            text = "1) Aktifkan keyboard ini di Pengaturan.\n2) Pilih Big Keyboard sebagai keyboard aktif."
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

        layout.addView(title)
        layout.addView(desc)
        layout.addView(enableButton)
        layout.addView(chooseButton)

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
