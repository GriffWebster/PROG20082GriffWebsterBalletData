
package sheridan.webstgri.assignment2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val DATA_URL =
    "https://files.tetervak.com/mobile/ballet-data-app/data/ballet.json"

private const val IMAGE_BASE_URL =
    "https://files.tetervak.com/mobile/ballet-data-app/images/"

private val BalletPink = Color(0xFF9C456B)
private val BalletBackground = Color(0xFFFFF7FA)
private val BalletText = Color(0xFF33232C)
private val BalletMuted = Color(0xFF806B76)
private val BalletAccent = Color(0xFFF3DFE8)

data class BalletDancer(
    val name: String,
    val imageUrl: String,
    val details: List<Pair<String, String>>
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                var dancers by remember {
                    mutableStateOf<List<BalletDancer>>(emptyList())
                }

                var isLoading by remember {
                    mutableStateOf(true)
                }

                var errorMessage by remember {
                    mutableStateOf("")
                }

                fun loadDancers() {
                    isLoading = true
                    errorMessage = ""

                    lifecycleScope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                downloadDancers()
                            }

                            dancers = result
                            isLoading = false

                            if (result.isEmpty()) {
                                errorMessage =
                                    "No dancer records were found."
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage =
                                "Unable to load the ballet data. Check your internet connection and try again."
                        }
                    }
                }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    loadDancers()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BalletBackground
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Header(dancerCount = dancers.size)

                        when {
                            isLoading -> {
                                LoadingScreen()
                            }

                            errorMessage.isNotEmpty() -> {
                                ErrorScreen(
                                    message = errorMessage,
                                    onRetry = { loadDancers() }
                                )
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = 24.dp
                                    ),
                                    verticalArrangement =
                                        Arrangement.spacedBy(16.dp)
                                ) {
                                    items(
                                        items = dancers,
                                        key = { dancer ->
                                            dancer.name + dancer.imageUrl
                                        }
                                    ) { dancer ->
                                        DancerCard(dancer)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun downloadDancers(): List<BalletDancer> {
    val connection = URL(DATA_URL).openConnection()
            as HttpURLConnection

    connection.requestMethod = "GET"
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.setRequestProperty(
        "Accept",
        "application/json"
    )

    try {
        if (connection.responseCode !in 200..299) {
            throw Exception("Server returned an error")
        }

        val jsonText = connection.inputStream
            .bufferedReader()
            .use { it.readText() }

        return parseDancers(jsonText)
    } finally {
        connection.disconnect()
    }
}

private fun parseDancers(jsonText: String): List<BalletDancer> {
    val trimmed = jsonText.trim()

    val records = when {
        trimmed.startsWith("[") -> {
            val array = JSONArray(trimmed)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)
            }
        }

        trimmed.startsWith("{") -> {
            val root = JSONObject(trimmed)
            var array: JSONArray? = null

            val possibleKeys = listOf(
                "dancers",
                "ballet",
                "data",
                "results",
                "items"
            )

            for (key in possibleKeys) {
                val value = root.opt(key)
                if (value is JSONArray) {
                    array = value
                    break
                }
            }

            if (array == null) {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val value = root.opt(keys.next())
                    if (value is JSONArray) {
                        array = value
                        break
                    }
                }
            }

            val foundArray = array
                ?: throw Exception("No dancer array was found")

            (0 until foundArray.length()).mapNotNull { index ->
                foundArray.optJSONObject(index)
            }
        }

        else -> throw Exception("Invalid JSON data")
    }

    return records.mapNotNull { record ->
        val allFields = mutableListOf<Pair<String, String>>()
        val keys = record.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val value = record.opt(key)

            if (value != null && value != JSONObject.NULL) {
                val text = when (value) {
                    is JSONObject, is JSONArray -> value.toString()
                    else -> value.toString()
                }

                if (text.isNotBlank()) {
                    allFields.add(key to text)
                }
            }
        }

        fun normalize(value: String): String {
            return value.lowercase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
        }

        fun findField(
            names: List<String>
        ): String? {
            return allFields.firstOrNull { field ->
                normalize(field.first) in names
            }?.second
        }

        val imageField = allFields.firstOrNull { field ->
            val key = normalize(field.first)
            key.contains("image") ||
                    key.contains("photo") ||
                    key.contains("picture")
        }

        val filename = imageField?.second?.trim()
            ?.substringAfterLast("/")
            ?.takeIf { it.isNotBlank() }

        val firstName = findField(
            listOf("firstname", "givenname")
        )

        val lastName = findField(
            listOf("lastname", "surname", "familyname")
        )

        val nameField = findField(
            listOf("name", "fullname", "dancername", "title")
        )

        val dancerName = when {
            firstName != null && lastName != null ->
                "$firstName $lastName"

            nameField != null ->
                nameField

            firstName != null ->
                firstName

            else ->
                "Ballet Dancer"
        }

        val details = allFields.filter { field ->
            val key = normalize(field.first)

            key !in listOf(
                "name",
                "fullname",
                "dancername",
                "title",
                "firstname",
                "givenname",
                "lastname",
                "surname",
                "familyname"
            ) &&
                    field != imageField &&
                    !key.contains("image") &&
                    !key.contains("photo") &&
                    !key.contains("picture")
        }.map { field ->
            field.first
                .replace("_", " ")
                .replace("-", " ")
                .split(" ")
                .joinToString(" ") { word ->
                    word.replaceFirstChar {
                        if (it.isLowerCase()) {
                            it.titlecase(Locale.ROOT)
                        } else {
                            it.toString()
                        }
                    }
                } to field.second
        }

        val imageUrl = filename?.let {
            IMAGE_BASE_URL + it
        } ?: ""

        BalletDancer(
            name = dancerName,
            imageUrl = imageUrl,
            details = details
        )
    }
}

@Composable
private fun Header(dancerCount: Int) {
    Surface(
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 22.dp,
                    vertical = 20.dp
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(BalletAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "B",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = BalletPink
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "BALLET DATA",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = BalletText,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Discover the dancers",
                        fontSize = 14.sp,
                        color = BalletMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BalletAccent)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "THE WORLD OF BALLET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BalletPink,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (dancerCount > 0) {
                            "$dancerCount dancers to explore"
                        } else {
                            "Meet talented ballet dancers"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = BalletText
                    )
                }
            }
        }
    }
}

@Composable
private fun DancerCard(dancer: BalletDancer) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .background(BalletAccent),
                contentAlignment = Alignment.Center
            ) {
                if (dancer.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = dancer.imageUrl,
                        contentDescription = dancer.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "Ballet",
                        color = BalletPink,
                        fontSize = 24.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = dancer.name,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = BalletText
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(BalletPink)
                )

                Spacer(modifier = Modifier.height(12.dp))

                dancer.details.forEach { detail ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = detail.first,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BalletPink
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = detail.second,
                            fontSize = 15.sp,
                            color = BalletText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = BalletPink
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Loading ballet dancers...",
                fontSize = 16.sp,
                color = BalletMuted
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Unable to load dancers",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = BalletText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                fontSize = 15.sp,
                color = BalletMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BalletPink
                )
            ) {
                Text("Try Again")
            }
        }
    }
}
