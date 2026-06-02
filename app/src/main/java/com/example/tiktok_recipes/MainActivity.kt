package com.example.tiktok_recipes

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.chaquo.python.Python

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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

            //OK button
            builder.setPositiveButton("OK") { _, _ ->
                val name = etName.text.toString()
                val link = etLink.text.toString()

                //Checking if user typed sth
                if (name.isNotEmpty() && link.isNotEmpty()) {
                    val py = Python.getInstance()
                    val absPath=py.getModule("Recipes.py").callAttr("Recipes")
                    Toast.makeText(this, "Added: $name", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Type something ;s", Toast.LENGTH_SHORT).show()
                }
            }

            //Cancel button
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

            builder.show()
        }
    }
}