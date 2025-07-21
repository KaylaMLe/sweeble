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

    /**
     * Private generic function to send a prompt to OpenAI and return only the content field from the first choice.
     */
    private suspend fun promptOpenAI(
        systemPrompt: String,
        userPrompt: String,
        model: String,
        maxTokens: Int,
        temperature: Double,
        responseFormat: String? = null,
        stop: List<String>? = null,
        timeoutMs: Long = 5000L
    ): String? {
        // First, try to get API key from plugin settings
        val settings = SweebleSettingsState.getInstance()
        val settingsKey = settings.openaiApiKey
        val apiKey = when {
            settingsKey.isNotBlank() -> {
                LOG.info("Using OpenAI API key from plugin settings")
                settingsKey
            }
            !System.getenv("OPENAI_API_KEY").isNullOrBlank() -> {
                LOG.info("Using OpenAI API key from system environment")
                System.getenv("OPENAI_API_KEY")
            }
            else -> {
                LOG.error("OpenAI API key not found. Please configure it in Settings > Tools > Sweeble AI Assistant or set the OPENAI_API_KEY environment variable.")
                return null
            }
        }
        val escapedPrompt = userPrompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        val stopJson = stop?.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        val responseFormatJson = responseFormat?.let { ",\n                    \"response_format\": $it" } ?: ""
        val stopField = stopJson?.let { ",\n                    \"stop\": $it" } ?: ""
        val requestBody = """
            {
                \"model\": \"$model\",
                \"messages\": [
                    {
                        \"role\": \"system\",
                        \"content\": \"$systemPrompt\"
                    },
                    {
                        \"role\": \"user\",
                        \"content\": \"$escapedPrompt\"
                    }
                ],
                \"max_tokens\": $maxTokens,
                \"temperature\": $temperature$stopField$responseFormatJson
            }
        """.trimIndent()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        return try {
            val response = withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }
            }
            if (response.statusCode() == 200) {
                val responseBody = response.body()
                val json = JSONObject(responseBody)
                val choices = json.getJSONArray("choices")
                if (choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val message = firstChoice.getJSONObject("message")
                    message.getString("content")
                } else null
            } else null
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCompletion(prompt: String, language: String, maxTokens: Int = 100, temperature: Double = 0.3): String? {
        LOG.info("OpenAIService: Starting completion request")
        val systemPrompt = "You are an expert $language programmer. Complete the following $language code by adding code **only at the [CURSOR_HERE] marker**.\n- Do **not** rewrite, modify, or remove any code before or after the [CURSOR_HERE] marker. The code outside the cursor position (both before and after) is locked and must remain unchanged.\n- You are strictly adding code at the cursor position, continuing the current logical unit (such as the rest of a statement, function, method, or class).\n- Complete any unfinished lines starting at the cursor (e.g., missing parameters or syntax) and continue until the current structure is complete.\n- Stop as soon as the current logical unit is completed. Do **not** generate code for any subsequent functions, methods, or classes.\n- Use clean, properly formatted, idiomatic $language code with correct indentation and code style.\n- If it is not possible to produce a valid, syntactically correct completion **only by adding code at the cursor**, return nothing.\n- **Return only the code completion, without explanations or markdown formatting.**\n- **CRITICAL: Always include proper newlines (\\n) when inserting multiple statements or when the insertion should start on a new line.**\n\nExamples:\n\nExample 1: Completing a function signature and body\nInput:\n    public static int nextPrime[CURSOR_HERE]\nOutput:\n(int n) {\n        if (n <= 2) return 2;\n        int candidate = n % 2 == 0 ? n + 1 : n;\n        while (true) {\n            if (isPrime(candidate)) return candidate;\n            candidate += 2;\n        }\n    }\n\nExample 2: Continuing a function body\nInput:\npublic int sum(int a, int b) {\n    int result = a + b;\n    [CURSOR_HERE]\n}\nOutput:\nreturn result;\n\nExample 3: Completing an unfinished line\nInput:\nList<String> names = new ArrayList<>()[CURSOR_HERE]\nOutput:\n;\n\nExample 4: Do not modify existing lines before or after cursor\nInput:\npublic void logMessage(String message) {\n    System.out.print(message);\n    [CURSOR_HERE]\n    System.out.println();\n}\nOutput:\n// maybe add more logic here\n\nExample 5: Return nothing if a valid insertion is impossible\nInput:\npublic void invalid() {\n    [CURSOR_HERE]\n}\n}\nOutput:\n\n\nExample 6: Adding a new line after a complete line\nInput:\npublic int square(int x) {\n    int result = x * x;[CURSOR_HERE]\n}\nOutput:\n\nreturn result;\n\nExample 7: Start completion on a new line after a complete statement\nInput:\npublic static void logMessages() {\n    System.out.println(\"First message\");[CURSOR_HERE]\n}\nOutput:\n\n    System.out.println(\"Second message\");\n\nExample 8: Multiple statements with proper newlines\nInput:\npublic int addFo(int foo, int bar){\n    int foobar = foo + bar;[CURSOR_HERE]\n    return foobar;\n}\nOutput:\n\n        this.foo = foo;\n        this.bar = String.valueOf(bar);\n\nExample 9: Constructor completion with proper newlines\nInput:\npublic HelloWorld(String foo, String bar) {\n    [CURSOR_HERE]\n}\nOutput:\n    this.foo = foo;\n    this.bar = bar;\n\nExplanation:\n- Example 1: Completes the function signature and body.\n- Example 2: Continues a function body by adding a missing return statement.\n- Example 3: Completes an unfinished line with proper syntax.\n- Example 4: Inserts code between existing lines without modifying them.\n- Example 5: Returns nothing because the surrounding code has invalid syntax that cannot be fixed by insertion alone.\n- Example 6: Starts with a newline after a valid statement and adds the next logical line of code.\n- Example 7: Ensures that new code is inserted on a separate line after a complete statement, respecting proper indentation and avoiding multiple statements on one line.\n- Example 8: Shows how to insert multiple statements with proper newlines and indentation.\n- Example 9: Shows how to complete a constructor with proper newlines and indentation.\n\nFollow these examples strictly. ALWAYS include newlines when inserting multiple statements or when the insertion should start on a new line."
        val content = promptOpenAI(
            systemPrompt = systemPrompt,
            userPrompt = prompt,
            model = MODEL,
            maxTokens = maxTokens,
            temperature = temperature,
            stop = listOf("````", "```")
        )
        return content?.let { parseOpenAIResponse(it) }
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
        LOG.info("OpenAIService: Starting complex edit suggestions request")
        val systemPrompt = "You are an expert $language programmer. Analyze the code and identify changes needed to fix the specific problem.\n\nCRITICAL REQUIREMENTS:\n- Focus on the specific problem area around the [CURSOR_HERE] marker\n- Provide the COMPLETE corrected line(s) that should replace the problematic line(s)\n- Work with complete logical units (statements, lines, or blocks)\n- Include proper indentation and newlines in your corrected text\n\nSTEP-BY-STEP PROCESS:\n1. Identify the exact problem (typo, missing character, wrong method name, etc.)\n2. Determine what the corrected code should look like\n3. Provide the COMPLETE corrected line(s) with proper formatting\n\nGuidelines:\n- For typos: Provide the COMPLETE corrected line (e.g., 'retrn \"HelloWorld{\" +' → 'return \"HelloWorld{\" +\\n                \"foo='\" + foo + '\\\\'' +\\n                \", bar='\" + bar + '\\\\'' +\\n                '}';')\n- For wrong types: Provide the COMPLETE corrected line (e.g., 'public HelloWorld(int foo, String bar)' → 'public HelloWorld(String foo, String bar)')\n- For missing elements: Provide the complete missing code\n- For wrong method calls: Provide the complete corrected method call\n- Include proper indentation and newlines\n- The [CURSOR_HERE] marker shows where the user is typing\n- Confidence should be between 0.0 and 1.0\n\nEXAMPLES:\n- Typo 'retrn' → corrected text: 'return \"HelloWorld{\" +\\n                \"foo='\" + foo + '\\\\'' +\\n                \", bar='\" + bar + '\\\\'' +\\n                '}';'\n- Wrong type 'int' → corrected text: 'public HelloWorld(String foo, String bar) {'\n- Missing semicolon → corrected text: 'String message = \"Hello World\";'\n\nCRITICAL: Provide the COMPLETE corrected line(s), not just the corrected part."
        val responseFormat = "{\n                        \"type\": \"json_schema\",\n                        \"json_schema\": {\n                            \"name\": \"ComplexEditChanges\",\n                            \"schema\": {\n                                \"type\": \"object\",\n                                \"properties\": {\n                                    \"changes\": {\n                                        \"type\": \"array\",\n                                        \"items\": {\n                                            \"type\": \"object\",\n                                            \"properties\": {\n                                                \"type\": {\n                                                    \"type\": \"string\",\n                                                    \"enum\": [\"INSERT\", \"REPLACE\", \"DELETE\"],\n                                                    \"description\": \"The type of change to make\"\n                                                },\n                                                \"oldText\": {\n                                                    \"type\": \"string\",\n                                                    \"description\": \"The problematic text to find and replace (for REPLACE/DELETE) or empty string (for INSERT)\"\n                                                },\n                                                \"newText\": {\n                                                    \"type\": \"string\",\n                                                    \"description\": \"The corrected text to insert or replace with\"\n                                                },\n                                                \"confidence\": {\n                                                    \"type\": \"number\",\n                                                    \"minimum\": 0.0,\n                                                    \"maximum\": 1.0,\n                                                    \"description\": \"Confidence level of this change (0.0 to 1.0)\"\n                                                }\n                                            },\n                                            \"required\": [\"type\", \"oldText\", \"newText\", \"confidence\"]\n                                        }\n                                    }\n                                },\n                                \"required\": [\"changes\"]\n                            }\n                        }\n                    }"
        val content = promptOpenAI(
            systemPrompt = systemPrompt,
            userPrompt = prompt,
            model = MODEL,
            maxTokens = maxTokens,
            temperature = temperature,
            responseFormat = responseFormat
        )
        return content?.let { parseComplexEditSuggestions(it) } ?: emptyList()
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
        LOG.info("OpenAIService: Starting change type classification request")
        val systemPrompt = "You are a code analysis expert. Analyze the given code and determine what type of change is needed at the [CURSOR_HERE] marker.\n\nExamples:\n- 'public void test[CURSOR_HERE]' -> SIMPLE_INSERTION (completing method name)\n- 'public void test() { [CURSOR_HERE] }' -> SIMPLE_INSERTION (completing method body)\n- 'public HelloWo[CURSOR_HERE]rld(String foo, String bar) {' -> NO_SUGGESTION (class name already complete)\n- 'int x = String.parseInt[CURSOR_HERE]' -> COMPLEX_EDIT (fixing incorrect method name)\n- 'public int addFo[CURSOR_HERE](int foo, int bar)' -> COMPLEX_EDIT (fixing method signature syntax)\n- 'public void test() { return; [CURSOR_HERE] }' -> NO_SUGGESTION (method already complete)"
        val responseFormat = "{\n                        \"type\": \"json_schema\",\n                        \"json_schema\": {\n                            \"name\": \"ChangeClassification\",\n                            \"schema\": {\n                                \"type\": \"object\",\n                                \"properties\": {\n                                    \"classification\": {\n                                        \"type\": \"string\",\n                                        \"enum\": [\"SIMPLE_INSERTION\", \"COMPLEX_EDIT\", \"NO_SUGGESTION\"],\n                                        \"description\": \"The type of change needed at the cursor position\"\n                                    }\n                                },\n                                \"required\": [\"classification\"]\n                            }\n                        }\n                    }"
        val content = promptOpenAI(
            systemPrompt = systemPrompt,
            userPrompt = context,
            model = MINI_MODEL,
            maxTokens = 10,
            temperature = 0.0,
            responseFormat = responseFormat,
            timeoutMs = 3000L
        )
        return content?.let { parseClassificationResponse(it) } ?: SuggestionClassification.NO_SUGGESTION
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