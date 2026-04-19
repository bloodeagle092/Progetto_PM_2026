package com.example.progettopm2026.jsonConverter.model

import com.example.progettopm2026.jsonConverter.data.Menu

class MenuPostProcessor {

    fun normalize(menu: Menu): Menu {
        return menu.copy(
            restaurantName = menu.restaurantName.nullIfBlank(),
            currency = menu.currency.nullIfBlank(),
            dishes = menu.dishes
                .map { dish ->
                    dish.copy(
                        name = dish.name.trim(),
                        description = dish.description.nullIfBlank(),
                        category = dish.category.nullIfBlank(),
                        notes = dish.notes.nullIfBlank(),
                        ingredients = dish.ingredients
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct(),
                        allergens = dish.allergens
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                    )
                }
                .filter { it.name.isNotBlank() }
        )
    }

    private fun String?.nullIfBlank(): String? {
        return if (this == null || this.isBlank()) null else this.trim()
    }
}