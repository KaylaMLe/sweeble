package com.github.kaylamle.sweeble.services

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration

class OpenAIService {
    companion object {
        private val LOG = Logger.getInstance(OpenAIService::class.java)
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-3.5-turbo"
    }

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3)) // Faster timeout
        .build()

    suspend fun getCompletion(
        prompt: String,
        maxTokens: Int = 20, // Shorter completions for faster response
        temperature: Double = 0.3
    ): String? {
        return try {
            val apiKey = System.getenv("OPENAI_API_KEY")
            if (apiKey.isNullOrBlank()) {
                LOG.warn("OpenAI API key not found in environment variables")
                return null
            }

            // Properly escape the prompt for JSON
            val escapedPrompt = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")

            val requestBody = """
                {
                    "model": "$MODEL",
                    "messages": [
                        {
                            "role": "user",
                            "content": "Complete the following code. Return only the completion, no explanations: $escapedPrompt"
                        }
                    ],
                    "max_tokens": $maxTokens,
                    "temperature": $temperature,
                    "stop": ["\n", "```"]
                }
            """.trimIndent()

            LOG.debug("Sending request to OpenAI: $requestBody")

            val request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            // Add timeout to prevent hanging
            val response = withTimeout(5000) { // 5 second timeout
                withContext(Dispatchers.IO) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }
            }

            if (response.statusCode() == 200) {
                // Simple JSON parsing - in production you'd use a proper JSON library
                val responseBody = response.body()
                LOG.debug("OpenAI response: $responseBody")
                
                val contentStart = responseBody.indexOf("\"content\":\"") + 12
                val contentEnd = responseBody.indexOf("\"", contentStart)
                if (contentStart > 11 && contentEnd > contentStart) {
                    responseBody.substring(contentStart, contentEnd)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                } else {
                    null
                }
            } else {
                LOG.error("OpenAI API error: ${response.statusCode()} - ${response.body()}")
                null
            }
        } catch (e: TimeoutCancellationException) {
            LOG.debug("OpenAI API call timed out")
            null
        } catch (e: Exception) {
            // Check if it's a cancellation exception by checking the message
            if (e.message?.contains("cancelled", ignoreCase = true) == true || 
                e.message?.contains("cancellation", ignoreCase = true) == true) {
                LOG.debug("OpenAI API call was cancelled")
            } else {
                LOG.error("Error calling OpenAI API", e)
            }
            null
        }
    }
} 