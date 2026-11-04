package com.apex.martyr

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NetworkManager {
    private const val CONFIG_URL = "https://gist.githubusercontent.com/nesusbuill/47438ecc83d6e224dd8e993009f8daf5/raw/rules.json"

    fun fetchRules(): List<String> {
        val defaultRules = listOf(
            "files/hero*", "files/unit*", "files/effect*", 
            "files/item*", "files/avatar*", "cache/*"
        )

        return try {
            val url = URL(CONFIG_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val paths = mutableListOf<String>()
                val array = json.getJSONArray("delete_paths")
                for (i in 0 until array.length()) {
                    paths.add(array.getString(i))
                }
                return paths
            }
            defaultRules
        } catch (e: Exception) {
            e.printStackTrace()
            defaultRules
        }
    }
}
