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
            val etLink = dialogLayout.findViewById<EditText>(R.id.etLink)

            builder.setTitle("Dodaj nowy przepis!")
            builder.setView(dialogLayout)

            builder.setPositiveButton("Ok") { _, _ ->
                val link = etLink.text.toString()

                if (link.isNotEmpty()) {
                    processLink(link)
                } else {
                    Toast.makeText(this, "Wpisz cos ;s", Toast.LENGTH_SHORT).show()
                }
            }

            builder.setNegativeButton("Anuluj") { dialog, _ ->
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

    private fun processLink(link: String) {
        val trimmedLink = link.trim()
        if (!trimmedLink.startsWith("http://") && !trimmedLink.startsWith("https://")) {
            Toast.makeText(this, "Zły link!", Toast.LENGTH_LONG).show()
            return
        }
        if (!trimmedLink.contains("tiktok.com")) {
            Toast.makeText(this, "Napisz link do tiktoka", Toast.LENGTH_LONG).show()
            return
        }

        runOnUiThread { startFakeProgress() }

        Thread {
            try {
                val videoInfo = try {
                    YoutubeDL.getInstance().getInfo(trimmedLink)
                } catch (e: Exception) {
                    throw Exception("Tiktok nie znaleziony!")
                }

                val description = videoInfo.description?.trim() ?: ""
                if (description.isEmpty()) {
                    throw Exception("Brak opisu!")
                }

                val prompt = """
                    Na podstawie poniższego opisu filmiku z TikToka wygeneruj przepis kulinarny.
                    Jeżeli opis NIE zawiera żadnego przepisu kulinarnego (np. to nie jest film o gotowaniu),
                    zwróć dokładnie: {"error": "no_recipe"}
                    Wszystko ma być w języku polskim. tłumacz wszystko na polski jednostek takich jak tbsp czy cup nie ruszaj ALE zamieniaj lb na g zgodnie
                    z ogólnodostepnym przelicznikiem. Jedyne zczego nie tlumacz to naz wlasnych np. grana padano oraz nazw dań
                    Opis filmiku: "$description"
                    
                    W przeciwnym razie odpowiedz WYŁĄCZNIE w formacie JSON, bez żadnego dodatkowego tekstu:
                    {
                      "name": "nazwa dania",
                      "ingredients": ["składnik 1", "składnik 2"]
                    }
                """.trimIndent()

                val rawResponse = try {
                    GeminiHelper.askGemini(prompt)
                } catch (e: Exception) {
                    throw Exception("Błąd połączenia z AI: ${e.message}")
                }

                val cleanJson = rawResponse
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val recipeJson = try {
                    JSONObject(cleanJson)
                } catch (e: Exception) {
                    throw Exception("AI zwróciło niepoprawną odpowiedź. Spróbuj ponownie.")
                }

                if (recipeJson.has("error")) {
                    throw Exception("AI nie wykryło przepisu kulinarnego w tym filmiku.")
                }

                val aiName = recipeJson.optString("name", "Bez nazwy").trim()
                val ingredientsArray = recipeJson.optJSONArray("ingredients")
                val ingredients = mutableListOf<String>()
                if (ingredientsArray != null) {
                    for (i in 0 until ingredientsArray.length()) {
                        ingredients.add(ingredientsArray.getString(i))
                    }
                }

                if (ingredients.isEmpty()) {
                    throw Exception("AI nie znalazło żadnych składników w tym filmiku.")
                }

                val recipe = Recipe(
                    id = nextId++,
                    name = aiName.ifEmpty { "Bez nazwy" },
                    ingredients = ingredients,
                    link = trimmedLink,
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
                    Toast.makeText(this, e.message ?: "Wystąpił nieznany błąd", Toast.LENGTH_LONG).show()
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