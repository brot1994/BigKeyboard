package id.bram.bigkeyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Satu entri kamus: singkatan -> teks keren. */
data class TeksKeren(val singkatan: String, val teks: String)

/**
 * Penyimpanan kamus "Teks Keren" (singkatan -> teks gaya BBM).
 * Disimpan sebagai JSON di SharedPreferences supaya bisa dibaca keyboard & halaman pengaturan.
 */
object TextKerenStore {
    private const val PREFS = "teks_keren"
    private const val KEY = "entries"

    private val defaults = listOf(
        TeksKeren("maacih", "▄ █ ▄ █ мαкαѕιн υ∂αн ∂ι α∂∂ █ ▄ █ ▄"),
        TeksKeren("pagi", "☀️ ѕєℓαмαт ραgι ☀️"),
        TeksKeren("malam", "☾ ⋆*･ﾟ ѕєℓαмαт мαℓαм ･ﾟ*⋆ ☾"),
        TeksKeren("otw", "🏍💨 σтω уαн... 彡"),
        TeksKeren("wkwk", "ωкωкωкωк 😂😂"),
        TeksKeren("ok", "★彡 σкєєн∂ αн 彡★"),
        TeksKeren("sayang", "♥•.¸¸.•♥ ѕαуαηg ♥•.¸¸.•♥")
    )

    fun load(context: Context): MutableList<TeksKeren> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return defaults.toMutableList()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TeksKeren(o.getString("k"), o.getString("v"))
            }
        } catch (e: Exception) {
            defaults.toMutableList()
        }
    }

    fun save(context: Context, list: List<TeksKeren>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("k", it.singkatan).put("v", it.teks)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}

/** Pengubah teks biasa menjadi gaya-gaya "text keren" ala zaman BBM. */
object GayaTeks {

    // Gaya alay klasik (huruf Yunani/Kiril yang mirip huruf latin)
    private val alay = mapOf(
        'a' to "α", 'b' to "в", 'c' to "¢", 'd' to "∂", 'e' to "є", 'f' to "ƒ", 'g' to "g",
        'h' to "н", 'i' to "ι", 'j' to "נ", 'k' to "к", 'l' to "ℓ", 'm' to "м", 'n' to "η",
        'o' to "σ", 'p' to "ρ", 'q' to "q", 'r' to "я", 's' to "ѕ", 't' to "т", 'u' to "υ",
        'v' to "ν", 'w' to "ω", 'x' to "χ", 'y' to "у", 'z' to "z"
    )

    private val kecilKapital = mapOf(
        'a' to "ᴀ", 'b' to "ʙ", 'c' to "ᴄ", 'd' to "ᴅ", 'e' to "ᴇ", 'f' to "ꜰ", 'g' to "ɢ",
        'h' to "ʜ", 'i' to "ɪ", 'j' to "ᴊ", 'k' to "ᴋ", 'l' to "ʟ", 'm' to "ᴍ", 'n' to "ɴ",
        'o' to "ᴏ", 'p' to "ᴘ", 'q' to "ǫ", 'r' to "ʀ", 's' to "ꜱ", 't' to "ᴛ", 'u' to "ᴜ",
        'v' to "ᴠ", 'w' to "ᴡ", 'x' to "x", 'y' to "ʏ", 'z' to "ᴢ"
    )

    /** Ubah huruf ke blok Unicode "Mathematical Alphanumeric" mulai dari kode huruf A besar/kecil. */
    private fun mathStyle(text: String, upperStart: Int, lowerStart: Int): String {
        val sb = StringBuilder()
        for (ch in text) {
            when (ch) {
                in 'A'..'Z' -> sb.appendCodePoint(upperStart + (ch - 'A'))
                in 'a'..'z' -> sb.appendCodePoint(lowerStart + (ch - 'a'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun petakan(text: String, peta: Map<Char, String>) =
        text.lowercase().map { peta[it] ?: it.toString() }.joinToString("")

    private fun bulatan(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            when (ch) {
                in 'A'..'Z' -> sb.appendCodePoint(0x24B6 + (ch - 'A'))
                in 'a'..'z' -> sb.appendCodePoint(0x24D0 + (ch - 'a'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun angkaAlay(text: String): String {
        var genap = false
        return text.map { c ->
            val ganti = when (c.lowercase()) {
                "a" -> "4"; "e" -> "3"; "i" -> "1"; "o" -> "0"; "g" -> "6"; "s" -> "5"; else -> null
            }
            if (ganti != null) ganti else if (c.isLetter()) {
                genap = !genap
                if (genap) c.uppercase() else c.lowercase()
            } else c.toString()
        }.joinToString("")
    }

    val namaGaya = listOf(
        "αℓαу (BBM)", "𝓢𝓪𝓻𝓾𝓷𝓰", "𝐓𝐞𝐛𝐚𝐥", "𝔾𝕒𝕟𝕕𝕒", "ᴋᴀᴘɪᴛᴀʟ ᴋᴇᴄɪʟ", "ⓑⓤⓛⓐⓣ", "4N6k4 4L4y"
    )

    fun ubah(text: String, gaya: Int): String = when (gaya) {
        0 -> petakan(text, alay)
        1 -> mathStyle(text, 0x1D4D0, 0x1D4EA)   // script bold
        2 -> mathStyle(text, 0x1D400, 0x1D41A)   // bold
        3 -> ganda(text)
        4 -> petakan(text, kecilKapital)
        5 -> bulatan(text)
        6 -> angkaAlay(text)
        else -> text
    }

    // Double-struck punya beberapa huruf "lubang" di blok lain (C, H, N, P, Q, R, Z).
    private fun ganda(text: String): String {
        val khusus = mapOf('C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115, 'P' to 0x2119,
            'Q' to 0x211A, 'R' to 0x211D, 'Z' to 0x2124)
        val sb = StringBuilder()
        for (ch in text) {
            val k = khusus[ch]
            when {
                k != null -> sb.appendCodePoint(k)
                ch in 'A'..'Z' -> sb.appendCodePoint(0x1D538 + (ch - 'A'))
                ch in 'a'..'z' -> sb.appendCodePoint(0x1D552 + (ch - 'a'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Hiasan pembungkus (kiri, kanan). */
    val hiasan = listOf(
        "" to "",
        "▄ █ ▄ █ " to " █ ▄ █ ▄",
        "★彡 " to " 彡★",
        "♥•.¸¸.•♥ " to " ♥•.¸¸.•♥",
        "✿ " to " ✿",
        "꧁ " to " ꧂",
        "»-(¯`·.·´¯)-> " to " <-(¯`·.·´¯)-«",
        "•°¯`•• " to " ••´¯°•",
        "(っ◔◡◔)っ ♥ " to " ♥"
    )

    fun hias(text: String, idx: Int): String {
        val (l, r) = hiasan.getOrElse(idx) { "" to "" }
        return l + text + r
    }
}
