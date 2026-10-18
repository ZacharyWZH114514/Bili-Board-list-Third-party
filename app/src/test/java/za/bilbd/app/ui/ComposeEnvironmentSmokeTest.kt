package za.bilbd.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 冒烟测试：先确认 Robolectric + Compose 这套环境真的能在 JVM 上渲染。
 * 环境不通的话，后面所有界面测试都是空中楼阁。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ComposeEnvironmentSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun composeRendersOnJvm() {
        compose.setContent { Text("hello-robolectric") }
        compose.onNodeWithText("hello-robolectric").assertExists()
    }
}
