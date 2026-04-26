package com.example.progettopm2026.llmSearch.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
@Entity
data class MenuEmbedding(
    @Id var id: Long = 0,

    var itemId: String = "",
    var title: String = "",
    var description: String = "",

    @HnswIndex(dimensions = 384)
    var embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MenuEmbedding

        if (id != other.id) return false
        if (itemId != other.itemId) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + itemId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
