package com.example.aiassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private lateinit var languageManager: ContactLanguageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageManager = ContactLanguageManager(this)

        setContent {
            VoicemailManagerTheme {
                VoicemailScreen(
                    languageManager = languageManager,
                    onStartService = { startAssistantService() },
                    onRequestPermissions = { requestAppPermissions() }
                )
            }
        }
    }

    private fun startAssistantService() {
        val serviceIntent = Intent(this, AssistantForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Assistant & Voicemail Active", Toast.LENGTH_SHORT).show()
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(permissions.toTypedArray(), 101)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicemailScreen(
    languageManager: ContactLanguageManager,
    onStartService: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context: Context = LocalContext.current
    var contactName by remember { mutableStateOf("") }
    var contactNumber by remember { mutableStateOf("") }
    var selectedLanguageName by remember { mutableStateOf("English") }
    var selectedLanguageCode by remember { mutableStateOf("en") }
    var expandedDropdown by remember { mutableStateOf(false) }

    var rulesList by remember { mutableStateOf(languageManager.getAllRules()) }

    val languages = listOf(
        Pair("English", "en"),
        Pair("Hindi", "hi"),
        Pair("Spanish", "es"),
        Pair("French", "fr"),
        Pair("German", "de"),
        Pair("Arabic", "ar"),
        Pair("Mandarin", "zh")
    )

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = result.data?.data
        if (uri != null) {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    contactName = it.getString(0) ?: "Unknown"
                    contactNumber = it.getString(1) ?: ""
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Assistant & Voicemail", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Autonomous Voicemail Agent",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Automatically disconnects incoming calls and logs custom multilingual voicemails.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onStartService) {
                                Text("Start Service")
                            }
                            OutlinedButton(onClick = onRequestPermissions) {
                                Text("Permissions")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Configure Contact Voicemail", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                contactPickerLauncher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (contactName.isBlank()) "Choose Contact from Phone" else "Contact: $contactName")
                        }

                        if (contactNumber.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Number: $contactNumber", fontSize = 13.sp, color = Color(0xFF9E9E9E))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        ExposedDropdownMenuBox(
                            expanded = expandedDropdown,
                            onExpandedChange = { expandedDropdown = !expandedDropdown }
                        ) {
                            OutlinedTextField(
                                value = selectedLanguageName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Voicemail Language") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedDropdown,
                                onDismissRequest = { expandedDropdown = false }
                            ) {
                                languages.forEach { (langName, langCode) ->
                                    DropdownMenuItem(
                                        text = { Text(langName) },
                                        onClick = {
                                            selectedLanguageName = langName
                                            selectedLanguageCode = langCode
                                            expandedDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (contactNumber.isNotBlank()) {
                                    languageManager.saveContactRule(
                                        contactNumber,
                                        if (contactName.isBlank()) contactNumber else contactName,
                                        selectedLanguageName,
                                        selectedLanguageCode
                                    )
                                    rulesList = languageManager.getAllRules()
                                    contactName = ""
                                    contactNumber = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = contactNumber.isNotBlank()
                        ) {
                            Text("Save Voicemail Rule")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Configured Contacts (${rulesList.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (rulesList.isEmpty()) {
                item {
                    Text(
                        text = "No custom rules set. Default language is English.",
                        color = Color(0xFF9E9E9E),
                        fontSize = 13.sp
                    )
                }
            } else {
                items(rulesList) { rule ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(rule.number, fontSize = 13.sp, color = Color(0xFF9E9E9E))
                                Spacer(modifier = Modifier.height(4.dp))
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Language: ${rule.languageName}") }
                                )
                            }
                            IconButton(
                                onClick = {
                                    languageManager.removeContactRule(rule.number)
                                    rulesList = languageManager.getAllRules()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove Rule",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoicemailManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF38BDF8),
            onPrimary = Color(0xFF0F172A),
            primaryContainer = Color(0xFF0369A1),
            onPrimaryContainer = Color(0xFFF0F9FF),
            surface = Color(0xFF1E293B),
            surfaceVariant = Color(0xFF334155),
            error = Color(0xFFEF4444)
        ),
        content = content
    )
}
