package com.github.kaylamle.sweeble.completion

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.codeInsight.lookup.LookupElementBuilder

class SweebleCompletionContributor : CompletionContributor() {
    companion object {
        private val LOG = Logger.getInstance(SweebleCompletionContributor::class.java)
    }
    
    init {
        LOG.warn("SweebleCompletionContributor: Constructor called - plugin is loading!")
        
        // Try with a more specific pattern for text files
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(com.intellij.lang.Language.findLanguageByID("TEXT") ?: com.intellij.lang.Language.ANY)),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    try {
                        LOG.warn("SweebleCompletionContributor: addCompletions called for file: ${parameters.position.containingFile.name}")
                        
                        // Always use a blank prefix matcher so completions show up regardless of prefix
                        val allResults = result.withPrefixMatcher("")
                        
                        // Create a simple completion
                        val completion = LookupElementBuilder.create("SWEEBLE_TEST")
                            .withPresentableText("🤖 SWEEBLE TEST")
                            .withTypeText("AI Suggestion")
                        
                        // Add it to the result set
                        allResults.addElement(completion)
                        
                        LOG.warn("SweebleCompletionContributor: Successfully added completion to result set (blank prefix)")
                        
                        // Force the result set to refresh
                        allResults.stopHere()
                        
                    } catch (e: Exception) {
                        LOG.error("SweebleCompletionContributor: Error adding completions", e)
                    }
                }
            }
        )
        
        LOG.warn("SweebleCompletionContributor: Extension registered successfully")
    }
} 