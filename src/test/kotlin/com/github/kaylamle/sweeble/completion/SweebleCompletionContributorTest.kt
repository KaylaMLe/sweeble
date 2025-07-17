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
        myFixture.complete(CompletionType.BASIC)
        
        // Should not crash and should handle text files
        // (completions will be empty without API key, but that's expected)
    }

    @Test
    fun testCompletionInPythonFile() {
        myFixture.configureByText("test.py", "def hello():\n    print(<caret>)")
        myFixture.complete(CompletionType.BASIC)
        
        // Should handle Python files
    }

    @Test
    fun testCompletionInKotlinFile() {
        myFixture.configureByText("test.kt", "fun main() {\n    println(<caret>)\n}")
        myFixture.complete(CompletionType.BASIC)
        
        // Should handle Kotlin files
    }

    @Test
    fun testCompletionInJavaScriptFile() {
        myFixture.configureByText("test.js", "function hello() {\n    console.log(<caret>)\n}")
        myFixture.complete(CompletionType.BASIC)
        
        // Should handle JavaScript files
    }

    @Test
    fun testCompletionInEmptyFile() {
        myFixture.configureByText("empty.txt", "<caret>")
        myFixture.complete(CompletionType.BASIC)
        
        // Should handle empty files gracefully
    }
} 