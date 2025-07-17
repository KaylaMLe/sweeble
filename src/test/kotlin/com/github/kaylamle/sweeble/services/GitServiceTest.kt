package com.github.kaylamle.sweeble.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import kotlin.test.assertFalse

class GitServiceTest : BasePlatformTestCase() {

    @Test
    fun testIsGitIgnoredWithTextFile() {
        val mockFile = myFixture.configureByText("test.txt", "hello world").virtualFile
        val psiFile = myFixture.psiManager.findFile(mockFile)!!
        
        val isIgnored = GitService.isGitIgnored(project, psiFile)
        
        // Currently returns false (stub implementation)
        assertFalse(isIgnored)
    }

    @Test
    fun testIsGitIgnoredWithPythonFile() {
        val mockFile = myFixture.configureByText("main.py", "print('hello')").virtualFile
        val psiFile = myFixture.psiManager.findFile(mockFile)!!
        
        val isIgnored = GitService.isGitIgnored(project, psiFile)
        
        assertFalse(isIgnored)
    }

    @Test
    fun testIsGitIgnoredWithKotlinFile() {
        val mockFile = myFixture.configureByText("Main.kt", "fun main() { println(\"hello\") }").virtualFile
        val psiFile = myFixture.psiManager.findFile(mockFile)!!
        
        val isIgnored = GitService.isGitIgnored(project, psiFile)
        
        assertFalse(isIgnored)
    }

    @Test
    fun testIsGitIgnoredWithJavaScriptFile() {
        val mockFile = myFixture.configureByText("script.js", "console.log('hello')").virtualFile
        val psiFile = myFixture.psiManager.findFile(mockFile)!!
        
        val isIgnored = GitService.isGitIgnored(project, psiFile)
        
        assertFalse(isIgnored)
    }
} 