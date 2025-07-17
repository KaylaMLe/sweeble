package com.github.kaylamle.sweeble.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import kotlin.test.assertEquals

class OpenAIServiceTest : BasePlatformTestCase() {

    @Test
    fun testCompletionDataClass() {
        val completion = Completion("test completion")
        assertEquals("test completion", completion.text)
    }

    @Test
    fun testGetCompletionsWithMockedApi() {
        // Arrange: create a subclass and override callOpenAI
        val service = object : OpenAIService() {
            override fun callOpenAI(apiKey: String, context: String): String {
                return """
                    {
                        "choices": [
                            {
                                "message": {
                                    "content": "suggestion1\nsuggestion2"
                                }
                            }
                        ]
                    }
                """.trimIndent()
            }
        }

        val mockFile = myFixture.configureByText("test.txt", "hello world").virtualFile
        val psiFile = myFixture.psiManager.findFile(mockFile)!!
        val editor = myFixture.editor

        // Act
        val completions = service.getCompletions(project, psiFile, editor, 0)

        // Assert
        assertEquals(2, completions.size)
        assertEquals("suggestion1", completions[0].text)
        assertEquals("suggestion2", completions[1].text)
    }
} 