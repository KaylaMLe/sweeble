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
            
            // Sort changes by offset in reverse order to avoid offset shifting
            val sortedChanges = changes.sortedByDescending { it.startOffset }
            
            sortedChanges.forEach { change ->
                when (change.type) {
                    ChangeType.REPLACE -> {
                        // Ensure we're replacing whole lines
                        val startLine = document.getLineNumber(change.startOffset)
                        val endLine = document.getLineNumber(change.endOffset)
                        
                        // Get the actual line boundaries
                        val actualStartOffset = document.getLineStartOffset(startLine)
                        val actualEndOffset = document.getLineEndOffset(endLine)
                        
                        // Replace the entire line(s) with the new text
                        editor.document.replaceString(actualStartOffset, actualEndOffset, change.newText)
                    }
                    ChangeType.INSERT -> {
                        // For insertions, insert at the line end to ensure proper placement
                        val insertionLine = document.getLineNumber(change.startOffset)
                        val insertionOffset = document.getLineEndOffset(insertionLine)
                        editor.document.insertString(insertionOffset, change.newText)
                    }
                    ChangeType.DELETE -> {
                        // Ensure we're deleting whole lines
                        val startLine = document.getLineNumber(change.startOffset)
                        val endLine = document.getLineNumber(change.endOffset)
                        
                        // Get the actual line boundaries
                        val actualStartOffset = document.getLineStartOffset(startLine)
                        val actualEndOffset = document.getLineEndOffset(endLine)
                        
                        // Delete the entire line(s)
                        editor.document.deleteString(actualStartOffset, actualEndOffset)
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