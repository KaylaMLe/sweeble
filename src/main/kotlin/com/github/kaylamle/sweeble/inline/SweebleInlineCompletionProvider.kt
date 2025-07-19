package com.github.kaylamle.sweeble.inline

import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
        
        // Simple test: Let's try to create a basic suggestion
        // First, let me inspect what we have available in the request
        try {
            LOG.debug("Request class: ${request::class.java.name}")
            LOG.debug("Request methods: ${request::class.java.methods.map { it.name }}")
            
            // Let me try to find if there are any properties we can access
            val requestMethods = request::class.java.methods
            requestMethods.forEach { method ->
                if (method.parameterCount == 0 && method.name.startsWith("get")) {
                    try {
                        val value = method.invoke(request)
                        LOG.debug("  ${method.name}: $value")
                    } catch (e: Exception) {
                        LOG.debug("  ${method.name}: Error accessing")
                    }
                }
            }
            
        } catch (e: Exception) {
            LOG.error("Error inspecting request", e)
        }
        
        // Let me try to find the InlineCompletionSingleSuggestion interface
        try {
            val singleSuggestionClass = Class.forName("com.intellij.codeInsight.inline.completion.InlineCompletionSingleSuggestion")
            LOG.debug("Found InlineCompletionSingleSuggestion interface: ${singleSuggestionClass.name}")
            LOG.debug("Is interface: ${singleSuggestionClass.isInterface}")
            
            // Look for methods in the interface
            val interfaceMethods = singleSuggestionClass.methods
            LOG.debug("InlineCompletionSingleSuggestion methods: ${interfaceMethods.size}")
            interfaceMethods.forEach { method ->
                LOG.debug("  Method: ${method.name} -> ${method.returnType.name}")
            }
            
        } catch (e: ClassNotFoundException) {
            LOG.debug("InlineCompletionSingleSuggestion interface not found")
        } catch (e: Exception) {
            LOG.error("Error looking for InlineCompletionSingleSuggestion", e)
        }
        
        // Let me try to create a simple test suggestion
        // Since InlineCompletionSuggestion is abstract, let me try to create a mock implementation
        return object : InlineCompletionSuggestion() {
            override val suggestionFlow: Flow<InlineCompletionElement> = flowOf()
            
            override fun toString(): String {
                return "SweebleTestSuggestion"
            }
        }
    }
} 