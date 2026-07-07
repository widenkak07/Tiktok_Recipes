package com.example.tiktok_recipes

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yausername.youtubedl_android.YoutubeDL
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecipeAdapter
    private var nextId = 1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerViewRecipes)

        val savedRecipes = RecipeStorage.loadRecipes(this)
        nextId = (savedRecipes.maxOfOrNull { it.id } ?: 0L) + 1

        adapter = RecipeAdapter(savedRecipes) { recipe -> showRecipeDetail(recipe) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Inicjalizacja yt-dlp w tle (raz, przy starcie apki)
        Thread {
            try {
                YoutubeDL.getInstance().init(this)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        showAddDialog()
    }

    private fun showAddDialog() {
        val buttonAdd = findViewById<Button>(R.id.buttonAdd)

        buttonAdd.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            val inflater = layoutInflater
            val dialogLayout = inflater.inflate(R.layout.add_dialog_box, null)
            val etName = dialogLayout.findViewById<EditText>(R.id.etName)
            val etLink = dialogLayout.findViewById<EditText>(R.id.etLink)

            builder.setTitle("Add new recipe!")
            builder.setView(dialogLayout)

            builder.setPositiveButton("OK") { _, _ ->
                val name = etName.text.toString()
                val link = etLink.text.toString()

                if (name.isNotEmpty() && link.isNotEmpty()) {
                    processLink(name, link)
                } else {
                    Toast.makeText(this, "Type something ;s", Toast.LENGTH_SHORT).show()
                }
            }

            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

            builder.show()
        }
    }

    private fun processLink(userGivenName: String, link: String) {
        progressBar.visibility = android.view.View.VISIBLE

        Thread {
            try {
                // 1. Pobranie opisu z TikToka przez yt-dlp
                val videoInfo = YoutubeDL.getInstance().getInfo(link)
                val description = videoInfo.description ?: ""

                // 2. Zapytanie do Gemini
                // =========================================================
                // ============ TU WPISZ / EDYTUJ SWÓJ PROMPT =============
                // =========================================================
                val prompt = """
                    Wypisz mi składniki z tego TikToka na podstawie opisu; wszystko w języku polskim chyba, że chodzi o konkretną nazwe np pecorino romano. 
                    Jeśli napotkasz problem i nie dasz rady wypisac wszystkich składników napisz: 'PROBLEM 0'.
                    Opis filmiku: "$description"

                    Odpowiedz WYŁĄCZNIE w formacie JSON, bez żadnego dodatkowego tekstu, dokładnie w takiej strukturze:
                    {
                      "name": "nazwa dania",
                      "ingredients": ["składnik 1", "składnik 2"],
                        
                    }
                """.trimIndent()
                //TO DO dodaj "link": link do tiktoka
                // =========================================================

                val rawResponse = GeminiHelper.askGemini(prompt)
                val recipeText = rawResponse

                // 3. Parsowanie odpowiedzi AI do zmiennej "recipe"
                val cleanJson = recipeText
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val recipeJson = JSONObject(cleanJson)
                val aiName = recipeJson.optString("name", userGivenName)
                val ingredientsArray = recipeJson.optJSONArray("ingredients") ?: JSONArray()
                val ingredients = mutableListOf<String>()
                for (i in 0 until ingredientsArray.length()) {
                    ingredients.add(ingredientsArray.getString(i))
                }
                val instructions = recipeJson.optString("instructions", "")

                val recipe = Recipe(
                    id = nextId++,
                    name = aiName.ifEmpty { userGivenName },
                    ingredients = ingredients,
                    instructions = instructions
                )

                // 4. Zapis do JSON na dysku
                RecipeStorage.addRecipe(this, recipe)

                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    adapter.updateData(RecipeStorage.loadRecipes(this))
                    Toast.makeText(this, "Added: ${recipe.name}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, "Błąd: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showRecipeDetail(recipe: Recipe) {
        val dialogLayout = layoutInflater.inflate(R.layout.dialog_recipe_detail, null)
        dialogLayout.findViewById<android.widget.TextView>(R.id.tvDetailName).text = recipe.name
        dialogLayout.findViewById<android.widget.TextView>(R.id.tvDetailIngredients).text =
            recipe.ingredients.joinToString("\n") { "• $it" }
        dialogLayout.findViewById<android.widget.TextView>(R.id.tvDetailInstructions).text =
            recipe.instructions

        AlertDialog.Builder(this)
            .setView(dialogLayout)
            .setPositiveButton("Zamknij", null)
            .setNegativeButton("Usuń") { _, _ ->
                RecipeStorage.deleteRecipe(this, recipe.id)
                adapter.updateData(RecipeStorage.loadRecipes(this))
                Toast.makeText(this, "Usunięto: ${recipe.name}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}