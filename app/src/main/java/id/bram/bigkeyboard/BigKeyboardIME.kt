package id.bram.bigkeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.TextView

class BigKeyboardIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var lettersKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard

    // --- Shift / caps-lock state ---
    private var isShifted = false          // one-shot shift (kembali normal setelah 1 huruf)
    private var isCapsLocked = false       // caps-lock permanen sampai shift ditekan lagi
    private var lastShiftTapTime = 0L

    // --- Auto-capitalize state ---
    private var autoCapNext = true         // huruf pertama di awal ketik / setelah titik

    private var isSymbolsMode = false

    // --- Long-press DEL state ---
    private val handler = Handler(Looper.getMainLooper())
    private var isDeleteLongPress = false
    private val clearAllRunnable = Runnable {
        clearAllText()
        isDeleteLongPress = true
    }

    companion object {
        private const val KEYCODE_SWITCH_SYMBOLS = -6
        private const val KEYCODE_SWITCH_IME = -2
        private const val KEYCODE_ALT = -10
        private const val DOUBLE_TAP_MS = 350L
        private const val LONG_PRESS_DELETE_MS = 3000L
    }

    override fun onCreateInputView(): View {
        return try {
            keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
            lettersKeyboard = Keyboard(this, R.xml.keyboard_layout)
            symbolsKeyboard = Keyboard(this, R.xml.keyboard_symbols)
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
                setBackgroundColor(0xFF2b2530.toInt())
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isSymbolsMode = false
        isShifted = false
        isCapsLocked = false
        autoCapNext = true // huruf pertama saat mulai ketik = kapital
        keyboardView.keyboard = lettersKeyboard
        updateShiftVisual()
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

            KEYCODE_SWITCH_SYMBOLS -> {
                isSymbolsMode = !isSymbolsMode
                keyboardView.keyboard = if (isSymbolsMode) symbolsKeyboard else lettersKeyboard
            }

            KEYCODE_SWITCH_IME -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }

            KEYCODE_ALT -> {
                // Placeholder: belum ada halaman simbol ke-3.
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
