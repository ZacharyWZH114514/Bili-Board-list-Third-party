package za.bilbd.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import za.bilbd.app.data.AiConfig
import za.bilbd.app.data.AiProvider
import za.bilbd.app.data.AiProviders
import za.bilbd.app.data.DeepSeekApi

/**
 * AI 分析设置。
 *
 * Key 完全由用户自己填，应用不内置也不上传；只保存在本机 SharedPreferences。
 * 默认接 DeepSeek，同时允许改 Base URL 以兼容其它 OpenAI 格式的服务。
 */
@Composable
fun AiSettingsScreen(
    dark: Boolean,
    config: AiConfig,
    onSave: (AiConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 各服务商的 Key 各自保存，互不覆盖
    var keys by remember { mutableStateOf(config.keys) }
    var apiKey by remember { mutableStateOf(config.keyFor(AiProviders.of(config.baseUrl).id)) }
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var model by remember { mutableStateOf(config.model) }
    // 当前服务商：只决定预填哪个地址与模型，请求代码完全共用
    var providerId by remember { mutableStateOf(AiProviders.of(config.baseUrl).id) }
    val provider = AiProviders.byId(providerId)
    var doubaoKey by remember { mutableStateOf(config.doubaoApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var showDoubaoKey by remember { mutableStateOf(false) }
    var persona by remember { mutableStateOf(config.persona) }

    // 申请 Key 的入口：直接跳系统浏览器
    val uriHandler = LocalUriHandler.current
    val openUrl: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }

    // 任何一项改动都立刻落盘，不需要额外的「保存」按钮
    fun save() = onSave(
        AiConfig(
            keys = keys,
            baseUrl = baseUrl.trim(),
            model = model,
            doubaoApiKey = doubaoKey.trim(),
            persona = persona,
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(0).fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("${provider.label} API Key", Modifier.weight(1f))
                // 明确告诉用户「已经存住了」：改动是即时落盘的、没有保存按钮，
                // 界面看不出来时人容易以为没保存而反复重填
                if (apiKey.isNotBlank()) {
                    Text(
                        text = "已保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            GlassInput(
                value = apiKey,
                onValueChange = { apiKey = it; keys = keys + (provider.id to it.trim()); save() },
                placeholder = "sk-…",
                dark = dark,
                masked = !showKey,
            )
            Spacer(Modifier.height(8.dp))
            if (provider.keyUrl.isNotBlank()) {
                KeyLink(
                    label = "点这里去 ${provider.label} 申请 Key",
                    url = provider.keyUrl,
                    onOpen = openUrl,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "改动即时保存到本机，不需要点保存；关掉应用再进来还在。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (showKey) "隐藏" else "显示",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showKey = !showKey }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when {
                        apiKey.isBlank() -> "未填写"
                        else -> "已填写（${apiKey.length} 字符）"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp).padding(top = 1.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Key 只保存在本机（SharedPreferences 明文），不会上传到除该 AI 服务之外的任何地方。" +
                        "这是个人应用的做法；如果你在意安全性，建议单独申请一个只用于本应用的 Key。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 服务商：选定后自动填好 Base URL 与模型，请求代码完全共用
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(1).fillMaxWidth(),
        ) {
            SectionTitle("服务商")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProviders.ALL.forEach { p ->
                    Chip(
                        label = p.label,
                        selected = provider.id == p.id,
                        dark = dark,
                        onClick = {
                            // 先把当前输入框的 Key 归到旧服务商名下，再切到新的并回填它的 Key
                            keys = keys + (provider.id to apiKey.trim())
                            providerId = p.id
                            apiKey = keys[p.id].orEmpty()
                            // 选预设就把地址和模型一起填好；自定义则留空让用户自己写
                            if (!p.isCustom) {
                                baseUrl = p.baseUrl
                                model = p.models.firstOrNull().orEmpty()
                            }
                            save()
                        },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "这些都是 OpenAI 兼容接口，Key 也各用各的，换服务商请重新填 Key。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(2).fillMaxWidth(),
        ) {
            SectionTitle("模型")
            Spacer(Modifier.height(10.dp))
            if (provider.models.isEmpty()) {
                // 自定义服务商：模型名没法预设，让用户直接写
                GlassInput(
                    value = model,
                    onValueChange = { model = it; save() },
                    placeholder = "模型名，例如 gpt-4o、qwen-plus",
                    dark = dark,
                    masked = false,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    provider.models.forEach { m ->
                        Chip(
                            label = m,
                            selected = model == m,
                            dark = dark,
                            onClick = { model = m; save() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = provider.hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 人格模式：只改语气与自称，不改判断依据
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(3).fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "人格模式",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "开启后 AI 会用固定的角色口吻回答（鲸鱼少女、傲娇但认真）。" +
                            "只影响语气，解读依据不变。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = persona,
                    onCheckedChange = { persona = it; save() },
                )
            }
        }

        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(3).fillMaxWidth(),
        ) {
            SectionTitle("接口地址")
            Spacer(Modifier.height(8.dp))
            GlassInput(
                value = baseUrl,
                onValueChange = { baseUrl = it; save() },
                placeholder = "https://…/v1",
                dark = dark,
                masked = false,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (provider.isCustom) {
                    "填兼容 OpenAI /chat/completions 的地址，例如 http://127.0.0.1:11434/v1（Ollama）。"
                } else {
                    "已按「${provider.label}」填好。也可以手动改，应用会自动拼上 /chat/completions。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(3).fillMaxWidth(),
        ) {
            SectionTitle("豆包搜索 API Key")
            Spacer(Modifier.height(8.dp))
            GlassInput(
                value = doubaoKey,
                onValueChange = { doubaoKey = it; save() },
                placeholder = "用于联网查歌词与背景资料",
                dark = dark,
                masked = !showDoubaoKey,
            )
            Spacer(Modifier.height(8.dp))
            KeyLink(
                label = "点这里去火山引擎控制台申请 Key",
                url = DOUBAO_KEY_URL,
                onOpen = openUrl,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (showDoubaoKey) "隐藏" else "显示",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDoubaoKey = !showDoubaoKey }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when {
                        doubaoKey.isBlank() -> "未填写（解读将没有联网资料）"
                        else -> "已填写（${doubaoKey.length} 字符）"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "「AI 解读这首歌」会先用豆包搜索（Custom 版）联网查这首歌的歌词与创作背景，" +
                    "再把资料连同歌曲信息一起交给模型写解读。" +
                    "不填也能用，但模型只能靠曲名和 P主 猜，容易编——不建议。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(4).fillMaxWidth(),
        ) {
            SectionTitle("怎么用")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "填好 Key 之后，进入任意一期榜单，点「AI 分析本期」即可。" +
                    "分析结果按「期」缓存，同一期不会重复消耗额度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun GlassInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    dark: Boolean,
    masked: Boolean,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(inputFill(dark))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (masked) PasswordVisualTransformation()
            else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 申请 Key 的入口。用系统浏览器打开，不在应用内嵌 WebView ——
 * 登录火山引擎 / DeepSeek 的账号密码不该经过这个应用的进程。
 */
@Composable
private fun KeyLink(
    label: String,
    url: String,
    onOpen: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onOpen(url) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = url,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 申请 DeepSeek API Key */


/** 申请豆包搜索（联网搜索）API Key */
private const val DOUBAO_KEY_URL = "https://console.volcengine.com/search-infinity/web-search"

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else inputFill(dark))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 输入框 / 未选中 chip 的底色。
 *
 * 深色模式要**提亮**、浅色模式要**压暗**，才能读成「一个输入区」。
 * 之前这里写反了（深色用黑、浅色用白），两边都几乎看不见。
 */
private fun inputFill(dark: Boolean) =
    if (dark) androidx.compose.ui.graphics.Color(0x1FFFFFFF)
    else androidx.compose.ui.graphics.Color(0x14000000)
