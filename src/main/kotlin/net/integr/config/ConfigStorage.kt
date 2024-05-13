package net.integr.config

import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Files
import javax.naming.ConfigurationException
import kotlin.io.path.Path

class ConfigStorage(val mainUrl: String, val emailPassword: String, val emailUsername: String, val email: String, val sessionSignKey: String, val sessionEncryptKey: String) {
    companion object {
        var INSTANCE: ConfigStorage? = null
        fun load() {
            val f = File("./data/config.json")
            if (!f.exists()) {
                save(ConfigStorage("http://127.0.0.1:8080", "NONE", "NONE", "NONE", "6819b47a326945c1968f45236389", "00112243445566778899aabbccdeeeff"))
                throw ConfigurationException("Please edit the config values!")
            }

            val json = f.readText()
            INSTANCE =  GsonBuilder().setPrettyPrinting().create().fromJson(json, ConfigStorage::class.java)
        }

        private fun save(storage: ConfigStorage) {
            val json = GsonBuilder().setPrettyPrinting().create().toJson(storage)
            Files.createDirectories(Path("./data"))
            File("./data/config.json").writeText(json)
        }
    }
}