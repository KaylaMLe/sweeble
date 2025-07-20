package com.github.kaylamle.sweeble.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

data class CodeAnalysisResult(
    val canCompleteWithInsertion: Boolean,
    val insertionSuggestion: String?,
    val needsComplexEdit: Boolean,
    val editSuggestions: List<EditSuggestion>,
    val currentLogicalUnit: String?,
    val issues: List<String>
)

data class EditSuggestion(
    val changes: List<CodeChange>,
    val priority: Int // Lower number = higher priority
)

data class CodeChange(
    val type: ChangeType,
    val startOffset: Int,
    val endOffset: Int,
    val newText: String,
    val confidence: Double = 1.0
)

enum class ChangeType {
    INSERT,
    REPLACE,
    DELETE
}

class CodeAnalysisService {
    companion object {
        private val LOG = Logger.getInstance(CodeAnalysisService::class.java)
    }

    fun analyzeCodeAtCursor(editor: Editor): CodeAnalysisResult {
        return ApplicationManager.getApplication().runReadAction<CodeAnalysisResult> {
            try {
                val project = editor.project ?: return@runReadAction createEmptyResult()
                val psiFile = PsiManager.getInstance(project).findFile(editor.virtualFile ?: return@runReadAction createEmptyResult())
                    ?: return@runReadAction createEmptyResult()
                
                val offset = editor.caretModel.offset
                val element = psiFile.findElementAt(offset) ?: return@runReadAction createEmptyResult()
                
                // Analyze the current context
                val analysis = analyzeElementContext(element, offset, editor)
                
                // Check if we can complete with simple insertion
                val insertionSuggestion = if (analysis.canCompleteWithInsertion) {
                    generateInsertionSuggestion(element, offset, editor)
                } else null
                
                // Generate complex edit suggestions if needed
                val editSuggestions = if (analysis.needsComplexEdit) {
                    generateEditSuggestions(element, offset, editor, analysis)
                } else emptyList()
                
                CodeAnalysisResult(
                    canCompleteWithInsertion = analysis.canCompleteWithInsertion,
                    insertionSuggestion = insertionSuggestion,
                    needsComplexEdit = analysis.needsComplexEdit,
                    editSuggestions = editSuggestions,
                    currentLogicalUnit = analysis.currentLogicalUnit,
                    issues = analysis.issues
                )
            } catch (e: Exception) {
                LOG.error("Error analyzing code at cursor", e)
                createEmptyResult()
            }
        }
    }
    
    private data class ContextAnalysis(
        val canCompleteWithInsertion: Boolean,
        val needsComplexEdit: Boolean,
        val currentLogicalUnit: String?,
        val issues: List<String>
    )
    
    private fun analyzeElementContext(element: PsiElement, offset: Int, editor: Editor): ContextAnalysis {
        val issues = mutableListOf<String>()
        var canCompleteWithInsertion = false
        var needsComplexEdit = false
        var currentLogicalUnit: String? = null
        
        // Get the current line and analyze it
        val currentLine = getCurrentLineText(editor, offset)
        val document = editor.document
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        
        // Check if we're at the end of a line or in a position where we can add content
        val lineOffset = offset - lineStart
        val lineBeforeCursor = currentLine.substring(0, minOf(lineOffset, currentLine.length))
        
        // If we're at the end of a line, or if the line before cursor ends with a complete statement
        if (offset >= lineEnd - 1 || 
            lineBeforeCursor.trim().endsWith(";") || 
            lineBeforeCursor.trim().endsWith("{") ||
            lineBeforeCursor.trim().endsWith("}") ||
            lineBeforeCursor.trim().isEmpty()) {
            canCompleteWithInsertion = true
        }
        
        // Check for syntax errors in the current line
        if (hasSyntaxError(currentLine)) {
            needsComplexEdit = true
            issues.add("Syntax error in current line: $currentLine")
        }
        
        // Check for incomplete expressions
        if (isIncompleteExpression(currentLine)) {
            needsComplexEdit = true
            issues.add("Incomplete expression: $currentLine")
        }
        
        // Try to determine the current logical unit by looking at surrounding context
        val beforeContext = getContextBeforeCursor(editor, offset, 200)
        val afterContext = getContextAfterCursor(editor, offset, 200)
        
        // Simple heuristics to identify logical units
        when {
            beforeContext.contains("void") || beforeContext.contains("int") || beforeContext.contains("String") || 
            beforeContext.contains("boolean") || beforeContext.contains("double") || beforeContext.contains("float") ||
            beforeContext.contains("long") || beforeContext.contains("short") || beforeContext.contains("byte") ||
            beforeContext.contains("char") || beforeContext.contains("Object") || beforeContext.contains("List") ||
            beforeContext.contains("Map") || beforeContext.contains("Set") -> {
                currentLogicalUnit = "method"
            }
            beforeContext.contains("class") -> {
                currentLogicalUnit = "class"
            }
            beforeContext.contains("if") || beforeContext.contains("for") || beforeContext.contains("while") -> {
                currentLogicalUnit = "control_structure"
            }
        }
        
        return ContextAnalysis(
            canCompleteWithInsertion = canCompleteWithInsertion,
            needsComplexEdit = needsComplexEdit,
            currentLogicalUnit = currentLogicalUnit,
            issues = issues
        )
    }
    
