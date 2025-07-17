package com.github.kaylamle.sweeble.services

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

object GitService {
    fun isGitIgnored(project: Project, file: PsiFile): Boolean {
        // TODO: Implement real gitignore check
        return false
    }
} 