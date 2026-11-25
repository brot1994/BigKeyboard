package id.bram.bigkeyboard

import android.content.Context
import android.content.res.Configuration

/** Pilihan susunan tombol huruf dan cara mengingatnya per ukuran layar. */
enum class Susunan(val label: String, val chip: String, val xml: Int) {
    LIMA("5 kolom", "5▦", R.xml.keyboard_layout),
    ENAM("6 kolom", "6▦", R.xml.keyboard_six),
    QWERTY("QWERTY", "QW", R.xml.keyboard_qwerty);

    fun berikutnya(): Susunan = values()[(ordinal + 1) % values().size]
}

object LayoutPilihan {
    private const val PREFS = "layout_keyboard"
    private const val KEY_MODE = "mode"          // "auto", atau nama Susunan untuk "selalu"

    const val AUTO = "auto"

    /** Kelompok ukuran layar, supaya pilihan di HP biasa & Fold dibuka diingat terpisah. */
    fun kelasLayar(config: Configuration): String = when {
        config.screenWidthDp >= 600 -> "lebar"
        config.screenHeightDp < 800 -> "pendek"
        else -> "biasa"
    }

    fun otomatis(config: Configuration): Susunan = when (kelasLayar(config)) {
        "lebar" -> Susunan.QWERTY
        "pendek" -> Susunan.ENAM
        else -> Susunan.LIMA
    }

    fun mode(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, AUTO) ?: AUTO

    fun setMode(ctx: Context, mode: String) {
        // Ganti mode di pengaturan -> lupakan pilihan cepat dari tombol di bar.
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
            .putString(KEY_MODE, mode).apply()
    }

    /** Susunan yang dipakai sekarang untuk layar ini. */
    fun aktif(ctx: Context, config: Configuration): Susunan {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("terakhir_" + kelasLayar(config), null)?.let { nama ->
            runCatching { return Susunan.valueOf(nama) }
        }
        val m = mode(ctx)
        return if (m == AUTO) otomatis(config) else runCatching { Susunan.valueOf(m) }.getOrDefault(Susunan.LIMA)
    }

    /** Dipanggil saat tombol di bar diketuk: ingat pilihan untuk kelas layar ini saja. */
    fun ingat(ctx: Context, config: Configuration, s: Susunan) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("terakhir_" + kelasLayar(config), s.name).apply()
    }
}
