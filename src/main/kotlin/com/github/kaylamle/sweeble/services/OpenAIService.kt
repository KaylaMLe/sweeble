package com.github.kaylamle.sweeble.services

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
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
        private const val MODEL = "gpt-4o"
    }

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3)) // Faster timeout
        .build()

    private fun getApiKey(): String? {
        // First, try to get API key from plugin settings
        val settings = SweebleSettingsState.getInstance()
        val settingsKey = settings.openaiApiKey

        if (settingsKey.isNotBlank()) {
            LOG.info("Using OpenAI API key from plugin settings")
            return settingsKey
        }
        
        // Fall back to system environment variable
        val envKey = System.getenv("OPENAI_API_KEY")

        if (!envKey.isNullOrBlank()) {
            LOG.info("Using OpenAI API key from system environment")
            return envKey
        }
        
        // No API key found
        LOG.error("No OpenAI API key found in plugin settings or system environment")
        return null
    }

    suspend fun getCompletion(prompt: String, language: String, maxTokens: Int = 100, temperature: Double = 0.3): String? {
        return try {
            LOG.info("OpenAIService: Starting completion request")
            val apiKey = getApiKey()

            if (apiKey.isNullOrBlank()) {
                LOG.error("OpenAI API key not found. Please configure it in Settings > Tools > Sweeble AI Assistant or set the OPENAI_API_KEY environment variable.")
                return null
            }

            LOG.info("OpenAI API key found (length: ${apiKey.length} characters)")
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
                            "role": "system",
                            "content": "You are an expert $language programmer. Complete the following $language code by adding code **only at the [CURSOR_HERE] marker**.\n- Do **not** rewrite, modify, or remove any code before or after the [CURSOR_HERE] marker. The code outside the cursor position (both before and after) is locked and must remain unchanged.\n- You are strictly adding code at the cursor position, continuing the current logical unit (such as the rest of a statement, function, method, or class).\n- Complete any unfinished lines starting at the cursor (e.g., missing parameters or syntax) and continue until the current structure is complete.\n- Stop as soon as the current logical unit is completed. Do **not** generate code for any subsequent functions, methods, or classes.\n- Use clean, properly formatted, idiomatic $language code with correct indentation and code style.\n- If it is not possible to produce a valid, syntactically correct completion **only by adding code at the cursor**, return nothing.\n- **Return only the code completion, without explanations or markdown formatting.**\n\nExamples:\n\nExample 1: Completing a function signature and body\nInput:\n    public static int nextPrime[CURSOR_HERE]\nOutput:\n(int n) {\n        if (n <= 2) return 2;\n        int candidate = n % 2 == 0 ? n + 1 : n;\n        while (true) {\n            if (isPrime(candidate)) return candidate;\n            candidate += 2;\n        }\n    }\n\nExample 2: Continuing a function body\nInput:\npublic int sum(int a, int b) {\n    int result = a + b;\n    [CURSOR_HERE]\n}\nOutput:\nreturn result;\n\nExample 3: Completing an unfinished line\nInput:\nList<String> names = new ArrayList<>()[CURSOR_HERE]\nOutput:\n;\n\nExample 4: Do not modify existing lines before or after cursor\nInput:\npublic void logMessage(String message) {\n    System.out.print(message);\n    [CURSOR_HERE]\n    System.out.println();\n}\nOutput:\n// maybe add more logic here\n\nExample 5: Return nothing if a valid insertion is impossible\nInput:\npublic void invalid() {\n    [CURSOR_HERE]\n}\n}\nOutput:\n\n\nExample 6: Adding a new line after a complete line\nInput:\npublic int square(int x) {\n    int result = x * x;[CURSOR_HERE]\n}\nOutput:\n\nreturn result;\n\nExample 7: Start completion on a new line after a complete statement\nInput:\npublic static void logMessages() {\n    System.out.println(\"First message\");[CURSOR_HERE]\n}\nOutput:\n\n    System.out.println(\"Second message\");\n\nExplanation:\n- Example 1: Completes the function signature and body.\n- Example 2: Continues a function body by adding a missing return statement.\n- Example 3: Completes an unfinished line with proper syntax.\n- Example 4: Inserts code between existing lines without modifying them.\n- Example 5: Returns nothing because the surrounding code has invalid syntax that cannot be fixed by insertion alone.\n- Example 6: Starts with a newline after a valid statement and adds the next logical line of code.\n- Example 7: Ensures that new code is inserted on a separate line after a complete statement, respecting proper indentation and avoiding multiple statements on one line.\n\nFollow these examples strictly."
                        },
                        {
                            "role": "user",
                            "content": "$escapedPrompt"
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
        } catch (e: CancellationException) {
            LOG.info("OpenAI API call was cancelled")
            throw e // Re-throw cancellation exceptions to propagate them
        } catch (e: Exception) {
            if (e.message?.contains("cancelled", ignoreCase = true) == true || 
                e.message?.contains("cancellation", ignoreCase = true) == true) {
                LOG.info("OpenAI API call was cancelled")
                throw CancellationException("Request was cancelled", e)
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