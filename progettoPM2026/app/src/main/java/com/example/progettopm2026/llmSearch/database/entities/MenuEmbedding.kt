package com.example.progettopm2026.llmSearch.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
@Entity
data class MenuEmbedding(
    @Id var id: Long = 0,

    var itemId: String = "",
    var restaurantName: String = "",
    var title: String = "",
    var description: String = "",
    var category: String = "",
    var ingredients: String = "",
    var notes: String = "",
    var allergens: String = "",
    var prices: String = "",
    var searchText: String = "",

    @HnswIndex(dimensions = 384)
    var embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MenuEmbedding

        if (id != other.id) return false
        if (itemId != other.itemId) return false
        if (restaurantName != other.restaurantName) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (category != other.category) return false
        if (ingredients != other.ingredients) return false
        if (notes != other.notes) return false
        if (allergens != other.allergens) return false
        if (prices != other.prices) return false
        if (searchText != other.searchText) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + itemId.hashCode()
        result = 31 * result + restaurantName.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + ingredients.hashCode()
        result = 31 * result + notes.hashCode()
        result = 31 * result + allergens.hashCode()
        result = 31 * result + prices.hashCode()
        result = 31 * result + searchText.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
