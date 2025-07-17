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
    fun testOpenAIServiceInstance() {
        // Test that we can get an instance of the service
        val service = project.getService(OpenAIService::class.java)
        assertEquals(OpenAIService::class.java, service::class.java)
    }
} 