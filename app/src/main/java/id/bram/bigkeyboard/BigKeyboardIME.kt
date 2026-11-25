package id.bram.bigkeyboard

import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class BigKeyboardIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var lettersKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard
    private lateinit var altKeyboard: Keyboard

    // --- Shift / caps-lock state ---
    private var isShifted = false          // one-shot shift (kembali normal setelah 1 huruf)
    private var isCapsLocked = false       // caps-lock permanen sampai shift ditekan lagi
    private var lastShiftTapTime = 0L

    // --- Auto-capitalize state ---
    private var autoCapNext = true         // huruf pertama di awal ketik / setelah titik

    private enum class PageMode { LETTERS, SYMBOLS, ALT }
    private var currentPage = PageMode.LETTERS

    // --- Long-press DEL state ---
    private val handler = Handler(Looper.getMainLooper())
    private var isDeleteLongPress = false
    private val clearAllRunnable = Runnable {
        clearAllText()
        isDeleteLongPress = true
    }

    // --- Voice typing (mic) state ---
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    companion object {
        private const val KEYCODE_TO_SYMBOLS = -6   // "?123" / "123"
        private const val KEYCODE_TO_LETTERS = -7   // "ABC"
        private const val KEYCODE_TO_ALT = -10      // "ALT"
        private const val KEYCODE_SWITCH_IME = -2
        private const val KEYCODE_MIC = -20
        private const val DOUBLE_TAP_MS = 350L
        private const val LONG_PRESS_DELETE_MS = 1500L
        private const val MIC_LABEL_IDLE = "🎤"      // 🎤
        private const val MIC_LABEL_LISTENING = "⏹"        // ⏹
    }

    // --- Teks Keren (bar saran) ---
    private var suggestionRow: LinearLayout? = null
    private var suggestionScroll: HorizontalScrollView? = null
    private var teksKeren: List<TeksKeren> = emptyList()
    private var lastSuggestKey = "\u0000"
    // Penggantian terakhir, supaya bisa dibatalkan: (teks yang disisipkan, kata asli)
    private var lastReplacement: Pair<String, String>? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // --- Susunan tombol (5 kolom / 6 kolom / QWERTY) & ciutkan ---
    private var susunan = Susunan.LIMA
    private val keyboardCache = HashMap<Susunan, Keyboard>()
    private var layoutChip: TextView? = null
    private var collapseChip: TextView? = null
    private var isCollapsed = false

    private fun keyboardUntuk(s: Susunan): Keyboard =
        keyboardCache.getOrPut(s) { Keyboard(this, s.xml) }

    /** Pasang susunan huruf sesuai pilihan untuk layar saat ini. */
    private fun terapkanSusunan(s: Susunan) {
        susunan = s
        lettersKeyboard = keyboardUntuk(s)
        if (::keyboardView.isInitialized && currentPage == PageMode.LETTERS) {
            keyboardView.keyboard = lettersKeyboard
            updateShiftVisual()
        }
        if (::keyboardView.isInitialized) {
            setMicKeyLabel(if (isListening) MIC_LABEL_LISTENING else MIC_LABEL_IDLE)
        }
        layoutChip?.text = s.chip
    }

    private fun gantiSusunan() {
        val baru = susunan.berikutnya()
        LayoutPilihan.ingat(this, resources.configuration, baru)
        currentPage = PageMode.LETTERS
        terapkanSusunan(baru)
        Toast.makeText(this, "Layout: " + baru.label, Toast.LENGTH_SHORT).show()
    }

    private fun setCollapsed(c: Boolean) {
        isCollapsed = c
        if (::keyboardView.isInitialized) keyboardView.visibility = if (c) View.GONE else View.VISIBLE
        collapseChip?.text = if (c) "▲" else "▼"
    }

    override fun onCreateInputView(): View {
        return try {
            keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
            // Ukuran layar bisa berubah (Fold dilipat/dibuka) -> buat ulang susunan.
            keyboardCache.clear()
            susunan = LayoutPilihan.aktif(this, resources.configuration)
            lettersKeyboard = keyboardUntuk(susunan)
            symbolsKeyboard = Keyboard(this, R.xml.keyboard_symbols)
            altKeyboard = Keyboard(this, R.xml.keyboard_alt)
            keyboardView.keyboard = lettersKeyboard
            keyboardView.setOnKeyboardActionListener(this)

            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF0d0d0d.toInt())
                addView(buildSuggestionBar())
                addView(keyboardView)
            }
        } catch (e: Throwable) {
            // Kalau ada error, tampilkan pesannya di layar (bukan diam/blank)
            // supaya gampang di-screenshot dan dilacak penyebabnya.
            Log.e("BigKeyboardIME", "Gagal membuat input view", e)
            TextView(this).apply {
                text = "Big Keyboard error:\n${e.javaClass.simpleName}: ${e.message}"
                setPadding(24, 24, 24, 24)
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF0d0d0d.toInt())
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentPage = PageMode.LETTERS
        isShifted = false
        isCapsLocked = false
        autoCapNext = true // huruf pertama saat mulai ketik = kapital
        terapkanSusunan(LayoutPilihan.aktif(this, resources.configuration))
        setCollapsed(false)
        keyboardView.keyboard = lettersKeyboard
        updateShiftVisual()
        // Muat ulang kamus (mungkin baru diubah di halaman pengaturan).
        teksKeren = TextKerenStore.load(this)
        lastReplacement = null
        lastSuggestKey = "\u0000"
        updateSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Keyboard ditutup / pindah field -> hentikan sesi dengar kalau masih aktif.
        stopListeningAndReset()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // Tampilan tombol shift mengikuti kondisi gabungan: shift sekali, caps-lock, atau auto-cap.
    private fun updateShiftVisual() {
        keyboardView.isShifted = isShifted || isCapsLocked || autoCapNext
        keyboardView.invalidateAllKeys()
    }

    private fun clearAllText() {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        // Trik aman untuk hapus semua isi field: minta hapus jauh lebih banyak
        // dari kemungkinan panjang teks; InputConnection akan berhenti di batas teks yang ada.
        ic.deleteSurroundingText(9999, 9999)
        ic.endBatchEdit()
    }

    // --- Teks Keren: bar saran di atas keyboard ---

    private fun buildSuggestionBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF161616.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            )
        }
        // Tombol ✨ di kiri: buka halaman pengaturan Teks Keren.
        val settings = TextView(this).apply {
            text = "✨"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener {
                startActivity(Intent(this@BigKeyboardIME, TextKerenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        bar.addView(settings, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        suggestionRow = row
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        suggestionScroll = scroll
        bar.addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        // Tombol ganti susunan: 5▦ -> 6▦ -> QW -> 5▦
        val chip = TextView(this).apply {
            text = susunan.chip
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFC94D.toInt())
            setPadding(dp(10), 0, dp(10), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), 0xFF555555.toInt())
            }
            setOnClickListener { gantiSusunan() }
        }
        layoutChip = chip
        bar.addView(chip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply { marginStart = dp(6) })

        // Tombol ciutkan / munculkan keyboard
        val collapse = TextView(this).apply {
            text = "▼"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFC94D.toInt())
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { setCollapsed(!isCollapsed) }
        }
        collapseChip = collapse
        bar.addView(collapse, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return bar
    }

    private fun makeChip(label: String, highlight: Boolean, onTap: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 16f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(260)
            setTextColor(if (highlight) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (highlight) 0xFFFFC94D.toInt() else 0xFF333333.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener { onTap() }
        }

    /** Kata yang sedang diketik (huruf/angka tepat sebelum kursor). */
    private fun currentWord(): String {
        val before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString() ?: return ""
        var i = before.length
        while (i > 0 && before[i - 1].isLetterOrDigit()) i--
        return before.substring(i)
    }

    private fun updateSuggestions() {
        val row = suggestionRow ?: return
        val word = currentWord()
        val key = word.lowercase()

        // Tombol batal: tampil selama teks keren yang baru disisipkan masih tepat sebelum kursor.
        val undo = lastReplacement?.takeIf { (inserted, _) ->
            currentInputConnection?.getTextBeforeCursor(inserted.length, 0)?.toString() == inserted
        }
        if (undo == null) lastReplacement = null

        if (undo != null) {
            val stateKey = "undo|" + undo.first
            if (stateKey != lastSuggestKey) {
                lastSuggestKey = stateKey
                row.removeAllViews()
                row.addView(makeChip("↩ batal (${undo.second})", false) { undoReplacement() })
            }
            return
        }

        val matches = if (key.length < 2) emptyList() else teksKeren
            .filter { it.singkatan.isNotEmpty() && it.singkatan.startsWith(key) }
            .sortedWith(compareBy({ it.singkatan != key }, { it.singkatan.length }))
            .take(8)

        if (matches.isNotEmpty()) {
            suggestionScroll?.scrollTo(0, 0)
            val stateKey = "match|$key"
            if (stateKey == lastSuggestKey) return
            lastSuggestKey = stateKey
            row.removeAllViews()
            matches.forEach { item ->
                row.addView(makeChip(item.teks, item.singkatan == key) { applyTeksKeren(word, item.teks) })
            }
            return
        }

        // Tidak ada saran -> tampilkan teks yang sedang diketik, supaya tetap terlihat
        // walaupun kolom isian di aplikasi tertutup keyboard (mis. Tokopedia).
        if (lastSuggestKey != "preview") {
            lastSuggestKey = "preview"
            row.removeAllViews()
            row.addView(previewView)
        }
        previewView.text = previewText()
        // Kursor biasanya di akhir -> geser bar ke kanan supaya ketikan terbaru terlihat.
        suggestionScroll?.post { suggestionScroll?.fullScroll(View.FOCUS_RIGHT) }
    }

    private val previewView: TextView by lazy {
        TextView(this).apply {
            textSize = 17f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            setTextColor(0xFFDDDDDD.toInt())
            setPadding(dp(4), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun isPasswordField(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: return false
        val v = t and android.text.InputType.TYPE_MASK_VARIATION
        val c = t and android.text.InputType.TYPE_MASK_CLASS
        return (c == android.text.InputType.TYPE_CLASS_TEXT &&
            (v == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
             v == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
             v == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
            (c == android.text.InputType.TYPE_CLASS_NUMBER &&
             v == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    /** Potongan teks di sekitar kursor, ditampilkan di bar atas keyboard. */
    private fun previewText(): CharSequence {
        if (isPasswordField()) return "•••••"
        val ic = currentInputConnection ?: return ""
        val before = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty().replace('\n', ' ')
        val after = ic.getTextAfterCursor(30, 0)?.toString().orEmpty().replace('\n', ' ')
        if (before.isEmpty() && after.isEmpty()) return ""
        return android.text.SpannableStringBuilder().apply {
            append(before)
            val c = length
            append("|")
            setSpan(android.text.style.ForegroundColorSpan(0xFFFFC94D.toInt()),
                c, c + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(after)
        }
    }

    private fun applyTeksKeren(word: String, teks: String) {
        val ic = currentInputConnection ?: return
        // Pastikan kata yang diketik masih ada tepat sebelum kursor.
        if (currentWord() != word) { updateSuggestions(); return }
        val inserted = "$teks "
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(inserted, 1)
        ic.endBatchEdit()
        lastReplacement = inserted to word
        autoCapNext = false
        updateShiftVisual()
        lastSuggestKey = "\u0000"
        updateSuggestions()
    }

    /** Kembalikan teks keren terakhir ke kata aslinya. Return true kalau berhasil. */
    private fun undoReplacement(): Boolean {
        val (inserted, original) = lastReplacement ?: return false
        val ic = currentInputConnection ?: return false
        if (ic.getTextBeforeCursor(inserted.length, 0)?.toString() != inserted) {
            lastReplacement = null
            return false
        }
        ic.beginBatchEdit()
        ic.deleteSurroundingText(inserted.length, 0)
        ic.commitText(original, 1)
        ic.endBatchEdit()
        lastReplacement = null
        lastSuggestKey = "\u0000"
        updateSuggestions()
        return true
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateSuggestions()
    }

    // --- Voice typing (mic) ---

    private fun setMicKeyLabel(label: String) {
        val key = lettersKeyboard.keys.firstOrNull { it.codes.isNotEmpty() && it.codes[0] == KEYCODE_MIC }
        key?.label = label
        keyboardView.invalidateAllKeys()
    }

    private fun toggleMic() {
        if (isListening) {
            // Tap kedua: berhenti dengar, proses jadi teks.
            speechRecognizer?.stopListening()
            return
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Izinkan akses mikrofon dulu di app Big Keyboard",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(this, SetupActivity::class.java).apply {
                putExtra(SetupActivity.EXTRA_REQUEST_MIC, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition tidak tersedia di HP ini", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(createRecognitionListener())

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer.startListening(intent)
            isListening = true
            setMicKeyLabel(MIC_LABEL_LISTENING)
        } catch (e: Exception) {
            Log.e("BigKeyboardIME", "Gagal memulai speech recognizer", e)
            Toast.makeText(this, "Gagal memulai mikrofon", Toast.LENGTH_SHORT).show()
            stopListeningAndReset()
        }
    }

    private fun stopListeningAndReset() {
        isListening = false
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
            // Abaikan; recognizer mungkin sudah dalam keadaan tidak valid.
        }
        speechRecognizer = null
        if (::lettersKeyboard.isInitialized) {
            setMicKeyLabel(MIC_LABEL_IDLE)
        }
    }

    private fun createRecognitionListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "Tidak terdengar jelas, coba lagi"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada suara terdeteksi"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon belum diberikan"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Butuh koneksi internet"
                else -> null
            }
            if (message != null) {
                Toast.makeText(this@BigKeyboardIME, message, Toast.LENGTH_SHORT).show()
            }
            stopListeningAndReset()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()
            if (!text.isNullOrBlank()) {
                val ic = currentInputConnection
                ic?.commitText("$text ", 1)
                // Kalimat baru dari suara -> kapital lagi untuk kata selanjutnya.
                autoCapNext = false
            }
            stopListeningAndReset()
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic: InputConnection = currentInputConnection ?: return
        // Tombol apa pun selain DEL membatalkan kesempatan "batal" teks keren.
        if (primaryCode != Keyboard.KEYCODE_DELETE) lastReplacement = null

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                // Kalau long-press (3 detik) sudah menghapus semua teks,
                // jangan proses hapus satu-huruf lagi di sini.
                if (isDeleteLongPress) {
                    isDeleteLongPress = false
                } else if (undoReplacement()) {
                    // Backspace tepat setelah memilih teks keren -> kembali ke kata asli.
                } else {
                    val selected = ic.getSelectedText(0)
                    if (selected.isNullOrEmpty()) {
                        ic.deleteSurroundingText(1, 0)
                    } else {
                        ic.commitText("", 1)
                    }
                }
            }

            Keyboard.KEYCODE_SHIFT -> {
                val now = System.currentTimeMillis()
                when {
                    isCapsLocked -> {
                        // Sedang terkunci -> sekali pencet buang kuncian.
                        isCapsLocked = false
                        isShifted = false
                    }
                    now - lastShiftTapTime < DOUBLE_TAP_MS -> {
                        // Double-tap -> kunci huruf besar semua.
                        isCapsLocked = true
                        isShifted = false
                    }
                    else -> {
                        // Single tap -> shift sekali pakai.
                        isShifted = !isShifted
                    }
                }
                lastShiftTapTime = now
                updateShiftVisual()
            }

            Keyboard.KEYCODE_DONE -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                autoCapNext = true // baris baru dianggap awal kalimat baru
                updateShiftVisual()
            }

            KEYCODE_TO_SYMBOLS -> {
                currentPage = PageMode.SYMBOLS
                keyboardView.keyboard = symbolsKeyboard
            }

            KEYCODE_TO_LETTERS -> {
                currentPage = PageMode.LETTERS
                keyboardView.keyboard = lettersKeyboard
            }

            KEYCODE_TO_ALT -> {
                currentPage = PageMode.ALT
                keyboardView.keyboard = altKeyboard
            }

            KEYCODE_SWITCH_IME -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }

            KEYCODE_MIC -> {
                toggleMic()
            }

            else -> {
                var code = primaryCode.toChar()
                val shouldCapitalize = isCapsLocked || isShifted || autoCapNext
                if (shouldCapitalize && Character.isLetter(code)) {
                    code = Character.toUpperCase(code)
                }
                ic.commitText(code.toString(), 1)

                if (Character.isLetter(code)) {
                    // Huruf sudah dipakai -> shift sekali-pakai & auto-cap nonaktif lagi,
                    // KECUALI kalau sedang caps-lock (tetap besar terus).
                    if (!isCapsLocked) {
                        isShifted = false
                        autoCapNext = false
                    }
                }

                // Setelah tanda titik -> huruf berikutnya otomatis kapital lagi.
                if (code == '.') {
                    autoCapNext = true
                }

                updateShiftVisual()
            }
        }
    }

    override fun onPress(primaryCode: Int) {
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            isDeleteLongPress = false
            handler.postDelayed(clearAllRunnable, LONG_PRESS_DELETE_MS)
        }
    }

    override fun onRelease(primaryCode: Int) {
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handler.removeCallbacks(clearAllRunnable)
        }
    }

    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
