package com.example.tiktok_recipes

data class Recipe(
    val id: Long,
    val name: String,
    val ingredients: List<String>,
    val link: String,
    val note: String = ""
)