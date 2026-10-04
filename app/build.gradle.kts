plugins {
    id("com.android.application")
}

android {
    namespace = "com.dagu.hyperalarmplus"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dagu.hyperalarmplus"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")
}
