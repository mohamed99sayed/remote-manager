package com.example.remotemanager

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var chatIdInput: EditText
    private lateinit var botTokenInput: EditText
    private lateinit var actionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatIdInput = findViewById(R.id.chat_id_input)
        botTokenInput = findViewById(R.id.bot_token_input)
        actionButton = findViewById(R.id.action_button)

        actionButton.setOnClickListener {
            val chatId = chatIdInput.text.toString()
            val botToken = botTokenInput.text.toString()

            if (chatId.isNotEmpty() && botToken.isNotEmpty()) {
                startTelegramService(chatId, botToken)
            } else {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startTelegramService(chatId: String, botToken: String) {
        val intent = Intent(this, TelegramService::class.java).apply {
            putExtra("CHAT_ID", chatId)
            putExtra("BOT_TOKEN", botToken)
        }
        startService(intent)
        Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show()
        finish() // Close UI, service runs in background
    }
}
