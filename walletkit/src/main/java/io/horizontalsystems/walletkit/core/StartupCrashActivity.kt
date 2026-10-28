package io.horizontalsystems.walletkit.core

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.horizontalsystems.walletkit.BuildConfig

/**
 * Debug-only screen that shows the exception which stopped the app, so the text can be read on
 * the phone. Launched once per process from the uncaught exception handler.
 */
class StartupCrashActivity : Activity() {

    companion object {
        private const val EXTRA_TEXT = "crash_text"

        @Volatile
        private var shown = false

        /**
         * Opens the crash screen. Only the first crash in a process opens it, so a crash inside
         * this screen cannot start another instance of it.
         */
        fun show(context: Context, text: String) {
            if (!BuildConfig.DEBUG || shown) return
            shown = true
            val intent = Intent(context, StartupCrashActivity::class.java)
                .putExtra(EXTRA_TEXT, text.take(200_000))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: Throwable) {
                // The process is already failing; there is nothing else to do here.
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashText = intent.getStringExtra(EXTRA_TEXT) ?: "No crash text"

        val copyButton = Button(this).apply {
            this.text = "Kopiraj tekst greške"
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("crash", crashText))
                Toast.makeText(this@StartupCrashActivity, "Kopirano", Toast.LENGTH_SHORT).show()
            }
        }

        val body = TextView(this).apply {
            this.text = crashText
            setTextColor(Color.WHITE)
            textSize = 11f
            setTextIsSelectable(true)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(20, 20, 24))
            setPadding(32, 96, 32, 32)
            addView(
                TextView(this@StartupCrashActivity).apply {
                    this.text = "Tariffia Wallet je pao. Tekst greške:"
                    setTextColor(Color.rgb(255, 208, 0))
                    textSize = 16f
                }
            )
            addView(
                copyButton,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            addView(
                ScrollView(this@StartupCrashActivity).apply { addView(body) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
        }
        setContentView(root)
    }
}
