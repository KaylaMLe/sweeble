package com.github.kaylamle.sweeble.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.diagnostic.Logger

class MyProjectActivity : ProjectActivity {
    companion object {
        private val LOG = Logger.getInstance(MyProjectActivity::class.java)
    }

    override suspend fun execute(project: Project) {
        LOG.warn("SWEEBLE PLUGIN: MyProjectActivity started for project: ${project.name}")
        LOG.warn("SWEEBLE PLUGIN: This should appear in the IDE logs!")
        println("SWEEBLE PLUGIN: Console output - plugin is running!")
    }
}