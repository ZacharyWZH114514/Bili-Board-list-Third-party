package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 服务商预设。
 *
 * 重点是 [AiProviders.of] 的反查：它决定打开设置页时哪个服务商被高亮。
 * 反查错了会误导用户以为自己在用 A，实际请求发去 B。
 */
class AiProviderTest {

    @Test
    fun everyPresetHasDistinctIdAndLabel() {
        assertEquals(AiProviders.ALL.size, AiProviders.ALL.map { it.id }.distinct().size)
        assertEquals(AiProviders.ALL.size, AiProviders.ALL.map { it.label }.distinct().size)
    }

    @Test
    fun onlyCustomIsCustom() {
        AiProviders.ALL.forEach { p ->
            assertEquals("只有自定义那项 isCustom 应为真", p.id == AiProvider.ID_CUSTOM, p.isCustom)
        }
    }

    @Test
    fun presetsOtherThanCustomHaveUrlAndModels() {
        AiProviders.ALL.filter { !it.isCustom }.forEach { p ->
            assertTrue("${p.label} 应有 Base URL", p.baseUrl.isNotBlank())
            assertTrue("${p.label} 应至少给一个模型", p.models.isNotEmpty())
            assertTrue("${p.label} 应有说明文案", p.hint.isNotBlank())
        }
    }

    @Test
    fun customHasNothingPrefilled() {
        val custom = AiProviders.byId(AiProvider.ID_CUSTOM)
        assertTrue("自定义不该预填地址", custom.baseUrl.isEmpty())
        assertTrue("自定义不该预设模型", custom.models.isEmpty())
        assertTrue("自定义没有申请页", custom.keyUrl.isEmpty())
    }

    // ---------- 反查 ----------

    @Test
    fun of_findsEachPresetByItsUrl() {
        AiProviders.ALL.filter { it.baseUrl.isNotBlank() }.forEach { p ->
            assertEquals(p.id, AiProviders.of(p.baseUrl).id)
        }
    }

    @Test
    fun of_toleratesTrailingSlashAndWhitespace() {
        assertEquals("deepseek", AiProviders.of("  https://api.deepseek.com/  ").id)
        assertEquals("qwen", AiProviders.of("https://dashscope.aliyuncs.com/compatible-mode/v1/").id)
    }

    @Test
    fun of_unknownUrlFallsBackToCustom() {
        // 用户之前手填过地址时，不该被硬塞进某个服务商
        assertEquals(AiProvider.ID_CUSTOM, AiProviders.of("http://127.0.0.1:11434/v1").id)
        assertEquals(AiProvider.ID_CUSTOM, AiProviders.of("https://my-gateway.example.com/v1").id)
        assertEquals(AiProvider.ID_CUSTOM, AiProviders.of("").id)
    }

    @Test
    fun byId_unknownFallsBackToFirst() {
        assertEquals(AiProviders.ALL.first().id, AiProviders.byId("不存在").id)
    }

    // ---------- 关键约束 ----------

    @Test
    fun allPresetsUseOpenAiCompatibleShape() {
        // 这些服务都走 /chat/completions，所以请求代码共用；地址不该带尾斜杠
        AiProviders.ALL.filter { it.baseUrl.isNotBlank() }.forEach { p ->
            assertFalse("${p.label} 的地址不该以斜杠结尾", p.baseUrl.endsWith("/"))
            assertTrue("${p.label} 的地址应是 http(s)", p.baseUrl.startsWith("https://"))
        }
    }

    @Test
    fun providerIdsAreStable() {
        // id 会写进用户配置，改名会让老配置认不出来
        assertEquals(listOf("deepseek", "openai", "qwen", AiProvider.ID_CUSTOM), AiProviders.ALL.map { it.id })
    }
}
