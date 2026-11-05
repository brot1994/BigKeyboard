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

    private var aktifSaja = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entries = TextKerenStore.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(16))
            setBackgroundColor(0xFF121212.toInt())
        }

        root.addView(TextView(this).apply {
            text = "✨ Teks Keren"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Kiri: teks keren. Kanan: isi singkatan untuk memanggilnya dari keyboard " +
                "(contoh: maacih). Yang singkatannya kosong tidak dipakai. " +
                "Ketuk teks kiri untuk mengubah, − untuk menghapus."
            textSize = 14f
            setTextColor(0xFFBBBBBB.toInt())
            setPadding(dp(4), dp(6), dp(4), dp(12))
        })

        val tombolAtas = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tombolAtas.addView(Button(this).apply {
            text = "+ Tambah"
            textSize = 17f
            setOnClickListener { showEditor(-1) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tombolAtas.addView(Button(this).apply {
            fun label() { text = if (aktifSaja) "Tampilkan: aktif" else "Tampilkan: semua" }
            label()
            textSize = 15f
            setOnClickListener { aktifSaja = !aktifSaja; label(); renderList() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tombolAtas)

        // Judul kolom
        val judul = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(4), dp(4))
        }
        judul.addView(TextView(this).apply {
            text = "TEKS KEREN"; textSize = 12f; setTextColor(0xFF888888.toInt())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        judul.addView(TextView(this).apply {
            text = "SINGKATAN"; textSize = 12f; setTextColor(0xFF888888.toInt())
        }, LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(judul)

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        root.addView(Button(this).apply {
            text = "Pulihkan template bawaan yang terhapus"
            textSize = 14f
            setOnClickListener {
                val n = TextKerenStore.tambahTemplate(entries)
                TextKerenStore.save(this@TextKerenActivity, entries)
                renderList()
                toast(if (n == 0) "Semua template masih ada" else "$n template dipulihkan")
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF121212.toInt()) }
        scroll.addView(root)
        setContentView(scroll)
        renderList()
    }

    private fun renderList() {
        listContainer.removeAllViews()
        val tampil = entries.withIndex().filter { !aktifSaja || it.value.singkatan.isNotEmpty() }
        if (tampil.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = if (aktifSaja) "Belum ada yang diberi singkatan." else "Belum ada teks keren."
                setTextColor(0xFF888888.toInt())
                setPadding(dp(12), dp(12), 0, 0)
            })
        }
        tampil.forEach { (i, e) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(4), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(0xFF2A2A2A.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            }

            // Kiri: teks keren (ketuk untuk ubah)
            row.addView(TextView(this).apply {
                text = e.teks
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(0, 0, dp(8), 0)
                setOnClickListener { showEditor(i) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            // Kanan: singkatan (langsung tersimpan saat diketik)
            val input = EditText(this).apply {
                setText(e.singkatan)
                hint = "singkatan"
                setSingleLine()
                textSize = 16f
                setTextColor(0xFFFFC94D.toInt())
                setHintTextColor(0xFF666666.toInt())
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val k = s.toString().trim().lowercase()
                    when {
                        k.contains(' ') -> input.error = "Tanpa spasi"
                        k.isNotEmpty() && entries.withIndex()
                            .any { it.index != i && it.value.singkatan == k } ->
                            input.error = "Sudah dipakai"
                        else -> {
                            input.error = null
                            entries[i] = entries[i].copy(singkatan = k)
                            TextKerenStore.save(this@TextKerenActivity, entries)
                        }
                    }
                }
            })
            row.addView(input, LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT))

            // Tombol hapus
            row.addView(TextView(this).apply {
                text = "−"
                textSize = 26f
                setTextColor(0xFFFF6B6B.toInt())
                gravity = android.view.Gravity.CENTER
                setOnClickListener {
                    entries.removeAt(i)
                    TextKerenStore.save(this@TextKerenActivity, entries)
                    renderList()
                }
            }, LinearLayout.LayoutParams(dp(40), dp(44)))

            listContainer.addView(row)
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
            hint = "contoh: maacih (boleh kosong)"
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
                    k.contains(' ') ->
                        toast("Singkatan harus satu kata, tanpa spasi")
                    t.isBlank() -> toast("Teks keren masih kosong")
                    k.isNotEmpty() && entries.withIndex().any { it.index != index && it.value.singkatan == k } ->
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
