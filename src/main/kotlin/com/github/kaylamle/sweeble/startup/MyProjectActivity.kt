package com.github.kaylamle.sweeble.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.diagnostic.Logger

class MyProjectActivity : StartupActivity {
    companion object {
        private val LOG = Logger.getInstance(MyProjectActivity::class.java)
    }

    override fun runActivity(project: Project) {
        LOG.info("Sweeble: Inline completion plugin initialized for project ${project.name}")
    }
}