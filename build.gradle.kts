// Root build.gradle.kts — versões de plugin usadas por todos os módulos
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

tasks.register("clean", Delete::class) {
    description = "Limpa o diretório de build do projeto"
    delete(layout.buildDirectory)
}
