package com.github.kaylamle.sweeble.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NextEditSuggestion(
    val type: SuggestionType,
    val changes: List<CodeChange>,
    val confidence: Double,
    val preview: String
)

enum class SuggestionType {
    SIMPLE_INSERTION,
    COMPLEX_EDIT,
    MULTIPLE_CHANGES
}

class NextEditSuggestionService {
    companion object {
        private val LOG = Logger.getInstance(NextEditSuggestionService::class.java)
    }

    private val codeAnalysisService = CodeAnalysisService()
    private val openAIService = OpenAIService()

    suspend fun getNextEditSuggestions(editor: Editor, context: String, language: String): List<NextEditSuggestion> {
        return withContext(Dispatchers.IO) {
            try {
                val analysis = codeAnalysisService.analyzeCodeAtCursor(editor)
                LOG.debug("Code analysis for context enhancement only - classification already done by mini model")

                val suggestions = mutableListOf<NextEditSuggestion>()

                // Get cursor offset within read action
                val cursorOffset = ApplicationManager.getApplication().runReadAction<Int> {
                    editor.caretModel.offset
                }

                // Generate complex edit suggestions (mini model already determined this is needed)
                val complexSuggestions = getComplexEditSuggestions(editor, context, language, analysis)
                suggestions.addAll(complexSuggestions)

                // Add rule-based suggestions from code analysis
                val ruleBasedSuggestions = analysis.editSuggestions.map { editSuggestion ->
                    NextEditSuggestion(
                        type = SuggestionType.COMPLEX_EDIT,
                        changes = editSuggestion.changes,
                        confidence = 0.8,
                        preview = generatePreview(editSuggestion.changes, editor)
                    )
                }
                suggestions.addAll(ruleBasedSuggestions)

                suggestions.sortedByDescending { it.confidence }
            } catch (e: Exception) {
                LOG.error("Error generating next edit suggestions", e)
                emptyList()
            }
        }
    }



    private suspend fun getComplexEditSuggestions(
        editor: Editor, 
        context: String, 
        language: String, 
        analysis: CodeAnalysisResult
    ): List<NextEditSuggestion> {
        return try {
            val enhancedContext = buildEnhancedContext(context, analysis)
            val aiSuggestions = openAIService.getComplexEditSuggestions(enhancedContext, language)
            
            aiSuggestions.map { suggestion ->
                NextEditSuggestion(
                    type = SuggestionType.COMPLEX_EDIT,
                    changes = suggestion.changes,
                    confidence = suggestion.confidence,
                    preview = generatePreview(suggestion.changes, editor)
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // This is normal behavior when the user types quickly and cancels the previous request
            LOG.debug("Complex edit suggestions were cancelled (normal behavior)")
            emptyList()
        } catch (e: Exception) {
            LOG.warn("Error getting complex edit suggestions", e)
            emptyList()
        }
    }

    private fun buildEnhancedContext(context: String, analysis: CodeAnalysisResult): String {
        val issues = if (analysis.issues.isNotEmpty()) {
            "\nIssues detected:\n" + analysis.issues.joinToString("\n") { "- $it" }
        } else ""
        
        val logicalUnit = analysis.currentLogicalUnit?.let { "\nCurrent logical unit: $it" } ?: ""
        
        return """
            $context
            $logicalUnit
            $issues
            
            Please suggest minimal changes to fix the code. Focus on the smallest possible edits that will resolve the issues.
            If multiple changes are needed, prioritize the most critical ones first.
        """.trimIndent()
    }

    private fun generatePreview(changes: List<CodeChange>, editor: Editor): String {
        return try {
            val document = editor.document
            val text = document.text
            
            // Apply changes to create a preview
            val previewBuilder = StringBuilder(text)
            var offsetAdjustment = 0
            
            for (change in changes.sortedBy { it.startOffset }) {
                val adjustedStart = change.startOffset + offsetAdjustment
                val adjustedEnd = change.endOffset + offsetAdjustment
                
                when (change.type) {
                    ChangeType.INSERT -> {
                        previewBuilder.insert(adjustedStart, change.newText)
                        offsetAdjustment += change.newText.length
                    }
                    ChangeType.REPLACE -> {
                        previewBuilder.replace(adjustedStart, adjustedEnd, change.newText)
                        offsetAdjustment += change.newText.length - (adjustedEnd - adjustedStart)
                    }
                    ChangeType.DELETE -> {
                        previewBuilder.delete(adjustedStart, adjustedEnd)
                        offsetAdjustment -= (adjustedEnd - adjustedStart)
                    }
                }
            }
            
            // Extract a small preview around the changes
            val cursorOffset = ApplicationManager.getApplication().runReadAction<Int> {
                editor.caretModel.offset
            }
            val previewStart = maxOf(0, cursorOffset - 100)
            val previewEnd = minOf(previewBuilder.length, cursorOffset + 100)
            
            previewBuilder.substring(previewStart, previewEnd)
        } catch (e: Exception) {
            LOG.error("Error generating preview", e)
            "Preview unavailable"
        }
    }
} 