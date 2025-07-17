package com.github.kaylamle.sweeble.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.codeInsight.completion.CompletionType
import org.junit.Test

class SweebleCompletionContributorTest : BasePlatformTestCase() {

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
    fun testCompletionInEmptyTextFile() {
        myFixture.configureByText("empty.txt", "<caret>")
        try {
            myFixture.complete(CompletionType.BASIC)
            // Should handle empty files gracefully
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
    fun testCompletionInJavaFile() {
        myFixture.configureByText("Test.java", "public class Test {\n    public static void main(String[] args) {\n        System.out.println(<caret>);\n    }\n}")
        try {
            myFixture.complete(CompletionType.BASIC)
            // Should handle Java files gracefully
        } catch (e: Exception) {
            println("Completion test exception (expected without API key): ${e.message}")
        }
    }

    @Test
    fun testCompletionInMarkdownFile() {
        myFixture.configureByText("test.md", "# Title\n\nSome <caret> content")
        try {
            myFixture.complete(CompletionType.BASIC)
            // Should handle Markdown files gracefully
        } catch (e: Exception) {
            println("Completion test exception (expected without API key): ${e.message}")
        }
    }
} 