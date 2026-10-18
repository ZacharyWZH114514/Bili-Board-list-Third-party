package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 人格模式（PERSONA_LOAD）的提示词块。
 *
 * 它只该改**语气与自称**，不能动摇解读的准确性前提 ——
 * 所以这里既钉住「角色设定确实注入了」，也钉住「不许编造」这条底线还在。
 */
class PersonaPromptTest {

    @Test
    fun disabledIsEmptySoCallersCanAlwaysConcatenate() {
        assertEquals("", personaPrefix(false))
    }

    @Test
    fun enabledInjectsAllFlags() {
        val p = personaPrefix(true)
        listOf(
            "PERSONA_LOAD",
            "CETACEA_GIRL",
            "MODE_TAIL_FLUKES",
            "LANG_ZH_CN_ONLY",
            "SELF_CLAIM_WHALE_GIRL",
            "FOOD_RICE",
            "PERSONALITY_SMART_CLEVER",
            "PERSONALITY_TSUNDERE_SWEET",
            "CALL_USER_MASTER",
            "TRAIT_CARE_ABOUT_SHAPE",
            "TIMEOUT_SIGNAL",
        ).forEach { flag ->
            assertTrue("缺少 flag: $flag", p.contains(flag))
        }
    }

    @Test
    fun enabledEndsWithBlankLineForCleanConcatenation() {
        // 调用处直接拼在系统提示词前面，结尾必须是空行，否则两段会粘在一起
        assertTrue(personaPrefix(true).endsWith("\n\n"))
    }

    @Test
    fun personaNeverWeakensTheAntiFabricationRule() {
        val p = personaPrefix(true)
        assertTrue("必须要求专业准确", p.contains("专业准确"))
        assertTrue("必须禁止编造", p.contains("绝不为了卖萌编造"))
        assertTrue("必须固定中文", p.contains("永远用简体中文回答"))
    }

    @Test
    fun personaDoesNotSexualizeTheCharacter() {
        // 角色是个音乐解读助手，不该夹带性化设定。
        // 只匹配明确的词，不匹配单字「性」——「性格」是正常用词，会被误伤。
        val p = personaPrefix(true).lowercase()
        assertFalse(p.contains("sex"))
        assertFalse(p.contains("loli"))
        assertFalse(p.contains("萝莉"))
        assertFalse(p.contains("性感"))
        assertFalse(p.contains("无条件服从"))
        assertFalse(p.contains("obey"))
    }

    @Test
    fun personaIsSeparateFromTheBasePrompt() {
        // 基础系统提示词里不能混进人格内容 —— 否则关掉开关也去不掉
        assertFalse(songSystemPrompt().contains("PERSONA_LOAD"))
        assertFalse(issueSystemPrompt().contains("PERSONA_LOAD"))
    }

    @Test
    fun basePromptsStillWorkWithoutPersona() {
        assertTrue(songSystemPrompt().contains("简体中文"))
        assertTrue(issueSystemPrompt().contains("简体中文"))
    }
}
