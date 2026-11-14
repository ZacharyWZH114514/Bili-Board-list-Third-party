# 保留 org.json 反射用到的构造函数（Android 平台自带，仅在需要时启用）
-keepclassmembers class org.json.** { *; }

# ---------------------------------------------------------------------------
# WorkManager
#
# WorkManager 有两处是**反射实例化**的，R8 会把没人显式调用的构造器裁掉：
#   1. Room 生成的 androidx.work.impl.WorkDatabase_Impl（无参构造）
#   2. 自定义 Worker 子类（按类名从数据库里 new 出来）
#
# 3.8 的 release 包就是这么崩的（一启动就闪退）：
#   java.lang.NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []
#   at androidx.work.WorkManagerInitializer
# debug 包不复现，因为 debug 不开 R8 —— 这类问题只有 release 装机才看得见。
# ---------------------------------------------------------------------------
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl$* { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }

-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepnames class za.bilbd.app.data.NewIssueWorker
