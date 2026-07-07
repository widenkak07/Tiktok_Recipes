package com.example.tiktok_recipes

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecipeAdapter(
    private val recipes: MutableList<Recipe>,
    private val onClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    class RecipeViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvRecipeName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.tvName.text = recipe.name
        holder.itemView.setOnClickListener { onClick(recipe) }
    }

    override fun getItemCount(): Int = recipes.size

    fun updateData(newRecipes: List<Recipe>) {
        recipes.clear()
        recipes.addAll(newRecipes)
        notifyDataSetChanged()
    }
}