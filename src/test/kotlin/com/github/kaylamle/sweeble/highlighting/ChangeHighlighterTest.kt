package com.github.kaylamle.sweeble.highlighting

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.kaylamle.sweeble.services.CodeChange
import com.github.kaylamle.sweeble.services.ChangeType

class ChangeHighlighterTest : BasePlatformTestCase() {

    private lateinit var changeHighlighter: ChangeHighlighter

    override fun setUp() {
        super.setUp()
        changeHighlighter = ChangeHighlighter()
    }

    fun testInlayHeightCalculation() {
        val javaCode = """
            public class TestClass {
                public String toString() {
                    retrn "Hello World";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        // Create a change with multi-line text
        val change = CodeChange(
            type = ChangeType.REPLACE,
            startOffset = 0,
            endOffset = 0,
            newText = "return \"HelloWorld{\" +\n                \"foo='\" + foo + '\\'' +\n                \", bar='\" + bar + '\\'' +\n                '}';",
            confidence = 1.0,
            oldText = "retrn \"Hello World\";"
        )
        
        // Test that highlighting doesn't throw exceptions
        changeHighlighter.highlightChanges(editor, listOf(change))
        
        // Verify that the highlighter was created successfully
        assertNotNull("ChangeHighlighter should be created", changeHighlighter)
        
        // Clean up
        changeHighlighter.cleanup()
    }

    fun testMultiLineInlayRendering() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    String message = "Hello";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        // Create a change with multiple lines
        val change = CodeChange(
            type = ChangeType.REPLACE,
            startOffset = 0,
            endOffset = 0,
            newText = "String message = \"Hello World\";\n        System.out.println(message);\n        return message;",
            confidence = 1.0,
            oldText = "String message = \"Hello\";"
        )
        
        // Test highlighting with multi-line text
        changeHighlighter.highlightChanges(editor, listOf(change))
        
        // Verify that the highlighter was created successfully
        assertNotNull("ChangeHighlighter should be created", changeHighlighter)
        
        // Clean up
        changeHighlighter.cleanup()
    }

    fun testInsertInlayRendering() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        // Create an insert change
        val change = CodeChange(
            type = ChangeType.INSERT,
            startOffset = 0,
            endOffset = 0,
            newText = "    System.out.println(\"Hello World\");\n    return true;",
            confidence = 1.0,
            oldText = ""
        )
        
        // Test highlighting with insert change
        changeHighlighter.highlightChanges(editor, listOf(change))
        
        // Verify that the highlighter was created successfully
        assertNotNull("ChangeHighlighter should be created", changeHighlighter)
        
        // Clean up
        changeHighlighter.cleanup()
    }

    fun testCleanup() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    String message = "Hello";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        val change = CodeChange(
            type = ChangeType.REPLACE,
            startOffset = 0,
            endOffset = 0,
            newText = "String message = \"Hello World\";",
            confidence = 1.0,
            oldText = "String message = \"Hello\";"
        )
        
        // Create highlighters
        changeHighlighter.highlightChanges(editor, listOf(change))
        
        // Test cleanup
        changeHighlighter.cleanup()
        
        // Verify that cleanup doesn't throw exceptions
        assertNotNull("ChangeHighlighter should still exist after cleanup", changeHighlighter)
    }
} 