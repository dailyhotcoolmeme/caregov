package com.ourmine.caregov.demo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun patientHomeSurvivesActivityRecreationWithoutDemonstrationControls() {
        compose.onNodeWithContentDescription("시연 계정 변경").assertDoesNotExist()
        compose.onNodeWithText("시연 계정").assertDoesNotExist()
        compose.onNodeWithText("김영희님 본인 동행").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("김영희님 본인 동행").assertIsDisplayed()
    }
}
