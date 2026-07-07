package com.example.tiktok_recipes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object RecipeStorage {
    private const val FILE_NAME = "recipes.json"

    fun loadRecipes(context: Context): MutableList<Recipe> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return mutableListOf()

        val jsonText = file.readText()
        val jsonArray = JSONArray(jsonText)
        val list = mutableListOf<Recipe>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val ingredientsArray = obj.getJSONArray("ingredients")
            val ingredients = mutableListOf<String>()
            for (j in 0 until ingredientsArray.length()) {
                ingredients.add(ingredientsArray.getString(j))
            }
            list.add(
                Recipe(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    ingredients = ingredients,
                    instructions = obj.getString("instructions")
                )
            )
        }
        return list
    }

    fun saveRecipes(context: Context, recipes: List<Recipe>) {
        val jsonArray = JSONArray()
        for (recipe in recipes) {
            val obj = JSONObject()
            obj.put("id", recipe.id)
            obj.put("name", recipe.name)
            obj.put("ingredients", JSONArray(recipe.ingredients))
            obj.put("instructions", recipe.instructions)
            jsonArray.put(obj)
        }
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(jsonArray.toString())
    }

    fun addRecipe(context: Context, recipe: Recipe) {
        val list = loadRecipes(context)
        list.add(recipe)
        saveRecipes(context, list)
    }

    fun deleteRecipe(context: Context, recipeId: Long) {
        val list = loadRecipes(context)
        list.removeAll { it.id == recipeId }
        saveRecipes(context, list)
    }
}