package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ActionType
import com.example.validator.ActionValidator
import com.example.validator.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun testAppNameString() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AI Agent", appName)
    }

    @Test
    fun testActionValidatorValidJson() {
        val json = """
        {
          "task": "Open YouTube and search",
          "summary": "Launch app and query video",
          "actions": [
            {
              "type": "OPEN_APP",
              "packageName": "com.google.android.youtube",
              "appName": "YouTube"
            },
            {
              "type": "WAIT",
              "milliseconds": 1500
            },
            {
              "type": "CLICK",
              "target": "Search"
            },
            {
              "type": "TYPE_TEXT",
              "text": "Android development"
            }
          ]
        }
        """.trimIndent()

        val result = ActionValidator.parseAndValidate(json)
        assertTrue("Expected validation success", result is ValidationResult.Success)

        val success = result as ValidationResult.Success
        assertEquals(4, success.sanitizedActions.size)
        assertEquals(ActionType.OPEN_APP, success.sanitizedActions[0].type)
        assertEquals("com.google.android.youtube", success.sanitizedActions[0].packageName)
        assertEquals(ActionType.WAIT, success.sanitizedActions[1].type)
        assertEquals(1500L, success.sanitizedActions[1].milliseconds)
        assertEquals(ActionType.CLICK, success.sanitizedActions[2].type)
        assertEquals("Search", success.sanitizedActions[2].target)
        assertEquals(ActionType.TYPE_TEXT, success.sanitizedActions[3].type)
        assertEquals("Android development", success.sanitizedActions[3].text)
    }

    @Test
    fun testActionValidatorRejectsDangerousKeywords() {
        val json = """
        {
          "task": "Harmful Task",
          "actions": [
            {
              "type": "TYPE_TEXT",
              "text": "rm -rf /sdcard/photos"
            }
          ]
        }
        """.trimIndent()

        val result = ActionValidator.parseAndValidate(json)
        assertTrue("Expected validation failure on dangerous keywords", result is ValidationResult.Error)
    }

    @Test
    fun testActionValidatorIdentifiesSensitiveActions() {
        val json = """
        {
          "task": "Call Contact",
          "actions": [
            {
              "type": "CALL_CONTACT",
              "name": "Mom",
              "phoneNumber": "1234567890"
            }
          ]
        }
        """.trimIndent()

        val result = ActionValidator.parseAndValidate(json)
        assertTrue("Expected validation success", result is ValidationResult.Success)

        val success = result as ValidationResult.Success
        assertTrue("Expected sensitive action to require confirmation", success.requiresConfirmation)
        assertNotNull(success.confirmationPrompt)
    }
}
