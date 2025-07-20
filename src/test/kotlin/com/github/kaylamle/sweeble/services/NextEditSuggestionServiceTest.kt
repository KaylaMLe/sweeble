package com.github.kaylamle.sweeble.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.runBlocking

class NextEditSuggestionServiceTest : BasePlatformTestCase() {

    private lateinit var nextEditSuggestionService: NextEditSuggestionService

    override fun setUp() {
        super.setUp()
        nextEditSuggestionService = NextEditSuggestionService()
    }

    fun testIntegrationWithOffsetCalculation() {
        val javaCode = """
            public class TestClass {
                public String toString() {
                    retrn "Hello World";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        // Create a mock context that would be sent to AI
        val context = """
            public class TestClass {
                public String toString() {
                    [CURSOR_HERE]retrn "Hello World";
                }
            }
        """.trimIndent()

        runBlocking {
            val suggestions = nextEditSuggestionService.getNextEditSuggestions(editor, context, "java")
            
            // Verify that suggestions are returned and have calculated offsets
            assertTrue("Should return suggestions", suggestions.isNotEmpty())
            
            for (suggestion in suggestions) {
                when (suggestion.type) {
                    ChangeType.REPLACE, ChangeType.DELETE -> {
                        // Should have calculated offsets for REPLACE/DELETE
                        assertTrue("Should have calculated start offset", suggestion.startOffset >= 0)
                        assertTrue("Should have calculated end offset", suggestion.endOffset > suggestion.startOffset)
                        assertTrue("Should have oldText for pattern matching", suggestion.oldText.isNotEmpty())
                    }
                    ChangeType.INSERT -> {
                        // INSERT should use cursor position
                        assertTrue("Should have calculated start offset", suggestion.startOffset >= 0)
                        assertEquals("INSERT should have same start and end offset", suggestion.startOffset, suggestion.endOffset)
                    }
                }
                
                // All suggestions should have confidence
                assertTrue("Should have confidence score", suggestion.confidence >= 0.0 && suggestion.confidence <= 1.0)
            }
        }
    }

    fun testFilteringInvalidChanges() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    String message = "Hello World";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        val context = """
            public class TestClass {
                public void test() {
                    [CURSOR_HERE]String message = "Hello World";
                }
            }
        """.trimIndent()

        runBlocking {
            val suggestions = nextEditSuggestionService.getNextEditSuggestions(editor, context, "java")
            
            // Verify that invalid changes (where oldText couldn't be found) are filtered out
            for (suggestion in suggestions) {
                if (suggestion.type == ChangeType.REPLACE || suggestion.type == ChangeType.DELETE) {
                    // If it's a REPLACE/DELETE, it should have valid offsets (not 0,0)
                    assertTrue("Should have valid offsets for REPLACE/DELETE", 
                              suggestion.startOffset != 0 || suggestion.endOffset != 0)
                }
            }
        }
    }

    fun testCursorPositionForInsert() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        // Position cursor in the empty method body
        val cursorOffset = javaCode.indexOf("    }")
        editor.caretModel.moveToOffset(cursorOffset)
        
        val context = """
            public class TestClass {
                public void test() {
                    [CURSOR_HERE]
                }
            }
        """.trimIndent()

        runBlocking {
            val suggestions = nextEditSuggestionService.getNextEditSuggestions(editor, context, "java")
            
            // Look for INSERT suggestions
            val insertSuggestions = suggestions.filter { it.type == ChangeType.INSERT }
            
            for (insertSuggestion in insertSuggestions) {
                assertEquals("INSERT should use cursor position", cursorOffset, insertSuggestion.startOffset)
                assertEquals("INSERT should use cursor position", cursorOffset, insertSuggestion.endOffset)
            }
        }
    }

    fun testMultipleChangesInContext() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    String mesage = "Hello";
                    retrn mesage;
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        val context = """
            public class TestClass {
                public void test() {
                    [CURSOR_HERE]String mesage = "Hello";
                    retrn mesage;
                }
            }
        """.trimIndent()

        runBlocking {
            val suggestions = nextEditSuggestionService.getNextEditSuggestions(editor, context, "java")
            
            // Should be able to handle multiple issues in the same context
            assertTrue("Should handle multiple issues", suggestions.isNotEmpty())
            
            // Verify each suggestion has proper offsets
            for (suggestion in suggestions) {
                assertTrue("Should have valid confidence", suggestion.confidence >= 0.0 && suggestion.confidence <= 1.0)
                
                if (suggestion.type != ChangeType.INSERT) {
                    assertTrue("Should have oldText for pattern matching", suggestion.oldText.isNotEmpty())
                }
            }
        }
    }

    fun testEmptyContext() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        val context = """
            public class TestClass {
                public void test() {
                    [CURSOR_HERE]
                }
            }
        """.trimIndent()

        runBlocking {
            val suggestions = nextEditSuggestionService.getNextEditSuggestions(editor, context, "java")
            
            // Should handle empty context gracefully
            // May return empty list or suggestions for common patterns
            assertNotNull("Should return list (even if empty)", suggestions)
        }
    }
} 