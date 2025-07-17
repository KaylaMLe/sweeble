package com.github.kaylamle.sweeble.inlay

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.openapi.diagnostic.Logger
import com.github.kaylamle.sweeble.services.OpenAIService
import com.github.kaylamle.sweeble.services.GitService
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color

class SweebleInlayHintsProvider : InlayHintsProvider<NoSettings> {
    companion object {
        private val LOG = Logger.getInstance(SweebleInlayHintsProvider::class.java)
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector {
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                try {
                    // Only show hints for supported languages
                    if (!isSupportedLanguage(file.language.id)) {
                        return true
                    }

                    // Check if file is gitignored
                    if (GitService.isGitIgnored(file.project, file)) {
                        return true
                    }

                    // Get the current line text and cursor position
                    val document = editor.document
                    val caretOffset = editor.caretModel.offset
                    val lineNumber = document.getLineNumber(caretOffset)
                    val lineStartOffset = document.getLineStartOffset(lineNumber)
                    val lineEndOffset = document.getLineEndOffset(lineNumber)
                    val currentLineText = document.getText().substring(lineStartOffset, lineEndOffset)
                    val cursorInLine = caretOffset - lineStartOffset

                    // Only show hints if we're at the end of a line or after certain characters
                    if (cursorInLine < currentLineText.length && 
                        !shouldShowHintAfter(currentLineText, cursorInLine)) {
                        return true
                    }

                    // Get AI completions
                    val openAIService = OpenAIService.getInstance(file.project)
                    val aiCompletions = openAIService.getCompletions(file.project, file, editor, caretOffset)

                    if (aiCompletions.isNotEmpty()) {
                        val suggestion = aiCompletions.first().text
                        
                        // Create the inlay hint presentation
                        val presentation = createInlayPresentation(suggestion, editor)
                        
                        // Add the inlay hint at the current cursor position
                        sink.addInlineElement(caretOffset, false, presentation, true)
                        
                        LOG.debug("SweebleInlayHintsProvider: Added inline hint: $suggestion")
                    }

                } catch (e: Exception) {
                    LOG.error("SweebleInlayHintsProvider: Error creating inlay hint", e)
                }

                return true
            }

            private fun isSupportedLanguage(languageId: String): Boolean {
                return languageId in listOf("JAVA", "Python", "kotlin", "TEXT", "Markdown")
            }

            private fun shouldShowHintAfter(lineText: String, cursorPosition: Int): Boolean {
                if (cursorPosition == 0) return false
                
                val charBeforeCursor = lineText[cursorPosition - 1]
                return charBeforeCursor in listOf('.', ' ', '(', ',', ';', ':', '=', '+', '-', '*', '/')
            }

            private fun createInlayPresentation(suggestion: String, editor: Editor): InlayPresentation {
                val factory = PresentationFactory(editor)
                
                // Use a muted gray color for the suggestion
                val mutedColor = Color.GRAY
                
                val attributes = TextAttributes().apply {
                    foregroundColor = mutedColor
                    fontType = 0 // Normal font
                }
                
                return factory.smallText(suggestion)
            }
        }
    }

    override fun createSettings(): NoSettings = NoSettings()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): javax.swing.JComponent {
                return javax.swing.JLabel("AI Inline Suggestions")
            }
        }
    }
    
    override val key: SettingsKey<NoSettings> = SettingsKey("sweeble.inlay.hints")
    
    override val name: String = "AI Inline Suggestions"
    
    override val previewText: String? = "AI suggestions will appear as gray inline text"
} 