package za.bilbd.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import za.bilbd.app.MainActivity

/** 当前生效的图标配色（供侧边导航栏等处显示） */
object AppIconState {
    private val _currentId = MutableStateFlow(IconPalettes.all.first().id)
    val currentId: StateFlow<String> = _currentId.asStateFlow()

    fun refresh(context: Context) {
        // 启动时对账一次：万一上次切换被系统中途打断，这里会把状态修回「恰好启用一个」
        AppIconSwitcher.reconcile(context)
        _currentId.value = AppIconSwitcher.detect(context).id
    }

    fun mark(id: String) {
        _currentId.value = id
    }
}

/**
 * 图标切换。
 *
 * 两件不同的事，别混：
 *  1. **预设配色**：切换的是真正的启动器图标。清单里为每套配色声明了一个
 *     activity-alias，运行时用 PackageManager 启用其中一个、禁用其余。
 *  2. **自定义图片**：系统不允许第三方应用把启动器图标换成用户自选的图片
 *     （android:icon 只能是打包进 APK 的资源，没有运行时修改的 API）。
 *     所以这里用 ShortcutManager 的「固定快捷方式」在桌面创建一个入口，
 *     图标就是用户选的那张图，点击进入 App。
 */
object AppIconSwitcher {

    /** 自定义图标的固定快捷方式 id——用固定值，重复创建会覆盖同一个 */
    const val CUSTOM_SHORTCUT_ID = "custom_icon"

    private const val PREFS = "biliboard_icon"
    private const val KEY_HAS_CUSTOM = "has_custom"
    private const val KEY_PALETTE = "palette_id"

    /** 与 app/build.gradle.kts 里的 namespace 一致：alias 声明为 .icon.Xxx */
    private const val ALIAS_PREFIX = "za.bilbd.app.icon"

    // ------------------------------------------------------------------
    // 预设配色（真实启动图标）
    // ------------------------------------------------------------------

    /** 当前配色：优先看本地记录，其次看组件的启用状态 */
    fun detect(context: Context): IconPalette {
        prefs(context).getString(KEY_PALETTE, null)?.let { id ->
            IconPalettes.all.firstOrNull { it.id == id }?.let { return it }
        }
        val pm = context.packageManager
        for (palette in IconPalettes.all) {
            val comp = resolveAlias(context, palette.alias) ?: continue
            if (pm.getComponentEnabledSetting(comp) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return palette
            }
        }
        // 都没被显式改过 -> 清单里 enabled="true" 的那个
        return IconPalettes.all.first()
    }

    /**
     * 启用目标 alias、禁用其余，返回是否成功。
     *
     * 顺序很重要：**先启用目标，再禁用其余**。
     * 先禁用「当前正在使用的那个 alias」会让系统回收本进程，
     * 后面的启用就执行不到了，结果是一个启动入口都不剩。
     */
    fun applyPalette(context: Context, palette: IconPalette): Boolean {
        prefs(context).edit().putString(KEY_PALETTE, palette.id).apply()
        val pm = context.packageManager

        val target = resolveAlias(context, palette.alias)
        val ok = target != null && runCatching {
            pm.setComponentEnabledSetting(
                target,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }.isSuccess

        for (p in IconPalettes.all) {
            if (p.id == palette.id) continue
            val comp = resolveAlias(context, p.alias) ?: continue
            runCatching {
                pm.setComponentEnabledSetting(
                    comp,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
        if (ok) AppIconState.mark(palette.id)
        return ok
    }

    /**
     * 把组件状态对齐到「只启用当前配色」。
     * 只在状态确实不一致时才调用 setComponentEnabledSetting，
     * 避免每次冷启动都触发一次组件变更（那可能导致进程被回收）。
     */
    fun reconcile(context: Context) {
        val preferred = detect(context)
        val pm = context.packageManager
        val manifestDefault = IconPalettes.all.first().id
        for (p in IconPalettes.all) {
            val comp = resolveAlias(context, p.alias) ?: continue
            val wantEnabled = p.id == preferred.id
            val current = pm.getComponentEnabledSetting(comp)
            val satisfied = when (current) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> wantEnabled
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> !wantEnabled
                // DEFAULT 表示「跟清单一致」，清单里只有第一个是 enabled="true"
                else -> wantEnabled == (p.id == manifestDefault)
            }
            if (!satisfied) {
                runCatching {
                    pm.setComponentEnabledSetting(
                        comp,
                        if (wantEnabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }
    }

    /**
     * 定位清单里声明的 activity-alias。
     *
     * 注意：`getPackageInfo(GET_ACTIVITIES)` 在部分版本上**不返回被禁用的 alias**，
     * 所以不能只靠它——否则「禁用着的目标 alias」永远解析不到，切换就会失败。
     * 这里先按 targetActivity 特征去找，找不到再按清单前缀直接拼。
     *
     * alias 在清单里写作 `.icon.Xxx`，相对的是 namespace（不是带 .debug 后缀的 applicationId）。
     */
    private fun resolveAlias(context: Context, simpleName: String): ComponentName {
        val pm = context.packageManager
        val pkg = context.packageName
        val info = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrNull()
        val hit = info?.activities?.firstOrNull {
            it.targetActivity != null && it.name.endsWith(".$simpleName")
        }
        return hit?.let { ComponentName(pkg, it.name) }
            ?: ComponentName(pkg, "$ALIAS_PREFIX.$simpleName")
    }

    // ------------------------------------------------------------------
    // 自定义图片图标（桌面快捷方式）
    // ------------------------------------------------------------------

    fun isPinSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun hasCustomIcon(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_CUSTOM, false)

    /** 用给定图片在桌面创建一个入口 */
    fun pinCustomIcon(context: Context, bitmap: Bitmap, label: String): Boolean {
        val shortcut = ShortcutInfoCompat.Builder(context, CUSTOM_SHORTCUT_ID)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithAdaptiveBitmap(bitmap))
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
            .build()
        val ok = runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.getOrDefault(false)
        if (ok) prefs(context).edit().putBoolean(KEY_HAS_CUSTOM, true).apply()
        return ok
    }

    fun removeCustomIcon(context: Context) {
        runCatching {
            ShortcutManagerCompat.disableShortcuts(context, listOf(CUSTOM_SHORTCUT_ID), null)
        }
        prefs(context).edit().putBoolean(KEY_HAS_CUSTOM, false).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
