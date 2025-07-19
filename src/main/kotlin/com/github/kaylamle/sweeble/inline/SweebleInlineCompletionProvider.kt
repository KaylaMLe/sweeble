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
        LOG.info("SweebleInlineCompletionProvider: isEnabled called with event: ${event.javaClass.simpleName}")
        return true
    }

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
        LOG.info("SweebleInlineCompletionProvider: getSuggestion called")
        
        // Get context from the request
        val context = extractContext(request)
        LOG.info("Extracted context: '$context'")
        
        // Only proceed if we have meaningful context
        if (context.isBlank() || context == "Complete this code:") {
            LOG.info("No meaningful context, returning empty suggestion")
            return createEmptySuggestion()
        }
        
        // Get AI completion
        LOG.info("Calling OpenAI API for completion...")
        val completion = openAIService.getCompletion(context)
        LOG.info("AI completion: '$completion'")
        
        return if (completion != null && completion.isNotBlank()) {
            LOG.info("Creating AI suggestion with completion: '$completion'")
            // Create a suggestion with the AI completion using InlineCompletionTextElement
            object : InlineCompletionSuggestion() {
                override val suggestionFlow: Flow<InlineCompletionElement> = flowOf(
                    InlineCompletionTextElement(completion) { editor ->
                        TextAttributes().apply {
                            foregroundColor = Color.GRAY
                            fontType = 0 // Normal font
                        }
                    }
                )
                
                override fun toString(): String {
                    return "SweebleAISuggestion: $completion"
                }
            }
        } else {
            LOG.info("No AI completion received, returning empty suggestion")
            createEmptySuggestion()
        }
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
    
    private fun extractContext(request: InlineCompletionRequest): String {
        return try {
            // Try to get the current text context from the request
            val requestMethods = request::class.java.methods
            LOG.info("Request methods: ${requestMethods.map { it.name }}")
            
            // Look for methods that might give us the current text
            val possibleMethods = listOf("getText", "getContent", "getDocument", "getEditor", "getFile")
            
            for (methodName in possibleMethods) {
                val method = requestMethods.find { it.name == methodName && it.parameterCount == 0 }
                if (method != null) {
                    try {
                        val value = method.invoke(request)
                        if (value != null) {
                            LOG.info("Found $methodName: $value")
                            // If it's an editor, try to get the text
                            if (value is Editor) {
                                // Wrap editor access in read action to fix threading issue
                                return ApplicationManager.getApplication().runReadAction<String> {
                                    val document = value.document
                                    val text = document.text
                                    val offset = value.caretModel.offset
                                    LOG.info("Editor text length: ${text.length}, cursor offset: $offset")
                                    
                                    // Get the last 100 characters before the cursor as context (shorter for faster processing)
                                    val start = maxOf(0, offset - 100)
                                    val context = text.substring(start, offset)
                                    LOG.info("Extracted context length: ${context.length}")
                                    context
                                }
                            }
                            // If it's a string, return it
                            if (value is String) {
                                return value
                            }
                        }
                    } catch (e: Exception) {
                        LOG.info("Error accessing $methodName: ${e.message}")
                    }
                }
            }
            
            // Fallback: return a simple prompt
            LOG.info("No context found, using fallback")
            "Complete this code:"
            
        } catch (e: Exception) {
            LOG.error("Error extracting context", e)
            "Complete this code:"
        }
    }
}