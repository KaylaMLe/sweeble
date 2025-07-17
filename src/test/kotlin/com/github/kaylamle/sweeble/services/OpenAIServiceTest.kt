package com.github.kaylamle.sweeble.services

import org.junit.Test
import kotlin.test.assertEquals

class OpenAIServiceTest {
    @Test
    fun testCompletionDataClass() {
        val completion = Completion("test completion")
        assertEquals("test completion", completion.text)
    }
} 