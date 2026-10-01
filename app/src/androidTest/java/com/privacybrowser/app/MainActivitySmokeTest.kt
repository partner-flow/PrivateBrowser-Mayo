package com.privacybrowser.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal smoke test: the app launches and shows the home/address bar placeholder text.
 * Extend this with tab-creation, history and bookmark flows as the app grows — kept small here
 * to stay fast and reliable in CI rather than exhaustive.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsSearchPlaceholder() {
        composeRule.onNodeWithText("Search or enter address").assertExists()
    }
}
