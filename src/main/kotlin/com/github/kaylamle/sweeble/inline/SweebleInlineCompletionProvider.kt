package com.github.kaylamle.sweeble.inline

import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionTextElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.github.kaylamle.sweeble.services.OpenAIService
import com.github.kaylamle.sweeble.services.SweebleSettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.awt.Color
import java.util.concurrent.atomic.AtomicLong
import java.security.MessageDigest

class SweebleInlineCompletionProvider : InlineCompletionProvider {
    companion object {
        private val LOG = Logger.getInstance(SweebleInlineCompletionProvider::class.java)
        private const val DEBOUNCE_DELAY_MS = 300L // Wait 300ms before processing a request
    }

    private val openAIService = OpenAIService()
    
    // Track the current request to cancel previous ones
    private var currentRequestJob: Job? = null
    private val requestCounter = AtomicLong(0)
    private var lastProcessedContextHash: String? = null

    init {
        LOG.info("SweebleInlineCompletionProvider: Initializing...")
        // Test that we can create a simple suggestion
        try {
            val testSuggestion = createTestSuggestion()
            LOG.info("SweebleInlineCompletionProvider: Test suggestion created successfully")
        } catch (e: Exception) {
            LOG.error("SweebleInlineCompletionProvider: Error creating test suggestion", e)
        }
    }

