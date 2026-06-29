package com.sakovich.collegeapp.presentation.chat

import android.content.Context
import org.json.JSONObject


object LocalStickerCatalog {

    data class StaticEntry(val id: String, val assetPath: String, val tags: List<String>)

    data class Catalog(val stickers: List<StaticEntry>)

    fun load(context: Context): Catalog {
        val jsonText = context.assets.open("stickers/stickers_catalog.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonText)
        val list = mutableListOf<StaticEntry>()
        val arr = root.optJSONArray("static") ?: return Catalog(emptyList())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                StaticEntry(
                    id = o.getString("id"),
                    assetPath = o.getString("path"),
                    tags = o.optJSONArray("tags")?.let { a ->
                        (0 until a.length()).map { a.getString(it) }
                    }.orEmpty()
                )
            )
        }
        return Catalog(list)
    }

    fun find(catalog: Catalog, id: String): StaticEntry? =
        catalog.stickers.firstOrNull { it.id == id }
}
