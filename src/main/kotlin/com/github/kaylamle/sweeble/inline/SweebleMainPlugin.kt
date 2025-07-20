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
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.github.kaylamle.sweeble.services.OpenAIService
import com.github.kaylamle.sweeble.services.SweebleSettingsState
import com.github.kaylamle.sweeble.services.NextEditSuggestionService
import com.github.kaylamle.sweeble.services.CodeAnalysisService
import com.github.kaylamle.sweeble.services.SuggestionType
import com.github.kaylamle.sweeble.services.ChangeType
import com.github.kaylamle.sweeble.services.SuggestionClassification
import com.github.kaylamle.sweeble.services.CodeChangeApplicationService
import com.github.kaylamle.sweeble.highlighting.ChangeHighlighter
import com.github.kaylamle.sweeble.actions.ApplyComplexEditAction
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

class SweebleMainPlugin : InlineCompletionProvider {
    companion object {
        private val LOG = Logger.getInstance(SweebleMainPlugin::class.java)
        private const val DEBOUNCE_DELAY_MS = 300L // Wait 300ms before processing a request
    }

    private val openAIService = OpenAIService()
    private val nextEditSuggestionService = NextEditSuggestionService()
    private val codeAnalysisService = CodeAnalysisService()
    private val codeChangeApplicationService = CodeChangeApplicationService()
    private val changeHighlighter = ChangeHighlighter()
    
    // Track the current request to cancel previous ones
    private var currentRequestJob: Job? = null
    private val requestCounter = AtomicLong(0)
    private var lastProcessedContextHash: String? = null
    
    // Store the current complex edit suggestion for application
    private var currentComplexEditChanges: List<com.github.kaylamle.sweeble.services.CodeChange>? = null
    private var currentEditor: Editor? = null

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
            override val suggestionFlow: Flow<InlineCompletionElement> = flowOf()
            
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
                                    // Filter out any inline completion text that might be in the document
                                    val cleanBeforeContext = filterInlineCompletionText(beforeContext)
                                    val cleanAfterContext = filterInlineCompletionText(afterContext)
                                    val fullContext = "$cleanBeforeContext[CURSOR_HERE]$cleanAfterContext"
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
        
        // Cancel any previous request and clean up highlights
        currentRequestJob?.cancel()
        changeHighlighter.cleanup()
        currentComplexEditChanges = null
        currentEditor = null
        
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
                
                // Get the editor from the request
                val editor = getEditorFromRequest(request)
                if (editor == null) {
                    LOG.debug("Could not get editor from request $requestId")
                    return@launch
                }
                
