package com.example.progettopm2026.llmSearch

import android.os.Bundle
import android.text.Editable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.progettopm2026.databinding.ActivitySearchBinding
import com.example.progettopm2026.llmSearch.database.MenuEmbeddingStore
import com.example.progettopm2026.llmSearch.embedding.SearchEmbeddingProvider
import com.example.progettopm2026.llmSearch.search.MenuSearchService
import com.example.progettopm2026.llmSearch.search.SearchResult
import kotlinx.coroutines.launch
import java.util.Locale

class SearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchBinding
    private val embeddingStore by lazy { MenuEmbeddingStore(applicationContext) }
    private var lastQueryText: String = ""
    private lateinit var embeddingBackendLabel: String

    private val viewModel: SearchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val runtime = SearchEmbeddingProvider.get(applicationContext)
                val service = MenuSearchService(
                    embedder = runtime.embedder,
                    store = MenuEmbeddingStore(applicationContext)
                )

                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(service) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        embeddingBackendLabel = SearchEmbeddingProvider.get(applicationContext).backendLabel

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupClickListeners()
        observeUiState()
        refreshDebugPanel()
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonNewChat.setOnClickListener {
            binding.chatContainer.removeAllViews()
            binding.textViewError.visibility = View.GONE
            viewModel.reset()
            lastQueryText = ""
            refreshDebugPanel()
        }

        binding.buttonSend.setOnClickListener {
            submitQuery()
        }

        binding.buttonClearIndex.setOnClickListener {
            clearIndexedItems()
        }

        binding.editTextMessage.setOnEditorActionListener { _, _, _ ->
            submitQuery()
            true
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderState(state)
            }
        }
    }

    private fun submitQuery() {
        val query = binding.editTextMessage.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) return

        lastQueryText = query
        refreshDebugPanel()
        appendBubble(query, isUser = true)
        binding.editTextMessage.text = Editable.Factory.getInstance().newEditable("")
        viewModel.search(query)
    }

    private fun renderState(state: SearchUiState) {
        when (state) {
            SearchUiState.Idle -> Unit
            SearchUiState.Loading -> {
                binding.textViewError.visibility = View.GONE
                appendSystemBubble("Searching...")
            }
            is SearchUiState.Empty -> {
                binding.textViewError.visibility = View.GONE
                appendSystemBubble("No matches for \"${state.query}\".")
            }
            is SearchUiState.Error -> {
                binding.textViewError.visibility = View.VISIBLE
                binding.textViewError.text = state.message
            }
            is SearchUiState.Success -> {
                binding.textViewError.visibility = View.GONE
                appendSystemBubble("Found ${state.results.size} results for \"${state.query}\":")
                state.results.forEachIndexed { index, result ->
                    appendResultBubble(index + 1, result)
                }
            }
        }
    }

    private fun refreshDebugPanel() {
        lifecycleScope.launch {
            val indexedCount = runCatching { embeddingStore.count() }.getOrDefault(0L)
            binding.textViewDebug.text = buildString {
                appendLine("Debug mode")
                appendLine("ObjectBox: active")
                appendLine("Indexed items: $indexedCount")
                appendLine("Embedder: $embeddingBackendLabel")
                append("Last query: ")
                append(if (lastQueryText.isBlank()) "none" else lastQueryText)
            }
        }
    }

    private fun clearIndexedItems() {
        lifecycleScope.launch {
            runCatching {
                embeddingStore.clearAll()
            }.onSuccess {
                binding.chatContainer.removeAllViews()
                lastQueryText = ""
                viewModel.reset()
                refreshDebugPanel()
                Toast.makeText(this@SearchActivity, "Indexed items cleared", Toast.LENGTH_SHORT).show()
            }.onFailure { throwable ->
                binding.textViewError.visibility = View.VISIBLE
                binding.textViewError.text = throwable.message ?: "Failed to clear index"
            }
        }
    }

    private fun appendSystemBubble(text: String) {
        appendBubble(text, isUser = false)
    }

    private fun appendResultBubble(rank: Int, result: SearchResult) {
        val formattedScore = String.format(Locale.ROOT, "%.3f", result.score)

        appendBubble(
            buildString {
                append('#')
                append(rank)
                append(' ')
                append(result.title)
                if (result.restaurantName.isNotBlank()) append('\n').append("Restaurant: ").append(result.restaurantName)
                if (result.description.isNotBlank()) append('\n').append("Description: ").append(result.description)
                if (result.category.isNotBlank()) append('\n').append("Category: ").append(result.category)
                if (result.ingredients.isNotBlank()) append('\n').append("Ingredients: ").append(result.ingredients)
                if (result.allergens.isNotBlank()) append('\n').append("Allergens: ").append(result.allergens)
                if (result.notes.isNotBlank()) append('\n').append("Notes: ").append(result.notes)
                if (result.prices.isNotBlank()) append('\n').append("Prices: ").append(result.prices)
                append('\n')
                append("score: ")
                append(formattedScore)
            },
            isUser = false
        )
    }

    private fun appendBubble(text: String, isUser: Boolean) {
        val bubble = TextView(this).apply {
            this.text = text
            setTextColor(if (isUser) 0xFF101010.toInt() else 0xFFFFFFFF.toInt())
            textSize = 15f
            setPadding(24, 18, 24, 18)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 28f
                setColor(if (isUser) 0xFFD4AF37.toInt() else 0xFF1E1E1E.toInt())
            }
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 12
            bottomMargin = 4
            if (isUser) {
                gravity = Gravity.END
                marginStart = 80
            } else {
                gravity = Gravity.START
                marginEnd = 80
            }
        }

        binding.chatContainer.addView(bubble, params)
        binding.chatScrollView.post {
            binding.chatScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
}
