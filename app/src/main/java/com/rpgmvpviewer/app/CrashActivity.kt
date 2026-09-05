package com.rpgmvpviewer.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Экран, который показывается вместо мгновенного вылета приложения.
 * Показывает полный текст ошибки и сразу копирует его в буфер обмена,
 * чтобы можно было вставить текст в чат/сообщение для диагностики.
 */
class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = intent.getStringExtra(EXTRA_TRACE) ?: "Текст ошибки не получен"

        val textView = TextView(this).apply {
            text = "Приложение упало с ошибкой.\n" +
                "Текст ниже уже скопирован в буфер обмена — просто вставьте его в сообщение.\n\n" +
                trace
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
            gravity = Gravity.START
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
        }
        setContentView(scrollView)

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("crash_trace", trace))
            Toast.makeText(this, "Текст ошибки скопирован в буфер обмена", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // не критично, если не получилось скопировать автоматически
        }
    }

    companion object {
        const val EXTRA_TRACE = "extra_trace"
    }
}