    private fun isIncompleteExpression(text: String): Boolean {
        // Simple heuristics for incomplete expressions
        return text.count { it == '(' } != text.count { it == ')' } ||
               text.count { it == '{' } != text.count { it == '}' } ||
               text.count { it == '[' } != text.count { it == ']' } ||
               text.endsWith(".") ||
               text.endsWith("+") ||
               text.endsWith("-") ||
               text.endsWith("*") ||
               text.endsWith("/") ||
               text.endsWith("=") ||
               text.endsWith(",")
    }
    
    private fun hasSyntaxError(line: String): Boolean {
        // Simple syntax error detection
        return line.contains(";;") || // Double semicolon
               line.contains("()") && !line.contains("new") && !line.contains("(") || // Empty parentheses in wrong context
               line.count { it == '"' } % 2 != 0 || // Unmatched quotes
               line.count { it == '\'' } % 2 != 0 // Unmatched single quotes
    }
    
    private fun getCurrentLineText(editor: Editor, offset: Int): String {
        val document = editor.document
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        return document.text.substring(lineStart, lineEnd)
    }
    
    private fun getContextBeforeCursor(editor: Editor, offset: Int, chars: Int): String {
        val document = editor.document
        val start = maxOf(0, offset - chars)
        return document.text.substring(start, offset)
    }
    
    private fun getContextAfterCursor(editor: Editor, offset: Int, chars: Int): String {
        val document = editor.document
        val end = minOf(document.textLength, offset + chars)
        return document.text.substring(offset, end)
    }
    
    private fun generateInsertionSuggestion(element: PsiElement, offset: Int, editor: Editor): String? {
        // This would be handled by the existing OpenAI service
        // For now, return null to let the existing system handle it
        return null
    }
    
    private fun generateEditSuggestions(element: PsiElement, offset: Int, editor: Editor, analysis: ContextAnalysis): List<EditSuggestion> {
        val suggestions = mutableListOf<EditSuggestion>()
        
        // Check for common issues and suggest fixes
        val currentLine = getCurrentLineText(editor, offset)
        
        // Fix missing semicolon
        if (currentLine.trim().isNotEmpty() && !currentLine.trim().endsWith(";") && !currentLine.trim().endsWith("{") && !currentLine.trim().endsWith("}")) {
            suggestions.add(EditSuggestion(
                changes = listOf(CodeChange(
                    type = ChangeType.INSERT,
                    startOffset = offset,
                    endOffset = offset,
                    newText = ";"
                )),
                priority = 1
            ))
        }
        
        // Fix incomplete parentheses
        val beforeContext = getContextBeforeCursor(editor, offset, 50)
        if (beforeContext.count { it == '(' } > beforeContext.count { it == ')' }) {
            suggestions.add(EditSuggestion(
                changes = listOf(CodeChange(
                    type = ChangeType.INSERT,
                    startOffset = offset,
                    endOffset = offset,
                    newText = ")"
                )),
                priority = 2
            ))
        }
        
        // Fix incomplete braces
        if (beforeContext.count { it == '{' } > beforeContext.count { it == '}' }) {
            suggestions.add(EditSuggestion(
                changes = listOf(CodeChange(
                    type = ChangeType.INSERT,
                    startOffset = offset,
                    endOffset = offset,
                    newText = "}"
                )),
                priority = 3
            ))
        }
        
        // Fix incomplete brackets
        if (beforeContext.count { it == '[' } > beforeContext.count { it == ']' }) {
            suggestions.add(EditSuggestion(
                changes = listOf(CodeChange(
                    type = ChangeType.INSERT,
                    startOffset = offset,
                    endOffset = offset,
                    newText = "]"
                )),
                priority = 4
            ))
        }
        
        return suggestions.sortedBy { it.priority }
    }
    
    private fun createEmptyResult(): CodeAnalysisResult {
        return CodeAnalysisResult(
            canCompleteWithInsertion = false,
            insertionSuggestion = null,
            needsComplexEdit = false,
            editSuggestions = emptyList(),
            currentLogicalUnit = null,
            issues = emptyList()
        )
    }
} 