package com.github.kaylamle.sweeble.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration

enum class SuggestionClassification {
    SIMPLE_INSERTION,
    COMPLEX_EDIT,
    NO_SUGGESTION
}

class OpenAIService {
    companion object {
        private val LOG = Logger.getInstance(OpenAIService::class.java)
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o"
        private const val MINI_MODEL = "gpt-4o-mini"
        private val httpClient = HttpClient.newHttpClient()
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
                            "content": "You are an expert $language programmer. Complete the following $language code by adding code **only at the [CURSOR_HERE] marker**.\n- Do **not** rewrite, modify, or remove any code before or after the [CURSOR_HERE] marker. The code outside the cursor position (both before and after) is locked and must remain unchanged.\n- You are strictly adding code at the cursor position, continuing the current logical unit (such as the rest of a statement, function, method, or class).\n- Complete any unfinished lines starting at the cursor (e.g., missing parameters or syntax) and continue until the current structure is complete.\n- Stop as soon as the current logical unit is completed. Do **not** generate code for any subsequent functions, methods, or classes.\n- Use clean, properly formatted, idiomatic $language code with correct indentation and code style.\n- If it is not possible to produce a valid, syntactically correct completion **only by adding code at the cursor**, return nothing.\n- **Return only the code completion, without explanations or markdown formatting.**\n- **CRITICAL: Always include proper newlines (\\n) when inserting multiple statements or when the insertion should start on a new line.**\n\nExamples:\n\nExample 1: Completing a function signature and body\nInput:\n    public static int nextPrime[CURSOR_HERE]\nOutput:\n(int n) {\n        if (n <= 2) return 2;\n        int candidate = n % 2 == 0 ? n + 1 : n;\n        while (true) {\n            if (isPrime(candidate)) return candidate;\n            candidate += 2;\n        }\n    }\n\nExample 2: Continuing a function body\nInput:\npublic int sum(int a, int b) {\n    int result = a + b;\n    [CURSOR_HERE]\n}\nOutput:\nreturn result;\n\nExample 3: Completing an unfinished line\nInput:\nList<String> names = new ArrayList<>()[CURSOR_HERE]\nOutput:\n;\n\nExample 4: Do not modify existing lines before or after cursor\nInput:\npublic void logMessage(String message) {\n    System.out.print(message);\n    [CURSOR_HERE]\n    System.out.println();\n}\nOutput:\n// maybe add more logic here\n\nExample 5: Return nothing if a valid insertion is impossible\nInput:\npublic void invalid() {\n    [CURSOR_HERE]\n}\n}\nOutput:\n\n\nExample 6: Adding a new line after a complete line\nInput:\npublic int square(int x) {\n    int result = x * x;[CURSOR_HERE]\n}\nOutput:\n\nreturn result;\n\nExample 7: Start completion on a new line after a complete statement\nInput:\npublic static void logMessages() {\n    System.out.println(\"First message\");[CURSOR_HERE]\n}\nOutput:\n\n    System.out.println(\"Second message\");\n\nExample 8: Multiple statements with proper newlines\nInput:\npublic int addFo(int foo, int bar){\n    int foobar = foo + bar;[CURSOR_HERE]\n    return foobar;\n}\nOutput:\n\n        this.foo = foo;\n        this.bar = String.valueOf(bar);\n\nExample 9: Constructor completion with proper newlines\nInput:\npublic HelloWorld(String foo, String bar) {\n    [CURSOR_HERE]\n}\nOutput:\n    this.foo = foo;\n    this.bar = bar;\n\nExplanation:\n- Example 1: Completes the function signature and body.\n- Example 2: Continues a function body by adding a missing return statement.\n- Example 3: Completes an unfinished line with proper syntax.\n- Example 4: Inserts code between existing lines without modifying them.\n- Example 5: Returns nothing because the surrounding code has invalid syntax that cannot be fixed by insertion alone.\n- Example 6: Starts with a newline after a valid statement and adds the next logical line of code.\n- Example 7: Ensures that new code is inserted on a separate line after a complete statement, respecting proper indentation and avoiding multiple statements on one line.\n- Example 8: Shows how to insert multiple statements with proper newlines and indentation.\n- Example 9: Shows how to complete a constructor with proper newlines and indentation.\n\nFollow these examples strictly. ALWAYS include newlines when inserting multiple statements or when the insertion should start on a new line."
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

    suspend fun getComplexEditSuggestions(prompt: String, language: String, maxTokens: Int = 500, temperature: Double = 0.2): List<CodeChange> {
        return try {
            LOG.info("OpenAIService: Starting complex edit suggestions request")
            val apiKey = getApiKey()

            if (apiKey.isNullOrBlank()) {
                LOG.error("OpenAI API key not found for complex edit suggestions")
                return emptyList()
            }

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
                            "content": "You are an expert $language programmer. Analyze the code and identify changes needed to fix the specific problem.\n\nCRITICAL REQUIREMENTS:\n- Focus on the specific problem area around the [CURSOR_HERE] marker\n- Provide the COMPLETE corrected line(s) that should replace the problematic line(s)\n- Work with complete logical units (statements, lines, or blocks)\n- Include proper indentation and newlines in your corrected text\n\nSTEP-BY-STEP PROCESS:\n1. Identify the exact problem (typo, missing character, wrong method name, etc.)\n2. Determine what the corrected code should look like\n3. Provide the COMPLETE corrected line(s) with proper formatting\n\nGuidelines:\n- For typos: Provide the COMPLETE corrected line (e.g., 'retrn \"HelloWorld{\" +' → 'return \"HelloWorld{\" +\\n                \"foo='\" + foo + '\\\\'' +\\n                \", bar='\" + bar + '\\\\'' +\\n                '}';')\n- For wrong types: Provide the COMPLETE corrected line (e.g., 'public HelloWorld(int foo, String bar)' → 'public HelloWorld(String foo, String bar)')\n- For missing elements: Provide the complete missing code\n- For wrong method calls: Provide the complete corrected method call\n- Include proper indentation and newlines\n- The [CURSOR_HERE] marker shows where the user is typing\n- Confidence should be between 0.0 and 1.0\n\nEXAMPLES:\n- Typo 'retrn' → corrected text: 'return \"HelloWorld{\" +\\n                \"foo='\" + foo + '\\\\'' +\\n                \", bar='\" + bar + '\\\\'' +\\n                '}';'\n- Wrong type 'int' → corrected text: 'public HelloWorld(String foo, String bar) {'\n- Missing semicolon → corrected text: 'String message = \"Hello World\";'\n\nCRITICAL: Provide the COMPLETE corrected line(s), not just the corrected part."
                        },
                        {
                            "role": "user",
                            "content": "$escapedPrompt"
                        }
                    ],
                    "max_tokens": $maxTokens,
                    "temperature": $temperature,
                    "response_format": {
                        "type": "json_schema",
                        "json_schema": {
                            "name": "ComplexEditChanges",
                            "schema": {
                                "type": "object",
                                "properties": {
                                    "changes": {
                                        "type": "array",
                                        "items": {
                                            "type": "object",
                                            "properties": {
                                                "type": {
                                                    "type": "string",
                                                    "enum": ["INSERT", "REPLACE", "DELETE"],
                                                    "description": "The type of change to make"
                                                },
                                                "oldText": {
                                                    "type": "string",
                                                    "description": "The problematic text to find and replace (for REPLACE/DELETE) or empty string (for INSERT)"
                                                },
                                                "newText": {
                                                    "type": "string",
                                                    "description": "The corrected text to insert or replace with"
                                                },
                                                "confidence": {
                                                    "type": "number",
                                                    "minimum": 0.0,
                                                    "maximum": 1.0,
                                                    "description": "Confidence level of this change (0.0 to 1.0)"
                                                }
                                            },
                                            "required": ["type", "oldText", "newText", "confidence"]
                                        }
                                    }
                                },
                                "required": ["changes"]
                            }
                        }
                    }
                }
            """.trimIndent()
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
            
            val response = withTimeout(5000) {
                withContext(Dispatchers.IO) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }
            }
            
            if (response.statusCode() == 200) {
                val responseBody = response.body()
                LOG.info("Raw OpenAI response for complex edits: $responseBody")
                
                // Check if response was truncated
                if (responseBody.contains("\"finish_reason\": \"length\"")) {
                    LOG.warn("AI response was truncated due to token limit. Consider increasing max_tokens.")
                }
                
                parseComplexEditSuggestions(responseBody)
            } else {
                LOG.error("OpenAI API error for complex edits: ${response.statusCode()} - ${response.body()}")
                emptyList()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            LOG.debug("Complex edit suggestions were cancelled (normal behavior)")
            emptyList()
        } catch (e: Exception) {
            LOG.warn("Error getting complex edit suggestions", e)
            emptyList()
        }
    }
    
    private fun parseComplexEditSuggestions(responseBody: String): List<CodeChange> {
        return try {
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                val content = message.getString("content")
                
                LOG.debug("Raw AI content: $content")
                
                // With response_format: json_schema, the content is already a properly structured JSON object
                val changesArray = try {
                    JSONObject(content).getJSONArray("changes")
                } catch (e: Exception) {
                    LOG.warn("Could not parse changes array from JSON response: ${e.message}")
                    return emptyList()
                }
                
                val changes = mutableListOf<CodeChange>()
                
                for (i in 0 until changesArray.length()) {
                    val changeObj = changesArray.getJSONObject(i)
                    val change = CodeChange(
                        type = ChangeType.valueOf(changeObj.getString("type")),
                        startOffset = 0, // Will be calculated programmatically
                        endOffset = 0,   // Will be calculated programmatically
                        newText = changeObj.getString("newText"),
                        confidence = changeObj.getDouble("confidence"),
                        oldText = changeObj.getString("oldText") // Store the text to find
                    )
                    LOG.info("Parsed change $i: ${change.type} with oldText: '${change.oldText}' newText: '${change.newText}' confidence: ${change.confidence}")
                    changes.add(change)
                }
                
                LOG.info("Successfully parsed ${changes.size} code changes")
                changes.sortedByDescending { it.confidence }
            } else {
                LOG.warn("No choices found in OpenAI response")
                emptyList()
            }
        } catch (e: org.json.JSONException) {
            LOG.warn("Failed to parse JSON from AI response: ${e.message}")
            LOG.debug("Response body: $responseBody")
            
            // Check if the response was truncated
            if (responseBody.contains("\"finish_reason\": \"length\"")) {
                LOG.warn("AI response was truncated due to token limit. Consider increasing max_tokens.")
            }
            
            emptyList()
        } catch (e: Exception) {
            LOG.warn("Error parsing complex edit suggestions: ${e.message}")
            emptyList()
        }
    }

    suspend fun classifyChangeType(context: String, language: String): SuggestionClassification {
        return try {
            LOG.info("OpenAIService: Starting change type classification")
            val apiKey = getApiKey()

            if (apiKey.isNullOrBlank()) {
                LOG.error("OpenAI API key not found for change type classification")
                return SuggestionClassification.NO_SUGGESTION // Default to no suggestion
            }

            val escapedContext = context
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            
            val requestBody = """
                {
                    "model": "$MINI_MODEL",
                    "messages": [
                        {
                            "role": "system",
                            "content": "You are a code analysis expert. Analyze the given code and determine what type of change is needed at the [CURSOR_HERE] marker.\n\nExamples:\n- 'public void test[CURSOR_HERE]' -> SIMPLE_INSERTION (completing method name)\n- 'public void test() { [CURSOR_HERE] }' -> SIMPLE_INSERTION (completing method body)\n- 'public HelloWo[CURSOR_HERE]rld(String foo, String bar) {' -> NO_SUGGESTION (class name already complete)\n- 'int x = String.parseInt[CURSOR_HERE]' -> COMPLEX_EDIT (fixing incorrect method name)\n- 'public int addFo[CURSOR_HERE](int foo, int bar)' -> COMPLEX_EDIT (fixing method signature syntax)\n- 'public void test() { return; [CURSOR_HERE] }' -> NO_SUGGESTION (method already complete)"
                        },
                        {
                            "role": "user",
                            "content": "$escapedContext"
                        }
                    ],
                    "max_tokens": 10,
                    "temperature": 0.0,
                    "response_format": {
                        "type": "json_schema",
                        "json_schema": {
                            "name": "ChangeClassification",
                            "schema": {
                                "type": "object",
                                "properties": {
                                    "classification": {
                                        "type": "string",
                                        "enum": ["SIMPLE_INSERTION", "COMPLEX_EDIT", "NO_SUGGESTION"],
                                        "description": "The type of change needed at the cursor position"
                                    }
                                },
                                "required": ["classification"]
                            }
                        }
                    }
                }
            """.trimIndent()
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
            
            val response = withTimeout(3000) { // Shorter timeout for classification
                withContext(Dispatchers.IO) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }
            }
            
            if (response.statusCode() == 200) {
                val responseBody = response.body()
                LOG.debug("Classification response: $responseBody")
                
                val classification = parseClassificationResponse(responseBody)
                LOG.info("Change type classification: $classification")
                classification
            } else {
                LOG.error("OpenAI API error for classification: ${response.statusCode()} - ${response.body()}")
                SuggestionClassification.NO_SUGGESTION // Default to no suggestion on error
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            LOG.debug("Change type classification was cancelled")
            SuggestionClassification.NO_SUGGESTION
        } catch (e: Exception) {
            LOG.warn("Error classifying change type", e)
            SuggestionClassification.NO_SUGGESTION // Default to no suggestion on error
        }
    }
    
    private fun parseClassificationResponse(responseBody: String): SuggestionClassification {
        return try {
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                val content = message.getString("content")
                
                // Parse the JSON content from the structured response
                val contentJson = JSONObject(content)
                val classification = contentJson.getString("classification")
                
                when (classification.uppercase()) {
                    "SIMPLE_INSERTION" -> SuggestionClassification.SIMPLE_INSERTION
                    "COMPLEX_EDIT" -> SuggestionClassification.COMPLEX_EDIT
                    "NO_SUGGESTION" -> SuggestionClassification.NO_SUGGESTION
                    else -> {
                        LOG.warn("Unknown classification: '$classification', defaulting to NO_SUGGESTION")
                        SuggestionClassification.NO_SUGGESTION
                    }
                }
            } else {
                LOG.warn("No choices in classification response, defaulting to NO_SUGGESTION")
                SuggestionClassification.NO_SUGGESTION
            }
        } catch (e: Exception) {
            LOG.warn("Error parsing classification response: ${e.message}")
            SuggestionClassification.NO_SUGGESTION
        }
    }
}

// Removed ComplexEditSuggestion - using flat CodeChange list with confidence