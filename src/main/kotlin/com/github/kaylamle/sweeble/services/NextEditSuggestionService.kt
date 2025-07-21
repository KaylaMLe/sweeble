package com.github.kaylamle.sweeble.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Removed NextEditSuggestion and SuggestionType - using flat CodeChange list with confidence

class NextEditSuggestionService {
    companion object {
        private val LOG = Logger.getInstance(NextEditSuggestionService::class.java)
    }

    private val codeAnalysisService = CodeAnalysisService()
    private val openAIService = OpenAIService()
    private val offsetCalculationService = OffsetCalculationService()

    suspend fun getNextEditSuggestions(editor: Editor, context: String, language: String): List<CodeChange> {
        return withContext(Dispatchers.IO) {
            try {
                val analysis = codeAnalysisService.analyzeCodeAtCursor(editor)
                LOG.debug("Code analysis for context enhancement only - classification already done by mini model")

                // Get cursor offset within read action
                val cursorOffset = ApplicationManager.getApplication().runReadAction<Int> {
                    editor.caretModel.offset
                }

                // Generate AI-powered complex edit suggestions only
                getComplexEditSuggestions(editor, context, language, analysis)
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
    ): List<CodeChange> {
        return try {
            val rawChanges = openAIService.getComplexEditSuggestions(context, language)
            
            // Calculate offsets programmatically for each change
            rawChanges.map { change ->
                offsetCalculationService.calculateOffsets(editor, change)
            }.filter { change ->
                // Filter out changes where we couldn't find the target text
                change.startOffset != 0 || change.endOffset != 0 || change.type == ChangeType.INSERT
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


} 