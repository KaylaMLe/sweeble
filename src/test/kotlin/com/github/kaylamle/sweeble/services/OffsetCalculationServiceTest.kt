package com.github.kaylamle.sweeble.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.editor.Editor

class OffsetCalculationServiceTest : BasePlatformTestCase() {

    private lateinit var offsetCalculationService: OffsetCalculationService

    override fun setUp() {
        super.setUp()
        offsetCalculationService = OffsetCalculationService()
    }

    fun testReplaceTypo() {
        val javaCode = """
            public class TestClass {
                public String toString() {
                    retrn "Hello World";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        val change = CodeChange(
            type = ChangeType.REPLACE,
            startOffset = 0, // Will be calculated
            endOffset = 0,   // Will be calculated
            newText = "return \"Hello World\";",
            confidence = 1.0,
            oldText = "retrn \"Hello World\";"
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        assertTrue("Should find the typo", result.startOffset > 0)
        assertTrue("Should have valid end offset", result.endOffset > result.startOffset)
        assertEquals("Should have correct new text", "return \"Hello World\";", result.newText)
    }

    fun testInsertAtCursor() {
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
        
        val change = CodeChange(
            type = ChangeType.INSERT,
            startOffset = 0, // Will be calculated
            endOffset = 0,   // Will be calculated
            newText = "    System.out.println(\"Hello\");",
            confidence = 1.0,
            oldText = ""
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        assertEquals("Should insert at cursor position", cursorOffset, result.startOffset)
        assertEquals("Should insert at cursor position", cursorOffset, result.endOffset)
    }

    fun testDeleteLine() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    System.out.println("Hello");
                    System.out.println("World");
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        val change = CodeChange(
            type = ChangeType.DELETE,
            startOffset = 0, // Will be calculated
            endOffset = 0,   // Will be calculated
            newText = "",
            confidence = 1.0,
            oldText = "        System.out.println(\"Hello\");"
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        assertTrue("Should find the line to delete", result.startOffset > 0)
        assertTrue("Should have valid end offset", result.endOffset > result.startOffset)
        assertEquals("Should have empty new text", "", result.newText)
    }

    fun testExactTextMatch() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    String message = "Hello World";
                    System.out.println(message);
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        val change = CodeChange(
            type = ChangeType.REPLACE,
            startOffset = 0,
            endOffset = 0,
            newText = "String message = \"Hello Universe\";",
            confidence = 1.0,
            oldText = "String message = \"Hello World\";"
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        assertTrue("Should find exact text match", result.startOffset > 0)
        assertTrue("Should have valid end offset", result.endOffset > result.startOffset)
        assertEquals("Should have correct new text", "String message = \"Hello Universe\";", result.newText)
    }

    fun testFuzzyMatchForTypo() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    String mesage = "Hello World";
                    System.out.println(mesage);
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        // Search for the typo to replace it with correct spelling
        val change = CodeChange(
            type = ChangeType.REPLACE,
            startOffset = 0,
            endOffset = 0,
            newText = "String message = \"Hello World\";",
            confidence = 1.0,
            oldText = "String mesage = \"Hello World\";" // The typo that exists in document
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        assertTrue("Should find the typo text", result.startOffset > 0)
        assertTrue("Should have valid end offset", result.endOffset > result.startOffset)
    }

    fun testMultipleOccurrences() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    String message = "Hello";
                    String message = "World";
                    System.out.println(message);
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        val change = CodeChange(
            type = ChangeType.REPLACE,
            startOffset = 0,
            endOffset = 0,
            newText = "String message = \"Hello Universe\";",
            confidence = 1.0,
            oldText = "String message = \"Hello\";"
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        assertTrue("Should find first occurrence", result.startOffset > 0)
        assertTrue("Should have valid end offset", result.endOffset > result.startOffset)
    }

    fun testNotFoundText() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    String message = "Hello World";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        val change = CodeChange(
            type = ChangeType.REPLACE,
            startOffset = 0,
            endOffset = 0,
            newText = "String message = \"Hello Universe\";",
            confidence = 1.0,
            oldText = "String message = \"This text does not exist\";"
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        // Should return unchanged change when text not found
        assertEquals("Should return unchanged change", 0, result.startOffset)
        assertEquals("Should return unchanged change", 0, result.endOffset)
    }

    fun testInsertWithCursorAtEnd() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    String message = "Hello World";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        // Position cursor at the end of the file
        val endOffset = editor.document.textLength
        editor.caretModel.moveToOffset(endOffset)
        
        val change = CodeChange(
            type = ChangeType.INSERT,
            startOffset = 0,
            endOffset = 0,
            newText = "\n    System.out.println(message);",
            confidence = 1.0,
            oldText = ""
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        assertEquals("Should insert at end of file", endOffset, result.startOffset)
        assertEquals("Should insert at end of file", endOffset, result.endOffset)
    }

    fun testDeleteWithEmptyOldText() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    String message = "Hello World";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        val change = CodeChange(
            type = ChangeType.DELETE,
            startOffset = 0,
            endOffset = 0,
            newText = "",
            confidence = 1.0,
            oldText = "" // Empty oldText
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        // Should return unchanged change when oldText is empty
        assertEquals("Should return unchanged change", 0, result.startOffset)
        assertEquals("Should return unchanged change", 0, result.endOffset)
    }

    fun testReplaceWithNewlines() {
        val javaCode = """
            public class TestClass {
                public void test() {
                    String message = "Hello World";
                    System.out.println(message);
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        val change = CodeChange(
            type = ChangeType.REPLACE,
            startOffset = 0,
            endOffset = 0,
            newText = "String message = \"Hello Universe\";\n    System.out.println(\"Updated\");",
            confidence = 1.0,
            oldText = "String message = \"Hello World\";"
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        assertTrue("Should find text with newlines", result.startOffset > 0)
        assertTrue("Should have valid end offset", result.endOffset > result.startOffset)
        assertTrue("Should include newlines in new text", result.newText.contains("\n"))
    }

    fun testCompleteLineReplacement() {
        val javaCode = """
            public class TestClass {
                public HelloWorld(int foo, String bar) {
                    this.foo = foo;
                    this.bar = bar;
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        // AI should provide the complete corrected line, not just the corrected part
        val change = CodeChange(
            type = ChangeType.REPLACE,
            startOffset = 0,
            endOffset = 0,
            newText = "    public HelloWorld(String foo, String bar) {",
            confidence = 1.0,
            oldText = "    public HelloWorld(int foo, String bar) {"
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        assertTrue("Should find the complete line", result.startOffset > 0)
        assertTrue("Should have valid end offset", result.endOffset > result.startOffset)
        assertEquals("Should have complete corrected line", "    public HelloWorld(String foo, String bar) {", result.newText)
    }

    fun testMultiLineReplacement() {
        val javaCode = """
            public class TestClass {
                public String toString() {
                    retrn "HelloWorld{" +
                            "foo='" + foo + '\'' +
                            ", bar='" + bar + '\'' +
                            '}';
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        // AI should provide the complete corrected multi-line block
        val change = CodeChange(
            type = ChangeType.REPLACE,
            startOffset = 0,
            endOffset = 0,
            newText = "        return \"HelloWorld{\" +\n                \"foo='\" + foo + '\\'' +\n                \", bar='\" + bar + '\\'' +\n                '}';",
            confidence = 1.0,
            oldText = "        retrn \"HelloWorld{\" +\n                \"foo='\" + foo + '\\'' +\n                \", bar='\" + bar + '\\'' +\n                '}';"
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        assertTrue("Should find the complete multi-line block", result.startOffset > 0)
        assertTrue("Should have valid end offset", result.endOffset > result.startOffset)
        assertTrue("Should include multiple lines", result.newText.contains("\n"))
        assertTrue("Should have corrected typo", result.newText.contains("return"))
    }

    fun testCursorMarkerHandling() {
        val javaCode = """
            public class TestClass {
                public HelloWorld {
                    this.foo = foo;
                    this.bar = bar;
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Test.java", javaCode)
        val editor = myFixture.editor
        
        // AI provides text with cursor marker that needs to be handled
        val change = CodeChange(
            type = ChangeType.REPLACE,
            startOffset = 0,
            endOffset = 0,
            newText = "    public HelloWorld(String foo, String bar) {",
            confidence = 1.0,
            oldText = "    public HelloWorld[CURSOR_HERE] {"
        )
        
        val result = offsetCalculationService.calculateOffsets(editor, change)
        
        assertTrue("Should find text with cursor marker removed", result.startOffset > 0)
        assertTrue("Should have valid end offset", result.endOffset > result.startOffset)
        assertEquals("Should have complete corrected line", "    public HelloWorld(String foo, String bar) {", result.newText)
        
        // Verify the found text doesn't include cursor marker
        val foundText = editor.document.text.substring(result.startOffset, result.endOffset)
        assertFalse("Found text should not contain cursor marker", foundText.contains("[CURSOR_HERE]"))
        assertEquals("Should find the correct text", "    public HelloWorld {", foundText)
    }
} 