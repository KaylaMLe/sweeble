package com.github.kaylamle.sweeble.completion

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.completion.InsertionContext
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
        LOG.warn("SweebleCompletionContributor: Constructor called - plugin is loading!")
        println("SweebleCompletionContributor: Console output - constructor called!")

        // Debug: List all available languages
        LOG.warn("SweebleCompletionContributor: Available languages:")
        Language.getRegisteredLanguages().forEach { lang ->
            LOG.warn("  - ${lang.id} (${lang.displayName})")
        }

        // Register for Plain Text
        val textLang = Language.findLanguageByID("TEXT")
        if (textLang != null) {
            extend(
                CompletionType.SMART,
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(textLang)),
                providerWithLogging("Plain Text")
            )
            LOG.warn("SweebleCompletionContributor: Registered for Plain Text")
        } else {
            LOG.warn("SweebleCompletionContributor: Plain Text language not found")
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
                CompletionType.SMART,
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(pythonLang)),
                providerWithLogging("Python")
            )
            LOG.warn("SweebleCompletionContributor: Registered for Python (ID: ${pythonLang.id})")
        } else {
            LOG.warn("SweebleCompletionContributor: Python language not found - tried Python, python, PYTHON")
            // Fallback: Register for .py files using file extension pattern
            extend(
                CompletionType.SMART,
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".py"))),
                providerWithLogging("Python (.py files)")
            )
            LOG.warn("SweebleCompletionContributor: Registered for Python using .py file extension pattern")
        }

        // Register for Java
        val javaLang = Language.findLanguageByID("JAVA")
        if (javaLang != null) {
            extend(
                CompletionType.SMART,
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(javaLang)),
                providerWithLogging("Java")
            )
            LOG.warn("SweebleCompletionContributor: Registered for Java")
        } else {
            LOG.warn("SweebleCompletionContributor: Java language not found")
        }

        // Register for Kotlin
        val kotlinLang = Language.findLanguageByID("kotlin")
        if (kotlinLang != null) {
            extend(
                CompletionType.SMART,
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(kotlinLang)),
                providerWithLogging("Kotlin")
            )
            LOG.warn("SweebleCompletionContributor: Registered for Kotlin")
        } else {
            LOG.warn("SweebleCompletionContributor: Kotlin language not found")
        }

        // Register for Markdown
        val markdownLang = Language.findLanguageByID("Markdown")
        if (markdownLang != null) {
            extend(
                CompletionType.SMART,
                PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(markdownLang)),
                providerWithLogging("Markdown")
            )
            LOG.warn("SweebleCompletionContributor: Registered for Markdown")
        } else {
            LOG.warn("SweebleCompletionContributor: Markdown language not found")
        }

        LOG.warn("SweebleCompletionContributor: Extension registered successfully")
        
        // Additional registrations for file extensions to ensure coverage
        // Register for .java files
        extend(
            CompletionType.SMART,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".java"))),
            providerWithLogging("Java (.java files)")
        )
        LOG.warn("SweebleCompletionContributor: Registered for Java using .java file extension pattern")
        
        // Register for .py files (additional registration)
        extend(
            CompletionType.SMART,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".py"))),
            providerWithLogging("Python (.py files)")
        )
        LOG.warn("SweebleCompletionContributor: Registered for Python using .py file extension pattern (additional)")
        
        // Register for .kt files
        extend(
            CompletionType.SMART,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".kt"))),
            providerWithLogging("Kotlin (.kt files)")
        )
        LOG.warn("SweebleCompletionContributor: Registered for Kotlin using .kt file extension pattern")
        
        // Register for .md files
        extend(
            CompletionType.SMART,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".md"))),
            providerWithLogging("Markdown (.md files)")
        )
        LOG.warn("SweebleCompletionContributor: Registered for Markdown using .md file extension pattern")
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

                LOG.warn("SweebleCompletionContributor: addCompletions called for file: ${file.name} (${file.language.displayName}) [Registered for: $language]")
                        
                // Check if file is gitignored
                if (GitService.isGitIgnored(project, file)) {
                    LOG.info("SweebleCompletionContributor: File is gitignored, skipping completions")
                    return
                }
                        
                // Always use a blank prefix matcher so completions show up regardless of prefix
                val allResults = result.withPrefixMatcher("")
                        
                // Get AI completions
                LOG.warn("SweebleCompletionContributor: Getting AI completions...")
                val openAIService = OpenAIService.getInstance(project)
                val aiCompletions = openAIService.getCompletions(project, file, editor, offset)
                        
                LOG.warn("SweebleCompletionContributor: Got ${aiCompletions.size} AI completions")
                        
                if (aiCompletions.isNotEmpty()) {
                    LOG.info("SweebleCompletionContributor: Got ${aiCompletions.size} AI completions")
                    
                    // Add AI completions with high priority to appear at the top
                    aiCompletions.forEach { completion ->
                        val lookupElement = LookupElementBuilder.create(completion.text)
                            .withPresentableText("🤖 ${completion.text}")
                            .withTypeText("AI Suggestion")
                            .withBoldness(true)
                        allResults.addElement(lookupElement)
                        LOG.warn("SweebleCompletionContributor: Added completion: ${completion.text}")
                    }
                } else {
                    LOG.info("SweebleCompletionContributor: No AI completions, adding fallback")
                    
                    // Add fallback completion if no AI suggestions
                    val fallbackCompletion = LookupElementBuilder.create("SWEEBLE_NO_AI")
                        .withPresentableText("🤖 No AI suggestions available")
                        .withTypeText("AI Suggestion")
                        .withBoldness(true)
                    allResults.addElement(fallbackCompletion)
                    LOG.warn("SweebleCompletionContributor: Added fallback completion")
                }
                        
                LOG.warn("SweebleCompletionContributor: Successfully added completions to result set")
                        
                // Force the result set to refresh and stop other contributors from adding completions after ours
                allResults.stopHere()
                        
            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                throw e // Rethrow as required by JetBrains guidelines
            } catch (e: Exception) {
                LOG.error("SweebleCompletionContributor: Error adding completions", e)
                // Add error completion
                val errorCompletion = LookupElementBuilder.create("SWEEBLE_ERROR")
                    .withPresentableText("🤖 AI completion error")
                    .withTypeText("Error")
                    .withBoldness(true)
                result.withPrefixMatcher("").addElement(errorCompletion)
            }
        }
    }
} 