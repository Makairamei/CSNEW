import com.lagradost.cloudstream3.gradle.CloudstreamExtension

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.lagradost.cloudstream3.gradle")
}

cloudstream {
    // Gunakan sintaks ini untuk menentukan Main Class
    mainClass = "com.betbet.yunshanid.YunshanIDPlugin"
    
    // Jika 'name =' error, biarkan plugin mengambil nama dari folder, 
    // atau gunakan fungsi config seperti di bawah ini:
    setDisplayName("YunshanID")
    setDescription("Donghua & Anime provider dari YunshanID")
    setAuthors(listOf("Betbet"))
    
    language = "id"
}

android {
    // Gunakan namespace yang sama dengan package di Provider/Plugin
    namespace = "com.betbet.yunshanid"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
    }
}
