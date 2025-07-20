package com.github.kaylamle.sweeble.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.github.kaylamle.sweeble.services.SweebleSettingsState",
    storages = [Storage("sweeble.xml")]
)
class SweebleSettingsState : PersistentStateComponent<SweebleSettingsState> {
    
    var openaiApiKey: String = ""
    
    override fun getState(): SweebleSettingsState = this
    
    override fun loadState(state: SweebleSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }
    
    companion object {
        fun getInstance(): SweebleSettingsState {
            return try {
                ApplicationManager.getApplication().getService(SweebleSettingsState::class.java)
            } catch (e: Exception) {
                // Fallback for test environments or when service is not available
                SweebleSettingsState()
            }
        }
    }
} 