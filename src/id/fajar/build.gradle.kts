import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Fajar"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    deeplink {
        host("mwland.my")
        host("*.mwland.my")
        path("/komik/..*")
    }

    source {
        lang = "id"
        baseUrl = "https://beta.mwland.my"
    }
}
