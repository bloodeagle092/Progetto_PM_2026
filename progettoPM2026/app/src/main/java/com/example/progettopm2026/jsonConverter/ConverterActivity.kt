package com.example.progettopm2026.jsonConverter

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.progettopm2026.BuildConfig
import com.example.progettopm2026.databinding.ActivityConverterBinding
import com.example.progettopm2026.jsonConverter.data.MenuSource
import com.example.progettopm2026.jsonConverter.model.MenuConverter
import com.example.progettopm2026.jsonConverter.model.MenuExtractor
import com.example.progettopm2026.jsonConverter.model.MenuLlmClient
import com.example.progettopm2026.jsonConverter.viewModel.MenuUiState
import com.example.progettopm2026.jsonConverter.viewModel.MenuViewModel
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
                    // LLM calls take a few seconds; default 10s timeout is too short.
                    .callTimeout(60, TimeUnit.SECONDS)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val llmClient = MenuLlmClient(
                    httpClient = httpClient,
                    apiKey = BuildConfig.ANTHROPIC_API_KEY
                )
                val extractor = MenuExtractor(applicationContext)
                val converter = MenuConverter(extractor, llmClient)

                @Suppress("UNCHECKED_CAST")
                return MenuViewModel(converter) as T
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

    // Pretty-printer for the output JSON view.
    private val prettyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityConverterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupClickListeners()
        observeUiState()
    }

    private fun setupClickListeners() {
        binding.buttonPickImage.setOnClickListener { pickImage.launch("image/*") }
        binding.buttonPickPdf.setOnClickListener   { pickPdf.launch("application/pdf") }
        binding.buttonPickText.setOnClickListener  { pickTextFile.launch("text/*") }

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
        // Reset visibility each time, then show only what this state needs.
        binding.progressBar.visibility = android.view.View.GONE
        binding.textViewResult.visibility = android.view.View.GONE
        binding.textViewError.visibility = android.view.View.GONE

        when (state) {
            is MenuUiState.Idle -> {
                // Nothing to show yet; buttons remain enabled.
            }
            is MenuUiState.Loading -> {
                binding.progressBar.visibility = android.view.View.VISIBLE
            }
            is MenuUiState.Success -> {
                binding.textViewResult.visibility = android.view.View.VISIBLE
                binding.textViewResult.text = prettyJson.encodeToString(state.menu)
            }
            is MenuUiState.Error -> {
                binding.textViewError.visibility = android.view.View.VISIBLE
                binding.textViewError.text = state.message
            }
        }
    }
}
