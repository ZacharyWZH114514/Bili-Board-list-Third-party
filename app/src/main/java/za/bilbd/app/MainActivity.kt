package za.bilbd.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import za.bilbd.app.data.ThemeMode
import za.bilbd.app.ui.AppRoot
import za.bilbd.app.ui.AppViewModel
import za.bilbd.app.ui.BiliBoardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // ViewModel 在这里创建，主题与页面栈共用同一个实例
            val vm: AppViewModel = viewModel()

            // 外观由设置决定（跟随系统 / 浅色 / 深色）。
            // 界面里的玻璃明暗是从 MaterialTheme 的背景亮度推导的（isDarkGlass），
            // 所以这里选定之后，整应用的深浅色会一起跟着变，不需要逐个页面传。
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            BiliBoardTheme(darkTheme = darkTheme) {
                // 液态玻璃底色与页面栈都放在 AppRoot 里，
                // 这样曲目详情页可以按封面主题色换背景
                AppRoot(vm = vm)
            }
        }
    }
}
