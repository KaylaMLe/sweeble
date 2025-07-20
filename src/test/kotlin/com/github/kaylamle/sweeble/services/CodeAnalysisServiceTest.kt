package com.github.kaylamle.sweeble.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

class CodeAnalysisServiceTest : BasePlatformTestCase() {

    private lateinit var codeAnalysisService: CodeAnalysisService

    override fun setUp() {
        super.setUp()
        codeAnalysisService = CodeAnalysisService()
    }

    fun testSimpleInsertionCase() {
        // Test case where a simple insertion can complete the logical unit
        val javaCode = """
            public class TestClass {
                public void testMethod() {
                    String message = "Hello"
                    [CURSOR_HERE]
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        // Position cursor at the end of the statement
        editor.caretModel.moveToOffset(javaCode.indexOf("[CURSOR_HERE]"))
        
        val result = codeAnalysisService.analyzeCodeAtCursor(editor)
        
        assertTrue("Should be able to complete with insertion", result.canCompleteWithInsertion)
        assertFalse("Should not need complex edit", result.needsComplexEdit)
        assertEquals("Should be in a method", "method", result.currentLogicalUnit)
    }

    fun testComplexEditCase() {
        // Test case where a complex edit is needed
        val javaCode = """
            public class TestClass {
                public void testMethod(String message {
                    System.out.println(message)
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        // Position cursor after the incomplete parameter list
        editor.caretModel.moveToOffset(javaCode.indexOf("message {") + "message ".length)
        
        val result = codeAnalysisService.analyzeCodeAtCursor(editor)
        
        assertFalse("Should not be able to complete with simple insertion", result.canCompleteWithInsertion)
        assertTrue("Should need complex edit", result.needsComplexEdit)
        assertTrue("Should have issues", result.issues.isNotEmpty())
        assertEquals("Should be in a method", "method", result.currentLogicalUnit)
    }

    fun testIncompleteExpression() {
        // Test case with incomplete expression
        val javaCode = """
            public class TestClass {
                public void testMethod() {
                    String result = "Hello" + 
                    [CURSOR_HERE]
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        editor.caretModel.moveToOffset(javaCode.indexOf("[CURSOR_HERE]"))
        
        val result = codeAnalysisService.analyzeCodeAtCursor(editor)
        
        assertFalse("Should not be able to complete with simple insertion", result.canCompleteWithInsertion)
        assertTrue("Should need complex edit", result.needsComplexEdit)
        assertTrue("Should have issues related to incomplete expression", 
                  result.issues.any { it.contains("Incomplete expression") })
    }

    fun testMissingSemicolon() {
        // Test case with missing semicolon
        val javaCode = """
            public class TestClass {
                public void testMethod() {
                    String message = "Hello"
                    [CURSOR_HERE]
                    System.out.println(message)
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        editor.caretModel.moveToOffset(javaCode.indexOf("[CURSOR_HERE]"))
        
        val result = codeAnalysisService.analyzeCodeAtCursor(editor)
        
        assertTrue("Should be able to complete with insertion", result.canCompleteWithInsertion)
        assertFalse("Should not need complex edit", result.needsComplexEdit)
        assertTrue("Should have edit suggestions", result.editSuggestions.isNotEmpty())
    }
} 