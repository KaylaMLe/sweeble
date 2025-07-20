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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.awt.Color

class SweebleInlineCompletionProvider : InlineCompletionProvider {
    companion object {
        private val LOG = Logger.getInstance(SweebleInlineCompletionProvider::class.java)
    }

    private val openAIService = OpenAIService()

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
        LOG.debug("SweebleInlineCompletionProvider: getSuggestion called")
        val (context, language) = extractContext(request)
        LOG.debug("Extracted context: '$context' with language: '$language'")
        
        if (context.isBlank() || context == "Complete this code: [CURSOR_HERE]") {
            LOG.debug("No meaningful context, returning empty suggestion")
            return createEmptySuggestion()
        }
        LOG.debug("Calling OpenAI API for completion...")
        val completion = openAIService.getCompletion(context, language)
        LOG.info("AI completion: '$completion'")
        return if (completion != null && completion.isNotBlank()) {
            LOG.info("Creating AI suggestion with completion: '$completion'")
            object : InlineCompletionSuggestion() {
                override val suggestionFlow: Flow<InlineCompletionElement> = flowOf(
                    InlineCompletionTextElement(completion) { editor ->
                        TextAttributes().apply {
                            foregroundColor = Color.GRAY
                            fontType = 0
                        }
                    }
                )
                override fun toString(): String {
                    return "SweebleAISuggestion: $completion"
                }
            }
        } else {
            LOG.debug("No valid AI completion received, returning empty suggestion")
            createEmptySuggestion()
        }
    }
}