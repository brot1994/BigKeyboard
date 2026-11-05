package id.bram.bigkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** Halaman pengaturan kamus "Teks Keren": tambah, ubah, hapus singkatan. */
class TextKerenActivity : Activity() {

    private lateinit var listContainer: LinearLayout
    private var entries = mutableListOf<TeksKeren>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entries = TextKerenStore.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xFF121212.toInt())
        }

        root.addView(TextView(this).apply {
            text = "✨ Teks Keren"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Ketik singkatan di keyboard, lalu ketuk sarannya di bar atas keyboard " +
                "untuk mengganti dengan teks keren. Ketuk entri di bawah untuk mengubah."
            textSize = 14f
            setTextColor(0xFFBBBBBB.toInt())
            setPadding(0, dp(6), 0, dp(12))
        })
        root.addView(Button(this).apply {
            text = "+ Tambah Teks Keren"
            textSize = 18f
            setOnClickListener { showEditor(-1) }
        })

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(listContainer)

        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF121212.toInt()) }
        scroll.addView(root)
        setContentView(scroll)
        renderList()
    }

    private fun renderList() {
        listContainer.removeAllViews()
        if (entries.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "Belum ada teks keren."
                setTextColor(0xFF888888.toInt())
            })
        }
        entries.forEachIndexed { i, e ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(0xFF2A2A2A.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setOnClickListener { showEditor(i) }
            }
            card.addView(TextView(this).apply {
                text = e.singkatan
                textSize = 18f
                setTextColor(0xFFFFC94D.toInt())
            })
            card.addView(TextView(this).apply {
                text = e.teks
                textSize = 16f
                setTextColor(Color.WHITE)
            })
            listContainer.addView(card)
        }
    }

    private fun showEditor(index: Int) {
        val existing = entries.getOrNull(index)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        fun label(t: String) = TextView(this).apply {
            text = t; textSize = 13f; setPadding(0, dp(10), 0, 0)
        }

        val inKey = EditText(this).apply {
            hint = "contoh: maacih"
            setSingleLine()
            setText(existing?.singkatan ?: "")
        }
        val inText = EditText(this).apply {
            hint = "teks keren yang akan dimasukkan"
            setText(existing?.teks ?: "")
        }

        // --- Pembuat otomatis ---
        val inPlain = EditText(this).apply { hint = "tulis kalimat biasa, mis. makasih udah di add" }
        val spGaya = Spinner(this).apply {
            adapter = ArrayAdapter(this@TextKerenActivity,
                android.R.layout.simple_spinner_dropdown_item, GayaTeks.namaGaya)
        }
        val contohHiasan = GayaTeks.hiasan.mapIndexed { i, _ ->
            if (i == 0) "(tanpa hiasan)" else GayaTeks.hias("teks", i)
        }
        val spHias = Spinner(this).apply {
            adapter = ArrayAdapter(this@TextKerenActivity,
                android.R.layout.simple_spinner_dropdown_item, contohHiasan)
        }
        val preview = TextView(this).apply { textSize = 16f; setPadding(0, dp(8), 0, 0) }

        fun buat(): String {
            val p = inPlain.text.toString().trim()
            if (p.isEmpty()) return ""
            return GayaTeks.hias(GayaTeks.ubah(p, spGaya.selectedItemPosition), spHias.selectedItemPosition)
        }
        fun refresh() { preview.text = buat() }
        val sel = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spGaya.onItemSelectedListener = sel
        spHias.onItemSelectedListener = sel
        inPlain.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = refresh()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        spHias.setSelection(1)

        val pakai = Button(this).apply {
            text = "⬆ Pakai hasil ini"
            setOnClickListener {
                val hasil = buat()
                if (hasil.isNotEmpty()) inText.setText(hasil)
            }
        }

        box.addView(label("Singkatan (yang diketik)"))
        box.addView(inKey)
        box.addView(label("Teks keren"))
        box.addView(inText)
        box.addView(label("— atau buat otomatis —"))
        box.addView(inPlain)
        box.addView(label("Gaya huruf"))
        box.addView(spGaya)
        box.addView(label("Hiasan"))
        box.addView(spHias)
        box.addView(preview)
        box.addView(pakai)

        val scroll = ScrollView(this).apply { addView(box) }

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Tambah Teks Keren" else "Ubah Teks Keren")
            .setView(scroll)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .apply {
                if (existing != null) {
                    setNeutralButton("Hapus") { _, _ ->
                        entries.removeAt(index)
                        TextKerenStore.save(this@TextKerenActivity, entries)
                        renderList()
                    }
                }
            }
            .create()

        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val k = inKey.text.toString().trim().lowercase()
                val t = inText.text.toString()
                when {
                    k.isEmpty() || k.contains(' ') ->
                        toast("Singkatan harus satu kata, tanpa spasi")
                    t.isBlank() -> toast("Teks keren masih kosong")
                    entries.withIndex().any { it.index != index && it.value.singkatan == k } ->
                        toast("Singkatan \"$k\" sudah dipakai")
                    else -> {
                        val item = TeksKeren(k, t)
                        if (index >= 0) entries[index] = item else entries.add(item)
                        TextKerenStore.save(this, entries)
                        renderList()
                        dlg.dismiss()
                    }
                }
            }
        }
        dlg.show()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