                // Check if this request is still current
                if (currentRequestJob == this) {
                    // Use mini model to classify the change type
                    LOG.debug("Classifying change type for request $requestId...")
                    val changeType = openAIService.classifyChangeType(context, language)
                    
                    when (changeType) {
                        SuggestionClassification.SIMPLE_INSERTION -> {
                            // Use simple completion (the original, well-tested logic)
                            LOG.debug("Classification: SIMPLE_INSERTION, trying simple completion for request $requestId...")
                            val simpleCompletion = openAIService.getCompletion(context, language)
                            
                            if (simpleCompletion != null && simpleCompletion.isNotBlank()) {
                                LOG.info("Simple completion successful for request $requestId: '$simpleCompletion'")
                                val element = InlineCompletionTextElement(simpleCompletion) { editor ->
                                    TextAttributes().apply {
                                        backgroundColor = java.awt.Color(173, 216, 230, 128) // 50% opacity light blue
                                    }
                                }
                                suggestionElements.value = element
                                lastProcessedContextHash = contextHash
                            } else {
                                LOG.debug("Simple completion returned null/empty for request $requestId")
                            }
                        }
                        SuggestionClassification.COMPLEX_EDIT -> {
                            // Use complex edit suggestions
                            LOG.debug("Classification: COMPLEX_EDIT, trying complex edit suggestions for request $requestId...")
                            val nextEditSuggestions = nextEditSuggestionService.getNextEditSuggestions(editor, context, language)
                            
                            if (nextEditSuggestions.isNotEmpty()) {
                                // Use the highest confidence suggestion
                                val bestSuggestion = nextEditSuggestions.first()
                                LOG.info("Best next edit suggestion for request $requestId: ${bestSuggestion.type}")
                                
                                when (bestSuggestion.type) {
                                    SuggestionType.COMPLEX_EDIT, SuggestionType.MULTIPLE_CHANGES -> {
                                        // For complex edits, show red highlighting AND green inlay hints ONLY
                                        if (bestSuggestion.changes.isNotEmpty()) {
                                            val changeCount = bestSuggestion.changes.size
                                            
                                            // Highlight the lines to be replaced in soft red AND show green inlays
                                            changeHighlighter.highlightChanges(editor, bestSuggestion.changes)
                                            
                                            // Store the changes for later application and setup the action
                                            currentComplexEditChanges = bestSuggestion.changes
                                            currentEditor = editor
                                            ApplyComplexEditAction.setCurrentChanges(bestSuggestion.changes, changeHighlighter, editor)
                                            
                                            LOG.info("Showing complex edit with red highlighting and green inlays")
                                            LOG.debug("Full changes: ${bestSuggestion.changes}")
                                            
                                            // CRITICAL: Don't set suggestionElements.value for complex edits - use inlay hints instead
                                            // This ensures simple inline suggestions never show with complex edits
                                            lastProcessedContextHash = contextHash
                                        }
                                    }
                                    SuggestionType.SIMPLE_INSERTION -> {
                                        // This shouldn't happen since we classified as COMPLEX_EDIT
                                        LOG.debug("Unexpected SIMPLE_INSERTION from NextEditSuggestionService")
                                    }
                                }
                            } else {
                                LOG.debug("No complex edit suggestions available for request $requestId")
                            }
                        }
                        SuggestionClassification.NO_SUGGESTION -> {
                            // No suggestion needed
                            LOG.debug("Classification: NO_SUGGESTION, no suggestion needed for request $requestId")
                            lastProcessedContextHash = contextHash
                        }
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
    
    private fun getEditorFromRequest(request: InlineCompletionRequest): Editor? {
        return try {
            val requestMethods = request::class.java.methods
            val editorMethod = requestMethods.find { it.name == "getEditor" && it.parameterCount == 0 }
            if (editorMethod != null) {
                val editor = editorMethod.invoke(request)
                if (editor is Editor) {
                    editor
                } else null
            } else null
        } catch (e: Exception) {
            LOG.debug("Error getting editor from request: ${e.message}")
            null
        }
    }
    
    private fun buildNewTextFromChanges(changes: List<com.github.kaylamle.sweeble.services.CodeChange>, editor: Editor): String {
        val document = editor.document
        
        return buildString {
            changes.forEachIndexed { index, change ->
                when (change.type) {
                    ChangeType.INSERT -> {
                        // For insertions, add the new text as complete lines
                        if (index > 0) append("\n")
                        append(change.newText)
                    }
                    ChangeType.REPLACE -> {
                        // For replacements, show the new text as complete lines
                        if (index > 0) append("\n")
                        append(change.newText)
                    }
                    ChangeType.DELETE -> {
                        // For deletions, don't show anything in inline completion
                        // The red highlighting will show what's being deleted
                    }
                }
            }
        }.trim() // Remove any trailing whitespace
    }
    
    private fun applyComplexEditChanges() {
        val changes = currentComplexEditChanges ?: return
        val editor = currentEditor ?: return
        
        ApplicationManager.getApplication().runWriteAction {
            // Sort changes by offset in reverse order to avoid offset shifting
            val sortedChanges = changes.sortedByDescending { it.startOffset }
            
            sortedChanges.forEach { change ->
                when (change.type) {
                    ChangeType.REPLACE -> {
                        editor.document.replaceString(change.startOffset, change.endOffset, change.newText)
                    }
                    ChangeType.INSERT -> {
                        editor.document.insertString(change.startOffset, change.newText)
                    }
                    ChangeType.DELETE -> {
                        editor.document.deleteString(change.startOffset, change.endOffset)
                    }
                }
            }
        }
        
        // Cleanup highlighters
        changeHighlighter.cleanup()
        currentComplexEditChanges = null
        currentEditor = null
    }
    
    private fun filterInlineCompletionText(text: String): String {
        // Remove any inline completion text that might be present
        // This is a simple approach - in a real implementation, you'd need to track
        // what text was added by inline completions and filter it out
        return text
    }
    

    

}