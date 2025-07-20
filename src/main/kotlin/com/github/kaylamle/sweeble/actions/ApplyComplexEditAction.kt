package com.github.kaylamle.sweeble.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.application.ApplicationManager
import com.github.kaylamle.sweeble.services.CodeChange
import com.github.kaylamle.sweeble.services.ChangeType
import com.github.kaylamle.sweeble.highlighting.ChangeHighlighter

class ApplyComplexEditAction : AnAction() {
    
    companion object {
        private var currentChanges: List<CodeChange>? = null
        private var currentHighlighter: ChangeHighlighter? = null
        private var currentEditor: Editor? = null
        
        fun setCurrentChanges(changes: List<CodeChange>, highlighter: ChangeHighlighter, editor: Editor) {
            currentChanges = changes
            currentHighlighter = highlighter
            currentEditor = editor
        }
        
        fun clearCurrentChanges() {
            currentChanges = null
            currentHighlighter = null
            currentEditor = null
        }
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val changes = currentChanges ?: return
        val highlighter = currentHighlighter ?: return
        val editor = currentEditor ?: return
        val project = e.project ?: return
        
        // Apply the changes using WriteCommandAction
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            
            // Debug: Log all changes before applying
            com.intellij.openapi.diagnostic.Logger.getInstance(ApplyComplexEditAction::class.java).info("Applying ${changes.size} changes:")
            changes.forEachIndexed { index, change ->
                com.intellij.openapi.diagnostic.Logger.getInstance(ApplyComplexEditAction::class.java).info("Change $index: ${change.type} at ${change.startOffset}-${change.endOffset}: '${change.newText}'")
            }
            
            // Sort changes by line number in ascending order and track line adjustments
            val sortedChanges = changes.sortedBy { document.getLineNumber(it.startOffset) }
            var lineAdjustment = 0
            
            sortedChanges.forEachIndexed { index, change ->
                when (change.type) {
                    ChangeType.REPLACE -> {
                        // Get line numbers for the change
                        val startLine = document.getLineNumber(change.startOffset) + lineAdjustment
                        val endLine = document.getLineNumber(change.endOffset) + lineAdjustment
                        
                        // Get the actual line boundaries
                        val actualStartOffset = document.getLineStartOffset(startLine)
                        val actualEndOffset = document.getLineEndOffset(endLine)
                        
                        // Replace the entire line(s) with the new text
                        editor.document.replaceString(actualStartOffset, actualEndOffset, change.newText)
                        
                        // Update line adjustment for subsequent changes
                        val oldLineCount = endLine - startLine + 1
                        val newLineCount = change.newText.count { it == '\n' } + 1
                        lineAdjustment += newLineCount - oldLineCount
                    }
                    ChangeType.INSERT -> {
                        // Get line number for insertion
                        val insertionLine = document.getLineNumber(change.startOffset) + lineAdjustment
                        
                        // Insert at the beginning of the target line (not end)
                        val insertionOffset = document.getLineStartOffset(insertionLine)
                        editor.document.insertString(insertionOffset, change.newText)
                        
                        // Update line adjustment for subsequent changes
                        val newLineCount = change.newText.count { it == '\n' } + 1
                        lineAdjustment += newLineCount
                    }
                    ChangeType.DELETE -> {
                        // Get line numbers for deletion
                        val startLine = document.getLineNumber(change.startOffset) + lineAdjustment
                        val endLine = document.getLineNumber(change.endOffset) + lineAdjustment
                        
                        // Get the actual line boundaries
                        val actualStartOffset = document.getLineStartOffset(startLine)
                        val actualEndOffset = document.getLineEndOffset(endLine)
                        
                        // Delete the entire line(s)
                        editor.document.deleteString(actualStartOffset, actualEndOffset)
                        
                        // Update line adjustment for subsequent changes
                        val deletedLineCount = endLine - startLine + 1
                        lineAdjustment -= deletedLineCount
                    }
                }
            }
        }
        
        // Cleanup highlighters
        highlighter.cleanup()
        clearCurrentChanges()
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = currentChanges != null
    }
} 