// 顶层构建文件，您可以在其中添加所有子项目/模块共用的配置选项。
plugins {
    id("com.android.application") version "7.4.2" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
