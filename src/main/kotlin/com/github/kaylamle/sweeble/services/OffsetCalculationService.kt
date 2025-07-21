package com.github.kaylamle.sweeble.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.application.ApplicationManager

class OffsetCalculationService {
    companion object {
        private val LOG = Logger.getInstance(OffsetCalculationService::class.java)
    }

    /**
     * Calculate offsets for a CodeChange by finding the oldText in the editor document
     * and determining the appropriate start/end offsets
     */
    fun calculateOffsets(editor: Editor, change: CodeChange): CodeChange {
        return ApplicationManager.getApplication().runReadAction<CodeChange> {
            val document = editor.document
            val documentText = document.text
            
            when (change.type) {
                ChangeType.REPLACE -> {
                    if (change.oldText.isNotEmpty()) {
                        // Find the oldText in the document (with cursor markers removed)
                        val cleanOldText = change.oldText.replace("[CURSOR_HERE]", "")
                        val startOffset = findTextInDocument(documentText, change.oldText)
                        if (startOffset != -1) {
                            val endOffset = startOffset + cleanOldText.length
                            LOG.info("Found REPLACE target at $startOffset-$endOffset: '${cleanOldText}'")
                            change.copy(startOffset = startOffset, endOffset = endOffset)
                        } else {
                            LOG.warn("Could not find oldText '${change.oldText}' in document for REPLACE")
                            change
                        }
                    } else {
                        LOG.warn("REPLACE change has empty oldText")
                        change
                    }
                }
                ChangeType.DELETE -> {
                    if (change.oldText.isNotEmpty()) {
                        // Find the oldText in the document (with cursor markers removed)
                        val cleanOldText = change.oldText.replace("[CURSOR_HERE]", "")
                        val startOffset = findTextInDocument(documentText, change.oldText)
                        if (startOffset != -1) {
                            val endOffset = startOffset + cleanOldText.length
                            LOG.info("Found DELETE target at $startOffset-$endOffset: '${cleanOldText}'")
                            change.copy(startOffset = startOffset, endOffset = endOffset)
                        } else {
                            LOG.warn("Could not find oldText '${change.oldText}' in document for DELETE")
                            change
                        }
                    } else {
                        LOG.warn("DELETE change has empty oldText")
                        change
                    }
                }
                ChangeType.INSERT -> {
                    // For INSERT, we need to determine where to insert
                    // Use the cursor position as the insertion point
                    val cursorOffset = editor.caretModel.offset
                    LOG.info("INSERT at cursor position $cursorOffset")
                    change.copy(startOffset = cursorOffset, endOffset = cursorOffset)
                }
            }
        }
    }

    /**
     * Find text in document with some tolerance for whitespace differences
     */
    private fun findTextInDocument(documentText: String, searchText: String): Int {
        // Remove cursor markers from search text
        val cleanSearchText = searchText.replace("[CURSOR_HERE]", "")
        
        // First try exact match with cleaned text
        val exactMatch = documentText.indexOf(cleanSearchText)
        if (exactMatch != -1) {
            return exactMatch
        }

        // Try with normalized whitespace
        val normalizedSearch = cleanSearchText.trim().replace(Regex("\\s+"), " ")
        val normalizedDocument = documentText.replace(Regex("\\s+"), " ")
        val normalizedMatch = normalizedDocument.indexOf(normalizedSearch)
        
        if (normalizedMatch != -1) {
            // Find the corresponding position in the original document
            return findOriginalPosition(documentText, normalizedDocument, normalizedMatch, normalizedSearch.length)
        }

        // Try fuzzy matching for common patterns
        return findFuzzyMatch(documentText, cleanSearchText)
    }

    /**
     * Find the original position in the document given a normalized position
     */
    private fun findOriginalPosition(original: String, normalized: String, normalizedPos: Int, length: Int): Int {
        // Create a mapping from normalized to original positions
        val originalToNormalized = mutableMapOf<Int, Int>()
        var normalizedIndex = 0
        
        for (i in original.indices) {
            originalToNormalized[i] = normalizedIndex
            if (!original[i].isWhitespace() || (i > 0 && !original[i-1].isWhitespace())) {
                normalizedIndex++
            }
        }
        
        // Find the original position that maps to the normalized position
        for ((originalPos, normPos) in originalToNormalized) {
            if (normPos == normalizedPos) {
                return originalPos
            }
        }
        
        return -1
    }

    /**
     * Fuzzy matching for common patterns like typos
     */
    private fun findFuzzyMatch(documentText: String, searchText: String): Int {
        // Split into lines for line-by-line matching
        val documentLines = documentText.split("\n")
        val searchLines = searchText.split("\n")
        
        for (i in documentLines.indices) {
            val docLine = documentLines[i]
            val searchLine = searchLines.firstOrNull() ?: continue
            
            // Try to find similar lines (for typos)
            if (isSimilarLine(docLine, searchLine)) {
                // Calculate the offset to the start of this line
                var offset = 0
                for (j in 0 until i) {
                    offset += documentLines[j].length + 1 // +1 for newline
                }
                return offset
            }
        }
        
        // If no line-by-line match, try substring matching with tolerance
        return findSubstringWithTolerance(documentText, searchText)
    }

    /**
     * Find substring with tolerance for typos
     */
    private fun findSubstringWithTolerance(documentText: String, searchText: String): Int {
        val searchLength = searchText.length
        val maxTolerance = 2 // Allow up to 2 character differences
        
        for (i in 0..documentText.length - searchLength) {
            val substring = documentText.substring(i, i + searchLength)
            if (calculateSimilarity(substring, searchText) >= searchLength - maxTolerance) {
                return i
            }
        }
        
        return -1
    }

    /**
     * Calculate similarity between two strings
     */
    private fun calculateSimilarity(str1: String, str2: String): Int {
        if (str1.length != str2.length) return 0
        
        var matches = 0
        for (i in str1.indices) {
            if (str1[i] == str2[i]) {
                matches++
            }
        }
        return matches
    }

    /**
     * Check if two lines are similar (for typo detection)
     */
    private fun isSimilarLine(line1: String, line2: String): Boolean {
        val normalized1 = line1.trim()
        val normalized2 = line2.trim()
        
        // Exact match after trimming
        if (normalized1 == normalized2) return true
        
        // Check for common typos (e.g., "retrn" vs "return")
        if (normalized1.length == normalized2.length) {
            var differences = 0
            for (i in normalized1.indices) {
                if (normalized1[i] != normalized2[i]) {
                    differences++
                    if (differences > 2) return false // Too many differences
                }
            }
            return differences <= 2 // Allow up to 2 character differences
        }
        
        return false
    }
} 