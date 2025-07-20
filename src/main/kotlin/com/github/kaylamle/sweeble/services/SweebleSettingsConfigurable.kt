package com.github.kaylamle.sweeble.services

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class SweebleSettingsConfigurable : Configurable {
    private var panel: JPanel? = null
    private var apiKeyField: JBPasswordField? = null
    
    override fun getDisplayName(): String = "Sweeble AI Assistant"
    
    override fun createComponent(): JComponent {
        apiKeyField = JBPasswordField()
        apiKeyField!!.preferredSize = Dimension(400, 30)
        
        val descriptionLabel = JBLabel("<html>Enter your OpenAI API key to enable AI-powered code completions.<br/>You can get an API key from <a href='https://platform.openai.com/api-keys'>https://platform.openai.com/api-keys</a></html>")
        descriptionLabel.preferredSize = Dimension(400, 50)
        
        panel = FormBuilder.createFormBuilder()
            .addComponent(descriptionLabel)
            .addSeparator()
            .addLabeledComponent(JBLabel("OpenAI API Key: "), apiKeyField!!, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            
        return panel!!
    }
    
    override fun isModified(): Boolean {
        val settings = SweebleSettingsState.getInstance()
        val currentKey = String(apiKeyField!!.password)
        return currentKey != settings.openaiApiKey
    }
    
    override fun apply() {
        val settings = SweebleSettingsState.getInstance()
        settings.openaiApiKey = String(apiKeyField!!.password)
    }
    
    override fun reset() {
        val settings = SweebleSettingsState.getInstance()
        apiKeyField!!.text = settings.openaiApiKey
    }
    
    override fun disposeUIResources() {
        panel = null
        apiKeyField = null
    }
} 