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
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
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
        private const val LONG_PRESS_DELETE_MS = 3000L
        private const val MIC_LABEL_IDLE = "🎤"      // 🎤
        private const val MIC_LABEL_LISTENING = "⏹"        // ⏹
    }

    override fun onCreateInputView(): View {
        return try {
            keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
            lettersKeyboard = Keyboard(this, R.xml.keyboard_layout)
            symbolsKeyboard = Keyboard(this, R.xml.keyboard_symbols)
            altKeyboard = Keyboard(this, R.xml.keyboard_alt)
            keyboardView.keyboard = lettersKeyboard
            keyboardView.setOnKeyboardActionListener(this)
            keyboardView
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
        keyboardView.keyboard = lettersKeyboard
        updateShiftVisual()
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

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                // Kalau long-press (3 detik) sudah menghapus semua teks,
                // jangan proses hapus satu-huruf lagi di sini.
                if (isDeleteLongPress) {
                    isDeleteLongPress = false
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
