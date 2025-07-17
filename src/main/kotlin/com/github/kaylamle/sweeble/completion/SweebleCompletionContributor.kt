package com.github.kaylamle.sweeble.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.github.kaylamle.sweeble.services.OpenAIService
import com.github.kaylamle.sweeble.services.GitService

class SweebleCompletionContributor : CompletionContributor() {

    init {
        // Register for all file types that can be edited
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val project = parameters.position.project
                    val file = parameters.originalFile
                    val editor = parameters.editor
                    
                    // Check if file is gitignored
                    if (GitService.isGitIgnored(project, file)) {
                        return
                    }
                    
                    // Get AI completions
                    val openAIService = OpenAIService.getInstance(project)
                    val completions = openAIService.getCompletions(project, file, editor, parameters.offset)
                    
                    completions.forEach { completion ->
                        result.addElement(
                            LookupElementBuilder.create(completion.text)
                                .withPresentableText(completion.text)
                                .withTypeText("AI Suggestion")
                                .withInsertHandler { context, _ ->
                                    val editor = context.editor
                                    val document = editor.document
                                    val caretOffset = editor.caretModel.offset
                                    document.insertString(caretOffset, completion.text)
                                    editor.caretModel.moveToOffset(caretOffset + completion.text.length)
                                }
                        )
                    }
                }
            }
        )
    }
} 