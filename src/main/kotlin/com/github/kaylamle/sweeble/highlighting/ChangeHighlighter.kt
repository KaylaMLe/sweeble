package com.github.kaylamle.sweeble.highlighting

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.github.kaylamle.sweeble.services.CodeChange
import com.github.kaylamle.sweeble.services.ChangeType

class ChangeHighlighter {
    
    private val highlighters = mutableListOf<RangeHighlighter>()
    private val inlays = mutableListOf<Inlay<*>>()
    
    fun highlightChanges(editor: Editor, changes: List<CodeChange>) {
        cleanup() // Clear previous highlighters and inlays
        
        val markupModel = editor.markupModel
        val document = editor.document
        
        // Group changes by type for proper diff-style highlighting
        val replaceChanges = changes.filter { it.type == ChangeType.REPLACE }
        val deleteChanges = changes.filter { it.type == ChangeType.DELETE }
        val insertChanges = changes.filter { it.type == ChangeType.INSERT }
        
        // Add red highlighting for REPLACE and DELETE changes
        (replaceChanges + deleteChanges).forEach { change ->
            val startLine = document.getLineNumber(change.startOffset)
            val endLine = document.getLineNumber(change.endOffset)
            
            for (lineNum in startLine..endLine) {
                val lineStart = document.getLineStartOffset(lineNum)
                val lineEnd = document.getLineEndOffset(lineNum)
                
                val lineHighlighter = markupModel.addRangeHighlighter(
                    lineStart,
                    lineEnd,
                    HighlighterLayer.SELECTION - 1,
                    TextAttributes().apply {
                        backgroundColor = java.awt.Color(255, 0, 0, 50) // Plain red with low opacity
                    },
                    HighlighterTargetArea.EXACT_RANGE
                )
                highlighters.add(lineHighlighter)
            }
        }
        
        // Add green inlay hints on EDT
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            val inlayModel = editor.inlayModel
            
            // For REPLACE changes: green inlay directly below the last red-highlighted line
            replaceChanges.forEach { change ->
                val lastReplacedLine = document.getLineNumber(change.endOffset)
                val inlayOffset = document.getLineEndOffset(lastReplacedLine)
                
                val inlay = inlayModel.addBlockElement(
                    inlayOffset,
                    false,
                    true,
                    0,
                    object : com.intellij.openapi.editor.EditorCustomElementRenderer {
                        override fun paint(inlay: Inlay<*>, g: java.awt.Graphics, targetRegion: java.awt.Rectangle, textAttributes: TextAttributes) {
                            val fontMetrics = g.fontMetrics
                            val text = change.newText.trim()
                            
                            // Draw green background
                            g.color = java.awt.Color(0, 255, 0, 50) // Plain green with low opacity
                            g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height)
                            
                            // Draw text
                            g.color = java.awt.Color.WHITE
                            g.drawString(text, targetRegion.x + 2, targetRegion.y + fontMetrics.ascent)
                        }
                        
                        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
                            val text = change.newText.trim()
                            return text.length * 8 + 4 // Approximate width
                        }
                    }
                )
                inlay?.let { inlays.add(it) }
            }
            
            // For INSERT changes: green inlay at insertion point (no red highlighting)
            insertChanges.forEach { change ->
                val insertionLine = document.getLineNumber(change.startOffset)
                val inlayOffset = document.getLineEndOffset(insertionLine)
                
                val inlay = inlayModel.addBlockElement(
                    inlayOffset,
                    false,
                    true,
                    0,
                    object : com.intellij.openapi.editor.EditorCustomElementRenderer {
                        override fun paint(inlay: Inlay<*>, g: java.awt.Graphics, targetRegion: java.awt.Rectangle, textAttributes: TextAttributes) {
                            val fontMetrics = g.fontMetrics
                            val text = change.newText.trim()
                            
                            // Draw green background
                            g.color = java.awt.Color(0, 255, 0, 50) // Plain green with low opacity
                            g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height)
                            
                            // Draw text
                            g.color = java.awt.Color.WHITE
                            g.drawString(text, targetRegion.x + 2, targetRegion.y + fontMetrics.ascent)
                        }
                        
                        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
                            val text = change.newText.trim()
                            return text.length * 8 + 4 // Approximate width
                        }
                    }
                )
                inlay?.let { inlays.add(it) }
            }
            
            // For DELETE changes: no green inlays (only red highlighting)
        }
    }
    
    fun cleanup() {
        // Dispose highlighters (can be done from any thread)
        highlighters.forEach { it.dispose() }
        highlighters.clear()
        
        // Dispose inlays on EDT (required by IntelliJ)
        if (inlays.isNotEmpty()) {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                inlays.forEach { it.dispose() }
                inlays.clear()
            }
        }
    }
} 