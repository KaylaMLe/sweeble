package com.github.kaylamle.sweeble.inline

import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.openapi.diagnostic.Logger

class SweebleInlineCompletionProvider : InlineCompletionProvider {
    companion object {
        private val LOG = Logger.getInstance(SweebleInlineCompletionProvider::class.java)
    }

    init {
        LOG.info("SweebleInlineCompletionProvider: Initializing...")
    }

    override val id: InlineCompletionProviderID = InlineCompletionProviderID("sweeble.ai")

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
        LOG.debug("SweebleInlineCompletionProvider: isEnabled called")
        return true
    }

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
        LOG.debug("SweebleInlineCompletionProvider: getSuggestion called")
        
        // Let me try to find the correct way to create an InlineCompletionSuggestion
        // Since InlineCompletionSuggestion is deprecated but still works, there must be a way to create it
        
        // Let me try to look for factory methods or concrete implementations
        try {
            // Try to find if there are any static factory methods
            val suggestionClass = InlineCompletionSuggestion::class.java
            LOG.debug("InlineCompletionSuggestion class: ${suggestionClass.name}")
            
            // Look for static methods that might be factory methods
            val staticMethods = suggestionClass.methods.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            LOG.debug("InlineCompletionSuggestion static methods: ${staticMethods.size}")
            staticMethods.forEach { method ->
                LOG.debug("  Static method: ${method.name} -> ${method.returnType.name}")
            }
            
            // Look for constructors
            val constructors = suggestionClass.constructors
            LOG.debug("InlineCompletionSuggestion constructors: ${constructors.size}")
            constructors.forEach { constructor ->
                LOG.debug("  Constructor: ${constructor.parameterTypes.map { it.name }}")
            }
            
        } catch (e: Exception) {
            LOG.error("Error inspecting InlineCompletionSuggestion", e)
        }
        
        // For now, let me try to find the correct way to create an InlineCompletionSuggestion
        throw UnsupportedOperationException("InlineCompletionSuggestion creation not implemented yet")
    }
} 