    override val id: InlineCompletionProviderID = InlineCompletionProviderID("sweeble.ai")

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
        LOG.debug("SweebleInlineCompletionProvider: isEnabled called with event: ${event.javaClass.simpleName}")
        return true
    }

    private fun createTestSuggestion(): InlineCompletionSuggestion {
        return object : InlineCompletionSuggestion() {
            override val suggestionFlow: Flow<InlineCompletionElement> = flowOf(
                InlineCompletionTextElement(" // Test completion") { editor ->
                    TextAttributes().apply {
                        foregroundColor = Color.GRAY
                        fontType = 0 // Normal font
                    }
                }
            )
            
            override fun toString(): String {
                return "SweebleTestSuggestion"
            }
        }
    }
    
    private fun createEmptySuggestion(): InlineCompletionSuggestion {
        return object : InlineCompletionSuggestion() {
            override val suggestionFlow: Flow<InlineCompletionElement> = flowOf()
            
            override fun toString(): String {
                return "SweebleEmptySuggestion"
            }
        }
    }
    
    private fun createContextHash(context: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(context.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            LOG.error("Error creating context hash", e)
            context.hashCode().toString()
        }
    }
    
    private fun detectLanguage(editor: Editor): String {
        return try {
            val file = editor.virtualFile
            if (file != null) {
                val extension = file.extension?.lowercase()
                val language = when (extension) {
                    "java" -> "Java"
                    "kt" -> "Kotlin"
                    "py" -> "Python"
                    "js", "ts", "jsx", "tsx" -> "JavaScript/TypeScript"
                    "cpp", "cc", "cxx" -> "C++"
                    "c" -> "C"
                    "cs" -> "C#"
                    "php" -> "PHP"
                    "rb" -> "Ruby"
                    "go" -> "Go"
                    "rs" -> "Rust"
                    "swift" -> "Swift"
                    "scala" -> "Scala"
                    "sql" -> "SQL"
                    "html", "htm" -> "HTML"
                    "css" -> "CSS"
                    "xml" -> "XML"
                    "json" -> "JSON"
                    "yaml", "yml" -> "YAML"
                    "md" -> "Markdown"
                    else -> "code"
                }
                LOG.debug("Detected language: $language for file extension: $extension")
                language
            } else {
                LOG.debug("No file associated with editor, using generic 'code'")
                "code"
            }
        } catch (e: Exception) {
            LOG.debug("Error detecting language: ${e.message}, using generic 'code'")
            "code"
        }
    }

    private fun extractContext(request: InlineCompletionRequest): Pair<String, String> {
        return try {
            val requestMethods = request::class.java.methods
            LOG.debug("Request methods: ${requestMethods.map { it.name }}")
            val possibleMethods = listOf("getText", "getContent", "getDocument", "getEditor", "getFile")
            for (methodName in possibleMethods) {
                val method = requestMethods.find { it.name == methodName && it.parameterCount == 0 }
                if (method != null) {
                    try {
                        val value = method.invoke(request)
                        if (value != null) {
                            LOG.debug("Found $methodName: $value")
                            if (value is Editor) {
                                return ApplicationManager.getApplication().runReadAction<Pair<String, String>> {
                                    val document = value.document
                                    val text = document.text
                                    val offset = value.caretModel.offset
                                    LOG.debug("Editor text length: ${text.length}, cursor offset: $offset")
                                    
                                    // Get context before cursor (500 chars)
                                    val beforeStart = maxOf(0, offset - 500)
                                    val beforeContext = text.substring(beforeStart, offset)
                                    
                                    // Get context after cursor (500 chars - increased to match before)
                                    val afterEnd = minOf(text.length, offset + 500)
                                    val afterContext = text.substring(offset, afterEnd)
                                    
                                    LOG.debug("Before context length: ${beforeContext.length}, After context length: ${afterContext.length}")
                                    LOG.debug("Before context: '$beforeContext'")
                                    LOG.debug("After context: '$afterContext'")
                                    
                                    val language = detectLanguage(value)
                                    
                                    // Create context with before, cursor position, and after
                                    val fullContext = "$beforeContext[CURSOR_HERE]$afterContext"
                                    LOG.info("Full context sent to AI: '$fullContext'")
                                    Pair(fullContext, language)
                                }
                            }
                            if (value is String) {
                                return Pair(value, "code")
                            }
                        }
                    } catch (e: Exception) {
                        LOG.debug("Error accessing $methodName: ${e.message}")
                    }
                }
            }
            LOG.debug("No context found, using fallback")
            Pair("Complete this code: [CURSOR_HERE]", "code")
        } catch (e: Exception) {
            LOG.error("Error extracting context", e)
            Pair("Complete this code: [CURSOR_HERE]", "code")
        }
    }

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
        val requestId = requestCounter.incrementAndGet()
        LOG.debug("SweebleInlineCompletionProvider: getSuggestion called with request ID: $requestId")
        
        // Cancel any previous request
        currentRequestJob?.cancel()
        
        val (context, language) = extractContext(request)
        LOG.debug("Extracted context: '$context' with language: '$language'")
        
        if (context.isBlank() || context == "Complete this code: [CURSOR_HERE]") {
            LOG.debug("No meaningful context, returning empty suggestion")
            return createEmptySuggestion()
        }
        
        // Check if API key is available
        val settings = SweebleSettingsState.getInstance()
        val envKey = System.getenv("OPENAI_API_KEY")
        if (settings.openaiApiKey.isBlank() && (envKey.isNullOrBlank())) {
            LOG.warn("No OpenAI API key configured")
            return object : InlineCompletionSuggestion() {
                override val suggestionFlow: Flow<InlineCompletionElement> = flowOf(
                    InlineCompletionTextElement(" // Configure OpenAI API key in Settings > Tools > Sweeble AI Assistant") { editor ->
                        TextAttributes().apply {
                            foregroundColor = Color.ORANGE
                            fontType = 0
                        }
                    }
                )
                override fun toString(): String {
                    return "SweebleConfigSuggestion"
                }
            }
        }
        
        // Check if this context is identical to the last processed context
        val contextHash = createContextHash(context)
        if (contextHash == lastProcessedContextHash) {
            LOG.debug("Context identical to last processed context, skipping request $requestId")
            return createEmptySuggestion()
        }
        
        // Create a mutable state flow for the suggestion elements
        val suggestionElements = MutableStateFlow<InlineCompletionElement?>(null)
        
        // Create a new job for this request
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Debounce: wait a bit before processing to avoid rapid successive requests
                delay(DEBOUNCE_DELAY_MS)
                
                // Check if this request is still current after debounce
                if (currentRequestJob != this) {
                    LOG.debug("Request $requestId was superseded during debounce period")
                    return@launch
                }
                
                LOG.debug("Calling OpenAI API for completion with request ID: $requestId...")
                val completion = openAIService.getCompletion(context, language)
                LOG.info("AI completion for request $requestId: '$completion'")
                
                // Check if this request is still current
                if (currentRequestJob == this) {
                    if (completion != null && completion.isNotBlank()) {
                        LOG.info("Creating AI suggestion with completion for request $requestId: '$completion'")
                        val element = InlineCompletionTextElement(completion) { editor ->
                            TextAttributes().apply {
                                foregroundColor = Color.GRAY
                                fontType = 0
                            }
                        }
                        suggestionElements.value = element
                        lastProcessedContextHash = contextHash
                    } else {
                        LOG.debug("No valid AI completion received for request $requestId")
                    }
                } else {
                    LOG.debug("Request $requestId was superseded by a newer request, discarding result")
                }
            } catch (e: CancellationException) {
                LOG.debug("Request $requestId was cancelled")
            } catch (e: Exception) {
                LOG.error("Error in request $requestId", e)
            }
        }
        
        currentRequestJob = job
        
        // Return a suggestion that will be updated asynchronously
        return object : InlineCompletionSuggestion() {
            override val suggestionFlow: Flow<InlineCompletionElement> = suggestionElements.filterNotNull()
            
            override fun toString(): String {
                return "SweebleAsyncSuggestion(requestId: $requestId)"
            }
        }
    }
}