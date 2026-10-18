package za.bilbd.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import za.bilbd.app.R

/** 引导页的一屏 */
private data class OnboardPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val footnote: String? = null,
)

/**
 * 新手引导（首启 OOBE）。
 *
 * 只在第一次打开时出现，之后可以在「设置 → 重看新手引导」再看。
 *
 * 内容选择上刻意只讲**用户不看就会误解的三件事**：
 *  1. 这是第三方客户端，不是官方；
 *  2. 数据是离线的、每 3 天增量更新一次，**不实时**——不说清楚最容易招来「怎么没更新」；
 *  3. AI 功能要自己填 Key，且完全可以不用。
 *
 * 没有讲「怎么滑动列表」这类废话，也不强制登录、不收集任何东西。
 */
@Composable
fun OnboardingScreen(
    dark: Boolean,
    onFinish: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val pages = listOf(
        OnboardPage(
            icon = Icons.Filled.Star,
            title = stringResource(R.string.onboard_1_title),
            body = stringResource(R.string.onboard_1_body),
            footnote = stringResource(R.string.onboard_1_note),
        ),
        OnboardPage(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.onboard_2_title),
            body = stringResource(R.string.onboard_2_body),
            footnote = stringResource(R.string.onboard_2_note),
        ),
        OnboardPage(
            icon = Icons.Filled.Star,
            title = stringResource(R.string.onboard_3_title),
            body = stringResource(R.string.onboard_3_body),
            footnote = stringResource(R.string.onboard_3_note),
        ),
        OnboardPage(
            icon = Icons.Filled.Favorite,
            title = stringResource(R.string.onboard_4_title),
            body = stringResource(R.string.onboard_4_body),
            footnote = stringResource(R.string.onboard_4_note),
        ),
    )

    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == pages.lastIndex

    Box(Modifier.fillMaxSize()) {
        // 复用全局的液态玻璃背景，让引导页和主界面是同一套视觉
        GlassBackdrop(dark = dark, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                // 应用是 edge-to-edge 的。不加内边距时「跳过」会落在状态栏下面点不到，
                // 底部按钮也会被导航栏盖住 —— 这个 bug 是实测时发现的。
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            // 右上角「跳过」：不逼人一页页翻完
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.onboard_skip)) }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 16.dp,
            ) { index ->
                OnboardPageContent(pages[index], dark)
            }

            // 页码圆点
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.indices.forEach { i ->
                    val active = i == pager.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 9.dp else 6.dp)
                            .background(
                                color = if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 13.dp),
                    onClick = {
                        if (isLast) {
                            onFinish()
                        } else {
                            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (isLast) stringResource(R.string.onboard_start) else stringResource(R.string.onboard_next),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (pager.currentPage == 2) {
                    // 讲到 AI 的那一页给个直达入口，省得用户回头去找
                    TextButton(
                        onClick = {
                            onFinish()
                            onOpenAiSettings()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.onboard_open_ai)) }
                }
            }
        }
    }
}

@Composable
private fun OnboardPageContent(page: OnboardPage, dark: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(24.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    page.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    // 引导文案里的 ** 只是我写文档时的强调记号，显示时去掉
                    text = page.body.replace("**", ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                )
                page.footnote?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
