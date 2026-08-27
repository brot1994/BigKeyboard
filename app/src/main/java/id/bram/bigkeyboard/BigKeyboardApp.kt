package id.bram.bigkeyboard

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class BigKeyboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(filesDir, "last_crash.txt").writeText(sw.toString())
            } catch (_: Throwable) {
                // Kalau logging-nya sendiri gagal, jangan sampai bikin loop error baru.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
