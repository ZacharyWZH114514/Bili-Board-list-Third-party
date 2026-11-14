package za.bilbd.app.data

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 崩溃日志的回归测试。
 *
 * 「有时会闪退」这类问题在 release 包里拿不到 logcat，只能靠应用自己把栈记下来。
 * 这个测试钉住三件事：崩溃会被记录、异常类型与消息在、栈帧也在。
 * 如果哪天 recordCrash 被改坏或被 R8 影响，这条测试会先红。
 */
class AppLogCrashTest {

    @Before
    fun setUp() {
        AppLog.resetForTest()
    }

    @Test
    fun `记录崩溃的类型、消息与栈帧`() {
        val boom = IllegalStateException("模拟闪退")

        AppLog.recordCrash(boom)

        val text = AppLog.exportText()
        assertTrue("应包含标签", text.contains("[崩溃]"))
        assertTrue("应包含异常类型", text.contains("IllegalStateException"))
        assertTrue("应包含异常消息", text.contains("模拟闪退"))
        assertTrue("应包含栈帧", text.contains("AppLogCrashTest"))
    }

    @Test
    fun `崩溃记录不会记成多行打爆日志`() {
        AppLog.recordCrash(RuntimeException("x"))

        // 两条：一条摘要、一条栈；栈用 | 拼成单行
        val lines = AppLog.exportText().lines().filter { it.isNotBlank() }
        assertTrue("应为两行(摘要+栈)，实际 ${lines.size}", lines.size == 2)
    }
}
