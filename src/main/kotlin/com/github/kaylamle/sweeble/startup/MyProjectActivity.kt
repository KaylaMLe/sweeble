package com.github.kaylamle.sweeble.startup

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class MyProjectActivity : StartupActivity {
    companion object {
        private val LOG = Logger.getInstance(MyProjectActivity::class.java)
    }

    override fun runActivity(project: Project) {
        LOG.info("Sweeble: Project activity started for project: ${project.name}")
        LOG.info("Sweeble: Inline completion provider should be registered via plugin.xml")
        LOG.info("Sweeble: Project activity completed")
    }
}