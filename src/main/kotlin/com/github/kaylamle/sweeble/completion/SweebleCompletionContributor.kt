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
import com.intellij.lang.Language

class SweebleCompletionContributor : CompletionContributor() {
    companion object {
        private val LOG = Logger.getInstance(SweebleCompletionContributor::class.java)
    }
    
    init {
        LOG.info("SweebleCompletionContributor: Initializing...")

        // Register for Plain Text
        val textLang = Language.findLanguageByID("TEXT")
        if (textLang != null) {
            extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(textLang)),
                providerWithLogging("Plain Text")
            )
        }

        // Register for Python - try multiple variations
        var pythonLang = Language.findLanguageByID("Python")
        if (pythonLang == null) {
            pythonLang = Language.findLanguageByID("python")
        }
        if (pythonLang == null) {
            pythonLang = Language.findLanguageByID("PYTHON")
        }
        if (pythonLang == null) {
            // Try to find by display name
            pythonLang = Language.getRegisteredLanguages().find { it.displayName.equals("Python", ignoreCase = true) }
        }
        
        if (pythonLang != null) {
            extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(pythonLang)),
                providerWithLogging("Python")
            )
        } else {
            // Fallback: Register for .py files using file extension pattern
            extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".py"))),
                providerWithLogging("Python (.py files)")
            )
        }

        // Register for Java
        val javaLang = Language.findLanguageByID("JAVA")
        if (javaLang != null) {
            extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(javaLang)),
                providerWithLogging("Java")
            )
        }

        // Register for Kotlin
        val kotlinLang = Language.findLanguageByID("kotlin")
        if (kotlinLang != null) {
            extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(kotlinLang)),
                providerWithLogging("Kotlin")
            )
        }

        // Register for Markdown
        val markdownLang = Language.findLanguageByID("Markdown")
        if (markdownLang != null) {
            extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(markdownLang)),
                providerWithLogging("Markdown")
            )
        }

        LOG.info("SweebleCompletionContributor: Registration complete")
        
        // Additional registrations for file extensions to ensure coverage
        // Register for .java files
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".java"))),
            providerWithLogging("Java (.java files)")
        )
        
        // Register for .py files (additional registration)
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".py"))),
            providerWithLogging("Python (.py files)")
        )
        
        // Register for .kt files
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".kt"))),
            providerWithLogging("Kotlin (.kt files)")
        )
        
        // Register for .md files
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".md"))),
            providerWithLogging("Markdown (.md files)")
        )
        
        // Register for inline completions (like GitHub Copilot)
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            inlineCompletionProvider()
        )
    }

    private fun providerWithLogging(language: String): CompletionProvider<CompletionParameters> = object : CompletionProvider<CompletionParameters>() {
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

                LOG.debug("SweebleCompletionContributor: Processing completions for ${file.name} (${file.language.displayName})")
                        
                // Check if file is gitignored
                if (GitService.isGitIgnored(project, file)) {
                    LOG.debug("SweebleCompletionContributor: File is gitignored, skipping completions")
                    return
                }
                        
                // Always use a blank prefix matcher so completions show up regardless of prefix
                val allResults = result.withPrefixMatcher("")
                        
                // Get AI completions
                val openAIService = OpenAIService.getInstance(project)
                val aiCompletions = openAIService.getCompletions(project, file, editor, offset)
                        
                if (aiCompletions.isNotEmpty()) {
                    LOG.debug("SweebleCompletionContributor: Got ${aiCompletions.size} AI completions")
                    
                    // Add AI completions with high priority to appear at the top
                    aiCompletions.forEach { completion ->
                        val lookupElement = LookupElementBuilder.create(completion.text)
                            .withPresentableText(completion.text)
                            .withTypeText("AI Suggestion")
                            .withBoldness(true)
                        
                        allResults.addElement(lookupElement)
                    }
                    
                    // For Kotlin files, ensure our completions appear at the top
                    if (file.language.id == "kotlin") {
                        LOG.debug("SweebleCompletionContributor: Kotlin file detected, ensuring AI completions at top")
                    }
                } else {
                    LOG.debug("SweebleCompletionContributor: No AI completions available")
                    
                    // Add fallback completion if no AI suggestions
                    val fallbackCompletion = LookupElementBuilder.create("SWEEBLE_NO_AI")
                        .withPresentableText("No AI suggestions available")
                        .withTypeText("AI Suggestion")
                        .withBoldness(true)
                    
                    allResults.addElement(fallbackCompletion)
                }
                        
                // Force the result set to refresh and stop other contributors from adding completions after ours
                allResults.stopHere()
                LOG.debug("SweebleCompletionContributor: Called stopHere() to ensure AI completions appear at top")
                        
            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                throw e // Rethrow as required by JetBrains guidelines
            } catch (e: Exception) {
                LOG.error("SweebleCompletionContributor: Error adding completions", e)
                // Add error completion
                val errorCompletion = LookupElementBuilder.create("SWEEBLE_ERROR")
                    .withPresentableText("AI completion error")
                    .withTypeText("Error")
                    .withBoldness(true)
                result.withPrefixMatcher("").addElement(errorCompletion)
            }
        }
    }

    private fun inlineCompletionProvider(): CompletionProvider<CompletionParameters> = object : CompletionProvider<CompletionParameters>() {
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

                // Only show inline completions for supported languages
                if (!isSupportedLanguage(file.language.id)) {
                    return
                }

                // Check if file is gitignored
                if (GitService.isGitIgnored(project, file)) {
                    return
                }

                // Get AI completions
                val openAIService = OpenAIService.getInstance(project)
                val aiCompletions = openAIService.getCompletions(project, file, editor, offset)

                if (aiCompletions.isNotEmpty()) {
                    val suggestion = aiCompletions.first().text
                    
                    // Create inline completion element
                    val inlineElement = LookupElementBuilder.create(suggestion)
                        .withPresentableText(suggestion)
                        .withTypeText("AI Inline")
                        .withBoldness(true)
                    
                    // Add as inline completion
                    result.addElement(inlineElement)
                    LOG.debug("SweebleCompletionContributor: Added inline completion: $suggestion")
                }

            } catch (e: Exception) {
                LOG.error("SweebleCompletionContributor: Error adding inline completion", e)
            }
        }

        private fun isSupportedLanguage(languageId: String): Boolean {
            return languageId in listOf("JAVA", "Python", "kotlin", "TEXT", "Markdown")
        }
    }
} 