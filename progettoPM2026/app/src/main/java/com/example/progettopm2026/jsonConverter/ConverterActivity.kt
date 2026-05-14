package com.example.progettopm2026.jsonConverter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.*
import com.example.progettopm2026.R
import com.example.progettopm2026.BuildConfig
import com.example.progettopm2026.databinding.ActivityConverterBinding
import com.example.progettopm2026.jsonConverter.data.MenuSource
import com.example.progettopm2026.jsonConverter.db.DatabaseProvider
import com.example.progettopm2026.jsonConverter.model.*
import com.example.progettopm2026.jsonConverter.repository.MenuRepository
import com.example.progettopm2026.jsonConverter.storage.MenuFileStore
import com.example.progettopm2026.jsonConverter.viewModel.MenuUiState
import com.example.progettopm2026.jsonConverter.viewModel.MenuViewModel
import com.example.progettopm2026.llmSearch.SearchActivity
import com.example.progettopm2026.llmSearch.database.MenuEmbeddingStore
import com.example.progettopm2026.llmSearch.embedding.SearchEmbeddingProvider
import com.example.progettopm2026.llmSearch.indexing.MenuSearchIndexer
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ConverterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConverterBinding

    private val viewModel: MenuViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val httpClient = OkHttpClient.Builder()
                    .callTimeout(360, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(360, TimeUnit.SECONDS)
                    .build()

                val llmClient = MenuLlmClient(
                    httpClient = httpClient,
                    apiKey = BuildConfig.OPENAI_API_KEY
                )

                val extractor = MenuExtractor(applicationContext)
                val cleaner = MenuTextCleaner()
                val postProcessor = MenuPostProcessor()

                val converter = MenuConverter(
                    extractor = extractor,
                    textCleaner = cleaner,
                    llmClient = llmClient,
                    postProcessor = postProcessor
                )

                val db = DatabaseProvider.getDatabase(applicationContext)
                val runtime = SearchEmbeddingProvider.get(applicationContext)
                val searchIndexer = MenuSearchIndexer(
                    embedder = runtime.embedder,
                    store = MenuEmbeddingStore(applicationContext)
                )
                val repository = MenuRepository(
                    fileStore = MenuFileStore(applicationContext),
                    dao = db.savedMenuDao(),
                    searchIndexer = searchIndexer
                )

                @Suppress("UNCHECKED_CAST")
                return MenuViewModel(converter, repository) as T
            }
        }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadMenu(MenuSource.ImageFile(it)) }
    }

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadMenu(MenuSource.PdfFile(it)) }
    }

    private val pickTextFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadMenu(MenuSource.TextFile(it)) }
    }

    private val prettyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityConverterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val initialPaddingLeft = binding.root.paddingLeft
        val initialPaddingTop = binding.root.paddingTop
        val initialPaddingRight = binding.root.paddingRight
        val initialPaddingBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                initialPaddingLeft + systemBars.left,
                initialPaddingTop + systemBars.top,
                initialPaddingRight + systemBars.right,
                initialPaddingBottom + systemBars.bottom
            )
            insets
        }

        setupClickListeners()
        showSourceView()
        observeUiState()
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonChangeSource.setOnClickListener { showSourceView() }
        binding.buttonOpenSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
            finish()
        }
        binding.buttonPickImage.setOnClickListener { pickImage.launch("image/*") }
        binding.buttonPickPdf.setOnClickListener { pickPdf.launch("application/pdf") }
        binding.buttonPickText.setOnClickListener { pickTextFile.launch("text/*") }

        binding.buttonLoadUrl.setOnClickListener {
            val url = binding.editTextUrl.text.toString().trim()
            if (url.isBlank()) {
                Toast.makeText(this, "Enter a URL first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.loadMenu(MenuSource.WebUrl(url))
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: MenuUiState) {
        binding.progressBar.visibility = View.GONE
        binding.textViewResult.visibility = View.GONE
        binding.textViewError.visibility = View.GONE

        when (state) {
            is MenuUiState.Idle -> {
                showSourceView()
            }
            is MenuUiState.Loading -> {
                showSourceView()
                binding.progressBar.visibility = View.VISIBLE
            }
            is MenuUiState.Success -> {
                showResultView()
                binding.textViewResult.visibility = View.VISIBLE
                binding.textViewResult.text = prettyJson.encodeToString(state.menu)
            }
            is MenuUiState.Error -> {
                showSourceView()
                binding.textViewError.visibility = View.VISIBLE
                binding.textViewError.text = state.message
            }
        }
    }

    private fun showSourceView() {
        binding.sourceContainer.visibility = View.VISIBLE
        binding.resultContainer.visibility = View.GONE
        binding.buttonOpenSearch.visibility = View.GONE
        centerTitleInScreen()
    }

    private fun showResultView() {
        binding.sourceContainer.visibility = View.GONE
        binding.resultContainer.visibility = View.VISIBLE
        binding.buttonOpenSearch.visibility = View.VISIBLE
        centerTitleBetweenActions()
    }

    private fun centerTitleInScreen() {
        ConstraintSet().apply {
            clone(binding.root)
            connect(R.id.textViewConverterTitle, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(R.id.textViewConverterTitle, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            applyTo(binding.root)
        }
    }

    private fun centerTitleBetweenActions() {
        ConstraintSet().apply {
            clone(binding.root)
            connect(R.id.textViewConverterTitle, ConstraintSet.START, R.id.buttonBack, ConstraintSet.END)
            connect(R.id.textViewConverterTitle, ConstraintSet.END, R.id.buttonOpenSearch, ConstraintSet.START)
            applyTo(binding.root)
        }
    }
}
