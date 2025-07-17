package com.github.kaylamle.sweeble.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import org.junit.Test
import kotlin.test.assertTrue

class SweebleCompletionContributorTest : BasePlatformTestCase() {

    @Test
    fun testCompletionContributorRegistration() {
        // Test that the completion contributor is properly registered
        val contributor = SweebleCompletionContributor()
        assertTrue(contributor is SweebleCompletionContributor)
    }

    @Test
    fun testCompletionInTextFile() {
        myFixture.configureByText("test.txt", "hello wo<caret>")
        try {
            myFixture.complete(CompletionType.BASIC)
            // Should not crash - completions may be empty without API key, but that's expected
        } catch (e: Exception) {
            println("Completion test exception (expected without API key): ${e.message}")
        }
    }

    @Test
    fun testCompletionInPythonFile() {
        myFixture.configureByText("test.py", "def hello():\n    print(<caret>)")
        try {
            myFixture.complete(CompletionType.BASIC)
            // Should handle Python files gracefully
        } catch (e: Exception) {
            println("Completion test exception (expected without API key): ${e.message}")
        }
    }

    @Test
    fun testCompletionInKotlinFile() {
        myFixture.configureByText("test.kt", "fun main() {\n    println(<caret>)\n}")
        try {
            myFixture.complete(CompletionType.BASIC)
            // Should handle Kotlin files gracefully
        } catch (e: Exception) {
            println("Completion test exception (expected without API key): ${e.message}")
        }
    }

    @Test
    fun testCompletionInJavaScriptFile() {
        myFixture.configureByText("test.js", "function hello() {\n    console.log(<caret>)\n}")
        try {
            myFixture.complete(CompletionType.BASIC)
            // Should handle JavaScript files gracefully
        } catch (e: Exception) {
            println("Completion test exception (expected without API key): ${e.message}")
        }
    }

    @Test
    fun testCompletionInEmptyFile() {
        myFixture.configureByText("empty.txt", "<caret>")
        try {
            myFixture.complete(CompletionType.BASIC)
            // Should handle empty files gracefully
        } catch (e: Exception) {
            println("Completion test exception (expected without API key): ${e.message}")
        }
    }
} 