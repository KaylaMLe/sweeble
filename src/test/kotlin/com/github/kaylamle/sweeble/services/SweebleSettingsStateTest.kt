package com.github.kaylamle.sweeble.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.application.ApplicationManager

class SweebleSettingsStateTest : BasePlatformTestCase() {
    
    fun testSettingsState() {
        // Ensure the application is initialized
        assertTrue("Application should be initialized", ApplicationManager.getApplication() != null)
        
        val settings = SweebleSettingsState.getInstance()
        assertNotNull("Settings should not be null", settings)
        
        // Test initial state
        assertTrue("Initial API key should be empty", settings.openaiApiKey.isEmpty())
        
        // Test setting API key
        val testApiKey = "test-api-key-123"
        settings.openaiApiKey = testApiKey
        assertEquals("API key should be set correctly", testApiKey, settings.openaiApiKey)
        
        // Test clearing API key
        settings.openaiApiKey = ""
        assertTrue("API key should be cleared", settings.openaiApiKey.isEmpty())
    }
} 