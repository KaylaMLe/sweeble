package com.github.kaylamle.sweeble.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project

class CodeChangeApplicationService {
    companion object {
        private val LOG = Logger.getInstance(CodeChangeApplicationService::class.java)
    }

    /**
     * Applies multiple code changes to the editor simultaneously.
     * Changes are applied in order of their startOffset to maintain correct positioning.
     */
    fun applyChanges(editor: Editor, changes: List<CodeChange>) {
        if (changes.isEmpty()) {
            LOG.debug("No changes to apply")
            return
        }

        val project = editor.project
        if (project == null) {
            LOG.warn("No project associated with editor")
            return
        }

        // Sort changes by startOffset to apply them in order
        val sortedChanges = changes.sortedBy { it.startOffset }
        
        LOG.info("Applying ${sortedChanges.size} changes to editor")
        
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val document = editor.document
                var offsetAdjustment = 0
                
                for (change in sortedChanges) {
                    val adjustedStart = change.startOffset + offsetAdjustment
                    val adjustedEnd = change.endOffset + offsetAdjustment
                    
                    LOG.debug("Applying change: ${change.type} at $adjustedStart-$adjustedEnd: '${change.newText}'")
                    
                    when (change.type) {
                        ChangeType.INSERT -> {
                            if (adjustedStart <= document.textLength) {
                                document.insertString(adjustedStart, change.newText)
                                offsetAdjustment += change.newText.length
                            } else {
                                LOG.warn("Insert position $adjustedStart is beyond document length ${document.textLength}")
                            }
                        }
                        ChangeType.REPLACE -> {
                            if (adjustedEnd <= document.textLength) {
                                document.replaceString(adjustedStart, adjustedEnd, change.newText)
                                offsetAdjustment += change.newText.length - (adjustedEnd - adjustedStart)
                            } else {
                                LOG.warn("Replace range $adjustedStart-$adjustedEnd is beyond document length ${document.textLength}")
                            }
                        }
                        ChangeType.DELETE -> {
                            if (adjustedEnd <= document.textLength) {
                                document.deleteString(adjustedStart, adjustedEnd)
                                offsetAdjustment -= (adjustedEnd - adjustedStart)
                            } else {
                                LOG.warn("Delete range $adjustedStart-$adjustedEnd is beyond document length ${document.textLength}")
                            }
                        }
                    }
                }
                
                LOG.info("Successfully applied ${sortedChanges.size} changes")
            } catch (e: Exception) {
                LOG.error("Error applying changes", e)
            }
        }
    }

    /**
     * Creates a preview of what the code would look like after applying the changes.
     */
    fun createPreview(editor: Editor, changes: List<CodeChange>): String {
        return try {
            val document = editor.document
            val text = document.text
            
            if (changes.isEmpty()) {
                return text
            }
            
            // Apply changes to create a preview
            val previewBuilder = StringBuilder(text)
            var offsetAdjustment = 0
            
            for (change in changes.sortedBy { it.startOffset }) {
                val adjustedStart = change.startOffset + offsetAdjustment
                val adjustedEnd = change.endOffset + offsetAdjustment
                
                when (change.type) {
                    ChangeType.INSERT -> {
                        if (adjustedStart <= previewBuilder.length) {
                            previewBuilder.insert(adjustedStart, change.newText)
                            offsetAdjustment += change.newText.length
                        }
                    }
                    ChangeType.REPLACE -> {
                        if (adjustedEnd <= previewBuilder.length) {
                            previewBuilder.replace(adjustedStart, adjustedEnd, change.newText)
                            offsetAdjustment += change.newText.length - (adjustedEnd - adjustedStart)
                        }
                    }
                    ChangeType.DELETE -> {
                        if (adjustedEnd <= previewBuilder.length) {
                            previewBuilder.delete(adjustedStart, adjustedEnd)
                            offsetAdjustment -= (adjustedEnd - adjustedStart)
                        }
                    }
                }
            }
            
            previewBuilder.toString()
        } catch (e: Exception) {
            LOG.error("Error creating preview", e)
            "Preview unavailable"
        }
    }
} 