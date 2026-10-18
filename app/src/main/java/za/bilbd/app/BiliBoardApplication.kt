package za.bilbd.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import za.bilbd.app.ui.AppIconState

/** 注册全局 Coil 图片加载器（封面来自 biliboard.uk 的图片代理，会 302 到 B 站图床） */
class BiliBoardApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // 读出用户当前选中的图标配色，供侧边导航栏、「关于」页显示
        AppIconState.refresh(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
}
