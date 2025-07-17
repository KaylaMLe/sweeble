package com.github.kaylamle.sweeble.completion

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.github.kaylamle.sweeble.services.OpenAIService
import com.github.kaylamle.sweeble.services.GitService
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

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
                        val project = parameters.position.project
                        val file = parameters.position.containingFile
                        val editor = parameters.editor
                        val offset = parameters.offset
                        
                        LOG.warn("SweebleCompletionContributor: addCompletions called for file: ${file.name}")
                        
                        // Check if file is gitignored
                        if (GitService.isGitIgnored(project, file)) {
                            LOG.info("SweebleCompletionContributor: File is gitignored, skipping completions")
                            return
                        }
                        
                        // Always use a blank prefix matcher so completions show up regardless of prefix
                        val allResults = result.withPrefixMatcher("")
                        
                        // Get AI completions
                        val openAIService = OpenAIService.getInstance(project)
                        val aiCompletions = openAIService.getCompletions(project, file, editor, offset)
                        
                        if (aiCompletions.isNotEmpty()) {
                            LOG.info("SweebleCompletionContributor: Got ${aiCompletions.size} AI completions")
                            
                            // Add AI completions
                            aiCompletions.forEach { completion ->
                                val lookupElement = LookupElementBuilder.create(completion.text)
                                    .withPresentableText("🤖 ${completion.text}")
                                    .withTypeText("AI Suggestion")
                                    .withBoldness(true)
                                
                                allResults.addElement(lookupElement)
                            }
                        } else {
                            LOG.info("SweebleCompletionContributor: No AI completions, adding fallback")
                            
                            // Add fallback completion if no AI suggestions
                            val fallbackCompletion = LookupElementBuilder.create("SWEEBLE_NO_AI")
                                .withPresentableText("🤖 No AI suggestions available")
                                .withTypeText("AI Suggestion")
                                .withBoldness(true)
                            
                            allResults.addElement(fallbackCompletion)
                        }
                        
                        LOG.warn("SweebleCompletionContributor: Successfully added completions to result set")
                        
                        // Force the result set to refresh
                        allResults.stopHere()
                        
                    } catch (e: Exception) {
                        LOG.error("SweebleCompletionContributor: Error adding completions", e)
                        
                        // Add error completion
                        val errorCompletion = LookupElementBuilder.create("SWEEBLE_ERROR")
                            .withPresentableText("❌ AI completion error")
                            .withTypeText("Error")
                            .withBoldness(true)
                        
                        result.withPrefixMatcher("").addElement(errorCompletion)
                    }
                }
            }
        )
        
        LOG.warn("SweebleCompletionContributor: Extension registered successfully")
    }
} 