package com.github.kaylamle.sweeble.services

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
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

    suspend fun getCompletion(prompt: String, language: String, maxTokens: Int = 100, temperature: Double = 0.3): String? {
        return try {
            LOG.info("OpenAIService: Starting completion request")
            val apiKey = System.getenv("OPENAI_API_KEY")
            if (apiKey.isNullOrBlank()) {
                LOG.warn("OpenAI API key not found in environment variables")
                return null
            }
            LOG.info("OpenAI API key found (length: [${apiKey.length})")
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
                            "content": "You are a helpful coding assistant. Complete the following $language code. Follow $language best practices. The [CURSOR_HERE] marker shows where the cursor is positioned in the code. Return only the completion that should appear at that position. Use proper formatting (including leading newlines) as appropriate for clean, readable code. Start new lines when it makes sense for readability and proper code structure. If the cursor is not at the end of a line, consider starting your completion with a newline. If the code appears incomplete (e.g., missing braces, incomplete statements), focus on completing it. If you're on an empty line, suggest appropriate code. If the code is already complete, return nothing. Do not use markdown formatting in your response - return only plain code.\n\nCode:\n$escapedPrompt"
                        }
                    ],
                    "max_tokens": $maxTokens,
                    "temperature": $temperature,
                    "stop": ["````", "```"]
                }
            """.trimIndent()
            
            LOG.info("Sending request to OpenAI with prompt length: ${prompt.length}")
            LOG.info("Full request body being sent to OpenAI:")
            LOG.info(requestBody)
            val request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
            val response = withTimeout(5000) {
                withContext(Dispatchers.IO) {
                    LOG.info("Making HTTP request to OpenAI...")
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }
            }
            LOG.info("OpenAI response status: ${response.statusCode()}")
            if (response.statusCode() == 200) {
                val responseBody = response.body()
                LOG.info("OpenAI response body length: ${responseBody.length}")
                LOG.info("OpenAI response body: $responseBody")
                val completion = parseOpenAIResponse(responseBody)
                if (completion != null) {
                    LOG.info("Extracted completion: '$completion'")
                    completion
                } else {
                    LOG.warn("Could not parse content from OpenAI response")
                    null
                }
            } else {
                LOG.error("OpenAI API error: ${response.statusCode()} - ${response.body()}")
                null
            }
        } catch (e: TimeoutCancellationException) {
            LOG.info("OpenAI API call timed out")
            null
        } catch (e: Exception) {
            if (e.message?.contains("cancelled", ignoreCase = true) == true || 
                e.message?.contains("cancellation", ignoreCase = true) == true) {
                LOG.info("OpenAI API call was cancelled")
            } else {
                LOG.error("Error calling OpenAI API", e)
            }
            null
        }
    }
    
    private fun parseOpenAIResponse(responseBody: String): String? {
        return try {
            LOG.info("Attempting to parse JSON response...")
            val json = JSONObject(responseBody)
            LOG.info("JSON parsed successfully")
            
            // Navigate through the JSON structure like in JavaScript
            val choices = json.getJSONArray("choices")
            LOG.info("Found choices array with length: ${choices.length()}")
            
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                LOG.info("Got first choice: $firstChoice")
                
                val message = firstChoice.getJSONObject("message")
                LOG.info("Got message: $message")
                
                val content = message.getString("content")
                LOG.info("Raw content from JSON: '$content'")
                LOG.debug("Raw content bytes: ${content.toByteArray().joinToString(", ") { "0x%02X".format(it) }}")
                
                if (content.isNotBlank()) {
                    val processedContent = content
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                    LOG.info("Processed content: '$processedContent'")
                    LOG.debug("Processed content bytes: ${processedContent.toByteArray().joinToString(", ") { "0x%02X".format(it) }}")
                    LOG.debug("Contains newlines: ${processedContent.contains("\n")}")
                    LOG.debug("Contains \\n: ${processedContent.contains("\\n")}")
                    return processedContent
                } else {
                    LOG.warn("Content is blank")
                }
            } else {
                LOG.warn("Choices array is empty")
            }
            
            LOG.warn("No valid content found in OpenAI response")
            null
        } catch (e: Exception) {
            LOG.error("Error parsing OpenAI JSON response: ${e.javaClass.simpleName} - ${e.message}")
            LOG.error("Stack trace:", e)
            null
        }
    }
} 