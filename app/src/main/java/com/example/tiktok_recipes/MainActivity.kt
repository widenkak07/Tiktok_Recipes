package com.example.tiktok_recipes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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

    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private var currentProgress = 0

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

    // ==== Symulowany pasek postępu ====
    private fun startFakeProgress() {
        currentProgress = 0
        progressBar.progress = 0
        progressBar.visibility = View.VISIBLE

        progressRunnable = object : Runnable {
            override fun run() {
                if (currentProgress < 95) {
                    currentProgress += 1
                    progressBar.progress = currentProgress
                    progressHandler.postDelayed(this, 150)
                }
            }
        }
        progressHandler.post(progressRunnable!!)
    }

    private fun finishProgress(success: Boolean) {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        if (success) {
            progressBar.progress = 100
            progressHandler.postDelayed({ progressBar.visibility = View.GONE }, 300)
        } else {
            progressBar.visibility = View.GONE
        }
    }

    private fun processLink(userGivenName: String, link: String) {
        runOnUiThread { startFakeProgress() }

        Thread {
            try {
                val videoInfo = YoutubeDL.getInstance().getInfo(link)
                val description = videoInfo.description ?: ""

                // =========================================================
                // ============ TU WPISZ / EDYTUJ SWÓJ PROMPT =============
                // =========================================================
                val prompt = """
                    Wypisz mi składniki z tego TikToka na podstawie opisu,wszystko w języku polskim chyba, że chodzi o konkretną nazwe np pecorino romano. 
                    Jeśli napotkasz problem i nie dasz rady wypisac wszystkich składników napisz: 'PROBLEM 0'

                    Opis filmiku: "$description"

                    Odpowiedz WYŁĄCZNIE w formacie JSON, bez żadnego dodatkowego tekstu, dokładnie w takiej strukturze:
                    {
                      "name": "nazwa dania",
                      "ingredients": ["składnik 1", "składnik 2"]
                    }
                """.trimIndent()
                // =========================================================

                val rawResponse = GeminiHelper.askGemini(prompt)

                val cleanJson = rawResponse
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

                val recipe = Recipe(
                    id = nextId++,
                    name = aiName.ifEmpty { userGivenName },
                    ingredients = ingredients,
                    link = link,
                    note = ""
                )

                RecipeStorage.addRecipe(this, recipe)

                runOnUiThread {
                    finishProgress(success = true)
                    adapter.updateData(RecipeStorage.loadRecipes(this))
                    Toast.makeText(this, "Added: ${recipe.name}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    finishProgress(success = false)
                    Toast.makeText(this, "Błąd: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showRecipeDetail(clickedRecipe: Recipe) {
        // Zawsze wczytaj najświeższą wersję z dysku, na wypadek wcześniejszych edycji
        val recipe = RecipeStorage.loadRecipes(this).firstOrNull { it.id == clickedRecipe.id } ?: clickedRecipe

        val dialogLayout = layoutInflater.inflate(R.layout.dialog_recipe_detail, null)
        val tvName = dialogLayout.findViewById<android.widget.TextView>(R.id.tvDetailName)
        val tvIngredients = dialogLayout.findViewById<android.widget.TextView>(R.id.tvDetailIngredients)
        val tvLink = dialogLayout.findViewById<android.widget.TextView>(R.id.tvDetailLink)
        val etNote = dialogLayout.findViewById<EditText>(R.id.etDetailNote)
        val btnDelete = dialogLayout.findViewById<android.widget.ImageButton>(R.id.btnDeleteRecipe)

        tvName.text = recipe.name
        tvIngredients.text = recipe.ingredients.joinToString("\n") { "• $it" }
        tvLink.text = recipe.link
        etNote.setText(recipe.note)

        val detailDialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .create()

        tvLink.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(recipe.link))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Nie można otworzyć linku", Toast.LENGTH_SHORT).show()
            }
        }

        tvName.setOnClickListener {
            val editText = EditText(this)
            editText.setText(tvName.text.toString())
            AlertDialog.Builder(this)
                .setTitle("Zmień nazwę")
                .setView(editText)
                .setPositiveButton("Zapisz") { _, _ ->
                    val newName = editText.text.toString()
                    if (newName.isNotBlank()) {
                        val current = RecipeStorage.loadRecipes(this).firstOrNull { it.id == recipe.id } ?: return@setPositiveButton
                        val updated = current.copy(name = newName)
                        RecipeStorage.updateRecipe(this, updated)
                        tvName.text = newName
                        adapter.updateData(RecipeStorage.loadRecipes(this))
                    }
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }

        val noteHandler = Handler(Looper.getMainLooper())
        var noteSaveRunnable: Runnable? = null

        fun saveNoteNow() {
            noteSaveRunnable?.let { noteHandler.removeCallbacks(it) }
            val current = RecipeStorage.loadRecipes(this).firstOrNull { it.id == recipe.id } ?: return
            val updated = current.copy(note = etNote.text.toString())
            RecipeStorage.updateRecipe(this, updated)
        }

        etNote.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                noteSaveRunnable?.let { noteHandler.removeCallbacks(it) }
                noteSaveRunnable = Runnable { saveNoteNow() }
                noteHandler.postDelayed(noteSaveRunnable!!, 800)
            }
        })

        detailDialog.setOnDismissListener {
            saveNoteNow()
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Usunąć przepis?")
                .setMessage("Tej operacji nie można cofnąć.")
                .setPositiveButton("Usuń") { _, _ ->
                    RecipeStorage.deleteRecipe(this, recipe.id)
                    adapter.updateData(RecipeStorage.loadRecipes(this))
                    Toast.makeText(this, "Usunięto: ${recipe.name}", Toast.LENGTH_SHORT).show()
                    detailDialog.dismiss()
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }

        detailDialog.show()
    }
}