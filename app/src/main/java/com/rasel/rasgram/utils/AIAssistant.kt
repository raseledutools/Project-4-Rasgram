package com.rasel.rasgram.utils

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.rasel.rasgram.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AIAssistant {
    
    // To make this fully functional, the user needs to provide a Gemini API Key.
    // We will read it from a constant for now. 
    // In production, this should be fetched securely or provided by the user in settings.
    var apiKey: String = "" // Placeholder

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
    }

    suspend fun generateSmartReplies(messages: List<Message>): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext listOf("Okay!", "Sounds good.", "I'll get back to you later.")
        }
        
        try {
            val recentMessages = messages.takeLast(5).joinToString("\n") { 
                "${it.senderMobile}: ${it.text}" 
            }
            
            val prompt = """
                Based on the following recent chat history, generate 3 short, distinct, and natural reply options for the recipient. 
                Format the output strictly as a JSON array of strings. No markdown, no extra text.
                
                Chat History:
                $recentMessages
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val responseText = response.text?.trim() ?: ""
            
            // basic JSON array parsing manually to avoid adding more dependencies just for this
            val cleanJson = responseText.removePrefix("```json").removeSuffix("```").trim()
            val replies = cleanJson.removePrefix("[").removeSuffix("]").split(",").map { 
                it.trim().removePrefix("\"").removeSuffix("\"") 
            }
            if (replies.size >= 3) {
                replies.take(3)
            } else {
                listOf("Okay!", "Sounds good.", "I'll get back to you later.")
            }
        } catch (e: Exception) {
            listOf("Okay!", "Sounds good.", "I'll get back to you later.")
        }
    }

    suspend fun summarizeChat(messages: List<Message>): String = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext "API Key missing. Cannot summarize."
        }

        try {
            val chatHistory = messages.takeLast(50).joinToString("\n") { 
                "${it.senderMobile}: ${it.text}" 
            }
            
            val prompt = """
                Please provide a concise and clear summary of the following chat history. 
                Focus on the main topics discussed, key decisions made, and any action items.
                
                Chat History:
                $chatHistory
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            response.text?.trim() ?: "Could not generate summary."
        } catch (e: Exception) {
            "Error generating summary: ${e.message}"
        }
    }
}
