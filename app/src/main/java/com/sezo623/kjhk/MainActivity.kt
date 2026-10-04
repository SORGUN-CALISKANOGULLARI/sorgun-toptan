package com.sezo623.kjhk

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class CartItem(
    val productName: String,
    val unitType: String,
    val quantity: Int,
    val unitPrice: Double,
    val adetFiyat: Double,
    val koliFiyat: Double,
    val discountPercent: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
            ) {
                MainAppNavigator()
            }
        }
    }
}

@Composable
fun CheckForUpdates(currentVersionCode: Int) {
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateUrl by remember { mutableStateOf("") }
    var newVersionName by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            val jsonUrl =
                "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/main/version.json"
            val jsonString = withContext(Dispatchers.IO) {
                URL(jsonUrl).readText()
            }

            if (jsonString.contains("versionCode")) {
                val remoteVersionCode =
                    jsonString.substringAfter("\"versionCode\":").substringBefore(",").trim()
                        .toIntOrNull() ?: currentVersionCode
                val extractedUrl =
                    jsonString.substringAfter("\"apkUrl\":").substringBefore("\"").replace("\"", "")
                        .trim()
                val extractedName =
                    jsonString.substringAfter("\"versionName\":").substringBefore(",")
                        .replace("\"", "").trim()

                if (remoteVersionCode > currentVersionCode) {
                    updateUrl = extractedUrl
                    newVersionName = extractedName
                    showUpdateDialog = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("🚀 Yeni Güncelleme Var!") },
            text = { Text("Uygulamanın yeni versiyonu ($newVersionName) yayınlandı. Güncellemeyi şimdi indirmek ister misiniz?") },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                    context.startActivity(intent)
                }) {
                    Text("Evet, İndir")
                }
            },
            dismissButton = {
                Button(onClick = { showUpdateDialog = false }) {
                    Text("Daha Sonra")
                }
            })
    }
}

@Composable
fun MainAppNavigator() {
    CheckForUpdates(currentVersionCode = 1)

    val context = LocalContext.current
    val sharedPreferences =
        remember { context.getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE) }

    var currentScreen by remember {
        mutableStateOf(value = sharedPreferences.getString("current_screen", "login") ?: "login")
    }

    var userRole by remember {
        mutableStateOf(value = sharedPreferences.getString("user_role", "guest") ?: "guest")
    }

    var selectedBrand by remember { mutableStateOf(value = "") }
    val screenHistory = remember { mutableStateOf(mutableListOf("login")) }

    var showPasswordDialog by remember { mutableStateOf(value = false) }
    var showDealerPinDialog by remember { mutableStateOf(value = false) }

    val cartItems = remember { mutableStateListOf<CartItem>() }

    fun navigateTo(screen: String) {
        if (currentScreen != screen) {
            screenHistory.value.add(currentScreen)
            currentScreen = screen
        }
    }

    fun goBack() {
        if (currentScreen == "brands") {
            // Eğer marka (ana) ekranındaysak, geri tuşuna basıldığında doğrudan uygulama kapanır
            (context as? android.app.Activity)?.finish()
        } else if (screenHistory.value.isNotEmpty()) {
            val previous = screenHistory.value.removeAt(screenHistory.value.size - 1)
            currentScreen = previous
        } else {
            currentScreen = "brands"
        }
    }

    // Geri tuşu kontrolü (Login ekranı haricinde aktif olur)
    androidx.activity.compose.BackHandler(enabled = currentScreen != "login") {
        goBack()
    }

    fun saveSession(screen: String, role: String) {
        userRole = role
        screenHistory.value.clear()
        screenHistory.value.add("login")
        currentScreen = screen
        sharedPreferences.edit().apply {
            putString("current_screen", screen)
            putString("user_role", role)
            apply()
        }
    }

    when (currentScreen) {
        "login" -> LoginScreen(
            onGuestClick = { saveSession("brands", "guest") },
            onDealerClick = { showDealerPinDialog = true },
            onAdminClick = { showPasswordDialog = true })

        "brands" -> BrandListScreen(
            userRole = userRole,
            cartCount = cartItems.size,
            onBrandClick = { brand ->
                selectedBrand = brand
                if (brand == "Pelagos Evcil Hayvan Mamaları") {
                    navigateTo("pelagos_submenu")
                } else {
                    navigateTo("products")
                }
            },
            onAboutClick = { navigateTo("about") },
            onCartClick = { navigateTo("cart") },
            onLogout = { saveSession("login", "guest") })

        "pelagos_submenu" -> PelagosSubMenuScreen(
            cartCount = cartItems.size,
            onBackClick = { goBack() },
            onCartClick = { navigateTo("cart") },
            onSubCategoryClick = { subCat ->
                selectedBrand = subCat
                navigateTo("products")
            })

        "products" -> ProductListScreen(
            brandName = selectedBrand,
            userRole = userRole,
            cartCount = cartItems.size,
            onBackClick = { goBack() },
            onCartClick = { navigateTo("cart") },
            onAddToCart = { newItem ->
                cartItems.add(newItem)
                Toast.makeText(context, "${newItem.productName} sepete eklendi", Toast.LENGTH_SHORT)
                    .show()
            })

        "cart" -> CartScreen(
            cartItems = cartItems,
            userRole = userRole,
            onBackClick = { goBack() },
            onClearCart = { cartItems.clear() },
        )

        "about" -> AboutScreen(
            userRole = userRole, onBackClick = { goBack() })
    }

    if (showPasswordDialog) {
        var adminPin by remember { mutableStateOf("") }
        var adminError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Yönetici Girişi") },
            text = {
                Column {
                    OutlinedTextField(
                        value = adminPin,
                        onValueChange = { adminPin = it },
                        label = { Text("Yönetici Şifresi") },
                        isError = adminError,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (adminError) {
                        Text(
                            text = "Hatalı Şifre!", color = Color.Red
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (adminPin == "1234") {
                        showPasswordDialog = false
                        saveSession("brands", "admin")
                    } else {
                        adminError = true
                    }
                }) {
                    Text("Giriş Yap")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("İptal")
                }
            })
    }

    if (showDealerPinDialog) {
        var dealerPin by remember { mutableStateOf("") }
        var dealerError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showDealerPinDialog = false },
            title = { Text("Personel / Bayi Girişi") },
            text = {
                Column {
                    OutlinedTextField(
                        value = dealerPin,
                        onValueChange = { dealerPin = it },
                        label = { Text("PIN Kodu") },
                        isError = dealerError,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (dealerError) {
                        Text(
                            text = "Hatalı PIN!", color = Color.Red
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (dealerPin == "4321") {
                        showDealerPinDialog = false
                        saveSession("brands", "dealer")
                    } else {
                        dealerError = true
                    }
                }) {
                    Text("Giriş Yap")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDealerPinDialog = false }) {
                    Text("İptal")
                }
            })
    }
}

@Composable
fun LoginScreen(onGuestClick: () -> Unit, onDealerClick: () -> Unit, onAdminClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF80CBC4), shape = RoundedCornerShape(24.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "✨", fontSize = 28.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ÇALIŞKANOĞULLARI\nGıda & Toptan Satış\nKataloğu",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onGuestClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4E157)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Text(
                text = "👀 Misafir Olarak İncele",
                fontSize = 16.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onDealerClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Text(
                text = "🤝 Personel Girişi",
                fontSize = 16.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onAdminClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF9A9A)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Text(
                text = "🔐 Yönetici Girişi",
                fontSize = 16.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BrandListScreen(
    userRole: String,
    cartCount: Int,
    onBrandClick: (String) -> Unit,
    onAboutClick: () -> Unit,
    onCartClick: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var isCheckingUpdate by remember { mutableStateOf(false) }

    val brandList = listOf(
        Pair(
            "Ekici",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
        ), Pair(
            "Aytaç",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/aytac.jpeg"
        ), Pair(
            "Ankara",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/angara.jpeg"
        ), Pair(
            "Duru",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/duru.jpeg"
        ), Pair(
            "Ece",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ece.jpeg"
        ), Pair(
            "Yağızefe",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/efe.jpg"
        ), Pair(
            "Kral",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/kral.jpg"
        ), Pair(
            "Ancora",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ancora.png"
        ), Pair(
            "Veronelli",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/veronel.png"
        ), Pair(
            "Kavaklıdere",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/kavak.jpg"
        ), Pair(
            "Mehmet efendi",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/memet.jpeg"
        ), Pair(
            "Öncü",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/oncu.jpeg"
        ), Pair(
            "Akçay",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/akcay.jpeg"
        ), Pair(
            "Seçkin ürünler",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/seckn.jpeg"
        ), Pair(
            "Viora",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/VI.jpeg"
        ), Pair(
            "Catering",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/catr.jpeg"
        ), Pair(
            "Pelagos Evcil Hayvan Mamaları",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/pegolas.jpeg"
        ), Pair(
            "Yeni ürünler",
            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/yeni.png"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val roleText = when (userRole) {
                "admin" -> "👑 Yönetici"
                "dealer" -> "🤝 Personel Girişi"
                else -> "👀 Misafir"
            }
            Text(
                text = roleText,
                fontWeight = FontWeight.Bold,
                color = if (userRole == "admin") Color(0xFF2E7D32) else Color.DarkGray,
                fontSize = 13.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onCartClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "🛒 Sepet ($cartCount)",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onAboutClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90CAF9)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("ℹ Bilgi", color = Color.Black, fontSize = 10.sp)
                }

                Button(
                    onClick = {
                        val imageLoader = ImageLoader(context)
                        imageLoader.memoryCache?.clear()
                        imageLoader.diskCache?.clear()

                        isCheckingUpdate = true
                        Toast.makeText(
                            context, "Güncellemeler kontrol ediliyor...", Toast.LENGTH_SHORT
                        ).show()

                        val versionJsonUrl =
                            "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/main/version.json"
                        val currentVersionCode = 1

                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                val jsonStr = URL(versionJsonUrl).readText()
                                val jsonObj = JSONObject(jsonStr)
                                val latestVersion = jsonObj.getInt("versionCode")
                                val apkUrl = jsonObj.getString("apkUrl")

                                withContext(Dispatchers.Main) {
                                    isCheckingUpdate = false
                                    if (latestVersion > currentVersionCode) {
                                        Toast.makeText(
                                            context,
                                            "Yeni sürüm bulundu, indiriliyor...",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        val request =
                                            DownloadManager.Request(Uri.parse(apkUrl)).apply {
                                                setTitle("Çalışkanoğulları Katalog Güncelleniyor")
                                                setDescription("Yeni APK indiriliyor...")
                                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                setDestinationInExternalPublicDir(
                                                    Environment.DIRECTORY_DOWNLOADS, "update.apk"
                                                )
                                                allowScanningByMediaScanner()
                                            }
                                        val dm =
                                            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                        dm.enqueue(request)
                                    } else {
                                        Toast.makeText(
                                            context, "Uygulamanız zaten güncel!", Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isCheckingUpdate = false
                                    Toast.makeText(
                                        context, "Önbellek temizlendi!", Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF80CBC4)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (isCheckingUpdate) "⏳" else "🔄 Güncelle",
                        color = Color.Black,
                        fontSize = 10.sp
                    )
                }

                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCCBC)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Çıkış", color = Color.Black, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(brandList) { brand ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .clickable { onBrandClick(brand.first) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = brand.second,
                            contentDescription = brand.first,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PelagosSubMenuScreen(
    cartCount: Int,
    onBackClick: () -> Unit,
    onCartClick: () -> Unit,
    onSubCategoryClick: (String) -> Unit
) {
    val subCategories = listOf(
        Pair("Kedi", "https://i.ibb.co/fGPFf918/kedii.jpg"),
        Pair("Köpek", "https://i.ibb.co/4g0hkWmv/dog.jpg"),
        Pair("Kuş", "https://i.ibb.co/qFBw482R/bird.jpg")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B)),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(text = "← Geri", color = Color.White, fontSize = 13.sp)
            }

            Button(
                onClick = onCartClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    "🛒 Sepet ($cartCount)",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Pelagos Evcil Hayvan Kategorileri",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()
        ) {
            items(subCategories) { category ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { onSubCategoryClick(category.first) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AsyncImage(
                            model = category.second,
                            contentDescription = category.first,
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )

                        Text(
                            text = category.first,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CartScreen(
    cartItems: androidx.compose.runtime.snapshots.SnapshotStateList<CartItem>,
    userRole: String,
    onBackClick: () -> Unit,
    onClearCart: () -> Unit
) {
    val context = LocalContext.current
    val canSeePrices = (userRole == "admin" || userRole == "dealer")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBackClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B)),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(text = "← Geri", color = Color.White, fontSize = 13.sp)
                }

                if (cartItems.isNotEmpty()) {
                    TextButton(onClick = onClearCart) {
                        Text("Sepeti Boşalt", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "🛒 Sepetim ve Sipariş Listesi",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (cartItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Sepetiniz henüz boş.", color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(cartItems.size) { index ->
                        if (index < cartItems.size) {
                            val item = cartItems[index]

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.productName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )

                                        TextButton(
                                            onClick = {
                                                if (index < cartItems.size) {
                                                    cartItems.removeAt(index)
                                                    Toast.makeText(
                                                        context,
                                                        "Ürün sepetten çıkarıldı",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }, contentPadding = PaddingValues(
                                                horizontal = 4.dp, vertical = 0.dp
                                            )
                                        ) {
                                            Text("❌ Kaldır", color = Color.Red, fontSize = 11.sp)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    val newUnit =
                                                        if (item.unitType == "Adet") "Koli" else "Adet"
                                                    val baseForNewUnit =
                                                        if (newUnit == "Koli") item.koliFiyat else item.adetFiyat
                                                    val discountedPrice =
                                                        baseForNewUnit - (baseForNewUnit * item.discountPercent / 100.0)
                                                    cartItems[index] = item.copy(
                                                        unitType = newUnit,
                                                        unitPrice = discountedPrice
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (item.unitType == "Koli") Color(
                                                        0xFF7E57C2
                                                    ) else Color(0xFF90CAF9)
                                                ),
                                                contentPadding = PaddingValues(
                                                    horizontal = 8.dp, vertical = 2.dp
                                                ),
                                                modifier = Modifier.height(30.dp),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Birim: ${item.unitType}",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    if (item.quantity > 1) {
                                                        cartItems[index] =
                                                            item.copy(quantity = item.quantity - 1)
                                                    }
                                                },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.size(28.dp),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "-",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }

                                            Text(
                                                text = "${item.quantity}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )

                                            OutlinedButton(
                                                onClick = {
                                                    cartItems[index] =
                                                        item.copy(quantity = item.quantity + 1)
                                                },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.size(28.dp),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "+",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }

                                        if (canSeePrices) {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "Toplam: ${
                                                        String.format(
                                                            "%.1f", item.unitPrice * item.quantity
                                                        )
                                                    }₺",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.Black
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        Color(0xFFE0E0E0),
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Fiyat Gizli",
                                                    fontSize = 10.sp,
                                                    color = Color.DarkGray
                                                )
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

        if (cartItems.isNotEmpty()) {
            val totalGeneral = cartItems.sumOf { item -> item.unitPrice * item.quantity }
            Column(
                modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canSeePrices) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Genel Toplam:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = "${String.format("%.1f", totalGeneral)}₺",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                Button(
                    onClick = {
                        val sb = StringBuilder("*Yeni Sipariş Talebi - Çalışkanoğulları*\n\n")
                        cartItems.forEach {
                            sb.append("• ${it.productName} (${it.quantity} ${it.unitType})")
                            if (canSeePrices) {
                                sb.append(
                                    " - Tutar: ${
                                        String.format(
                                            "%.1f", it.unitPrice * it.quantity
                                        )
                                    }₺"
                                )
                            }
                            sb.append("\n")
                        }
                        if (canSeePrices) {
                            sb.append("\n*Genel Toplam:* ${String.format("%.1f", totalGeneral)}₺")
                        }

                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, sb.toString())
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(
                            sendIntent, "Siparişi Gönderilecek Uygulamayı Seçin"
                        )
                        try {
                            context.startActivity(shareIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Paylaşım yapılamadı!", Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "📤 Siparişi Paylaş (WhatsApp vb.)",
                        fontSize = 15.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AboutScreen(userRole: String, onBackClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B)),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(text = "← Geri", color = Color.White, fontSize = 13.sp)
            }

            Text(
                text = "Hakkımızda & Sürüm Notları",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00796B)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🏢 Firma: Çalışkanoğulları Gıda & Toptan Dağıtım",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "📍 Adres: Ayrıdam Köyü Yozgat Yolu üzeri No:95 Sorgun / Yozgat",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "📞 İletişim / Tel: [0543 462 42 90]-[0545 955 5602]",
                        fontSize = 13.sp
                    )
                    Text(text = "✉️ E-posta: ", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "📜 Sürüm Notları (Yenilikler)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF37474F)
            )

            // Sürüm Notları Kartı (Kaydırılabilir alan)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "v1.2 (Güncel Sürüm)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF00796B)
                            )
                            Text(
                                text = "• Elle girilen özel fiyatlar üzerine iskonto yüzdesi uygulama desteği eklendi.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                            Text(
                                text = "• Adet ve koli moduna özel anlık toplam tutar göstergeleri düzenlendi.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                            Text(
                                text = "• Geri tuşu gezinme optimizasyonları ve hata düzeltmeleri yapıldı.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                        }
                    }

                    item {
                        Divider(
                            modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFE0E0E0)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "v1.1",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "• Koli ve adet bazlı dinamik sepet hesaplamaları eklendi.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                            Text(
                                text = "• Pelagos evcil hayvan alt kategorileri ve barkod görüntüleme özelliği getirildi.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                        }
                    }

                    item {
                        Divider(
                            modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFE0E0E0)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "v1.0",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "• İlk yayın ve temel toptan katalog altyapısı kuruldu.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "Çalışkanoğulları Dijital Toptan Satış Kataloğu v1.2",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
        )
    }
}

@Composable
fun ProductListScreen(
    brandName: String,
    userRole: String,
    cartCount: Int,
    onBackClick: () -> Unit,
    onCartClick: () -> Unit,
    onAddToCart: (CartItem) -> Unit
) {
    val context = LocalContext.current

    val productList = when (brandName) {
        "Ekici" -> listOf(
            mapOf(
                "ad" to "ekici beyaz 800 gr",
                "bazFiyat" to 255.0,
                "koliFiyati" to 2040.0,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ek800b.jpeg"
            ),
            mapOf(
                "ad" to "ekici beyaz 500 gr",
                "bazFiyat" to 172.0,
                "koliFiyati" to 2064.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ek500be.jpeg"
            ),
            mapOf(
                "ad" to "ekici klasik 450 gr",
                "bazFiyat" to 296.25,
                "koliFiyati" to 2370.0,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ekklsk.jpeg"
            ),
            mapOf(
                "ad" to "ekici süzme 400 gr",
                "bazFiyat" to 121.0,
                "koliFiyati" to 968.0,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/eksuz.jpeg"
            ),
            mapOf(
                "ad" to "peynir çubukları 200 gr",
                "bazFiyat" to 123.5,
                "koliFiyati" to 1482.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ekcbk.jpeg"
            ),
            mapOf(
                "ad" to "top peynir 200 gr",
                "bazFiyat" to 123.5,
                "koliFiyati" to 1482.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ektop.jpeg"
            ),
            mapOf(
                "ad" to "kaşar peynir 600 gr",
                "bazFiyat" to 272.5,
                "koliFiyati" to 2452.5,
                "koliAdet" to 9,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ek600k.jpeg"
            ),
            mapOf(
                "ad" to "tel peynir 200 gr",
                "bazFiyat" to 118.5,
                "koliFiyati" to 1422.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ektel.jpeg"
            ),
            mapOf(
                "ad" to "üçgen peynir 8*12,5 gr",
                "bazFiyat" to 49.8,
                "koliFiyati" to 1195.2,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ek3gen.jpeg"
            ),
            mapOf(
                "ad" to "krem peynir 400 gr",
                "bazFiyat" to 133.0,
                "koliFiyati" to 798.0,
                "koliAdet" to 6,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ek400kr.jpeg"
            ),
            mapOf(
                "ad" to "teneke peynir 20 kg",
                "bazFiyat" to 6045.0,
                "koliFiyati" to 6045.0,
                "koliAdet" to 1,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ektnk.jpeg"
            )
        )

        "Aytaç" -> listOf(
            mapOf(
                "ad" to "yarım yağlı süt 1 lt",
                "bazFiyat" to 44.0,
                "koliFiyati" to 528.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AYYST%20(2).jpeg"
            ),
            mapOf(
                "ad" to "kakaolu fındık krema 700 gr",
                "bazFiyat" to 145.0,
                "koliFiyati" to 870.0,
                "koliAdet" to 6,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AYYGGG.jpeg"
            ),
            mapOf(
                "ad" to "şıpşak piliç 40 gr",
                "bazFiyat" to 17.32,
                "koliFiyati" to 207.84,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayyslmacp.jpeg"
            ),
            mapOf(
                "ad" to "şıpşak macar 40 gr",
                "bazFiyat" to 40.37,
                "koliFiyati" to 484.44,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayymacar.jpeg"
            ),
            mapOf(
                "ad" to "şıpşak hindi 50 gr",
                "bazFiyat" to 25.24,
                "koliFiyati" to 454.32,
                "koliAdet" to 18,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayyslmhac.jpeg"
            ),
            mapOf(
                "ad" to "gurme hindi füme 60 gr",
                "bazFiyat" to 59.81,
                "koliFiyati" to 358.86,
                "koliAdet" to 6,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayhfme60.png"
            ),
            mapOf(
                "ad" to "hindi füme 60 gr",
                "bazFiyat" to 40.15,
                "koliFiyati" to 361.35,
                "koliAdet" to 9,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayhndfm.png"
            ),
            mapOf(
                "ad" to "piliç salam 200 gr",
                "bazFiyat" to 52.11,
                "koliFiyati" to 416.88,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AYY200P.jpeg"
            ),
            mapOf(
                "ad" to "fıstıklı hindi salam 500 gr",
                "bazFiyat" to 141.4,
                "koliFiyati" to 1696.8,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AYYHS500.jpeg"
            ),
            mapOf(
                "ad" to "ç. piliç salam 500 gr",
                "bazFiyat" to 92.19,
                "koliFiyati" to 1106.28,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayycps.jpeg"
            ),
            mapOf(
                "ad" to "piliç salam 500 gr",
                "bazFiyat" to 108.95,
                "koliFiyati" to 1307.4,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AYY500PS.jpeg"
            ),
            mapOf(
                "ad" to "kokteyl sosis 240 gr",
                "bazFiyat" to 67.73,
                "koliFiyati" to 406.38,
                "koliAdet" to 6,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AYYSSS.jpeg"
            ),
            mapOf(
                "ad" to "çiftlik sosis 225 gr",
                "bazFiyat" to 56.14,
                "koliFiyati" to 336.84,
                "koliAdet" to 6,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AYYCSSS.jpeg"
            ),
            mapOf(
                "ad" to "piliç parmak 400 gr",
                "bazFiyat" to 172.51,
                "koliFiyati" to 1380.08,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayyparp.jpeg"
            ),
            mapOf(
                "ad" to "dana parmak 400 gr",
                "bazFiyat" to 445.76,
                "koliFiyati" to 2674.56,
                "koliAdet" to 6,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayypard.jpeg"
            ),
            mapOf(
                "ad" to "kangal piliç acili 180 gr",
                "bazFiyat" to 67.8,
                "koliFiyati" to 542.4,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayy180a.jpeg"
            ),
            mapOf(
                "ad" to "kangal piliç acısız 180 gr",
                "bazFiyat" to 67.8,
                "koliFiyati" to 542.4,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayy180.jpeg"
            ),
            mapOf(
                "ad" to "dana kangal crm 200 gr",
                "bazFiyat" to 212.38,
                "koliFiyati" to 1699.04,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayycrm.jpg"
            ),
            mapOf(
                "ad" to "dana kangal 280 gr",
                "bazFiyat" to 291.14,
                "koliFiyati" to 2037.98,
                "koliAdet" to 7,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AYY280SCK.jpeg"
            ),
            mapOf(
                "ad" to "çerkeş dana 1 kg",
                "bazFiyat" to 1180.12,
                "koliFiyati" to 1180.12,
                "koliAdet" to 1,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayycrkes.jpeg"
            ),
            mapOf(
                "ad" to "geleneksel dana 1 kg",
                "bazFiyat" to 1066.72,
                "koliFiyati" to 1066.72,
                "koliAdet" to 1,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ayyglnk.jpeg"
            ),
            mapOf(
                "ad" to "piknik vişne 20 gr",
                "bazFiyat" to 2.0,
                "koliFiyati" to 250.0,
                "koliAdet" to 125,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AYPNK20V.jpeg"
            ),
            mapOf(
                "ad" to "piknik çilek 20 gr",
                "bazFiyat" to 2.0,
                "koliFiyati" to 250.0,
                "koliAdet" to 125,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AYPNK20C.jpeg"
            ),
            mapOf(
                "ad" to "piknik kayısı 20 gr",
                "bazFiyat" to 2.0,
                "koliFiyati" to 250.0,
                "koliAdet" to 125,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AYPNK20.jpeg"
            )
        )
        "Yağızefe" -> listOf(
            mapOf(
                "ad" to "Ankara Spagetti 500g",
                "bazFiyat" to 18.0,
                "koliFiyati" to 360.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/angara.jpeg"
            ), mapOf(
                "ad" to "Ankara Burgu Makarna 500g",
                "bazFiyat" to 18.0,
                "koliFiyati" to 360.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/angara.jpeg"
            ), mapOf(
                "ad" to "Ankara Boru Makarna 500g",
                "bazFiyat" to 18.0,
                "koliFiyati" to 360.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/angara.jpeg"
            ), mapOf(
                "ad" to "Ankara Kalem Makarna 500g",
                "bazFiyat" to 18.0,
                "koliFiyati" to 360.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/angara.jpeg"
            ), mapOf(
                "ad" to "Ankara Mantı Makarna 500g",
                "bazFiyat" to 22.0,
                "koliFiyati" to 420.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/angara.jpeg"
            ), mapOf(
                "ad" to "Ankara Fiyonk Makarna 500g",
                "bazFiyat" to 18.0,
                "koliFiyati" to 360.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/angara.jpeg"
            ), mapOf(
                "ad" to "Ankara İrmik 500g",
                "bazFiyat" to 25.0,
                "koliFiyati" to 480.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/angara.jpeg"
            ), mapOf(
                "ad" to "Ankara Yüksük Makarna 500g",
                "bazFiyat" to 18.0,
                "koliFiyati" to 360.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/angara.jpeg"
            ), mapOf(
                "ad" to "Ankara Kesme Makarna 500g",
                "bazFiyat" to 20.0,
                "koliFiyati" to 390.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/angara.jpeg"
            ), mapOf(
                "ad" to "Ankara Şehriye 500g",
                "bazFiyat" to 19.0,
                "koliFiyati" to 370.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/angara.jpeg"
            )
        )
        "Ankara" -> listOf(
            mapOf(
                "ad" to "sebzeli burgu 350 gr",
                "bazFiyat" to 31.5,
                "koliFiyati" to 630.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ansebz.jpeg"
            ),
            mapOf(
                "ad" to "nuh gemisi 350 gr",
                "bazFiyat" to 31.5,
                "koliFiyati" to 630.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anknuhgms.png"
            ),
            mapOf(
                "ad" to "tam kalem 500 gr",
                "bazFiyat" to 31.5,
                "koliFiyati" to 630.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/antbkalem.jpeg"
            ),
            mapOf(
                "ad" to "tam çubuk 500 gr",
                "bazFiyat" to 31.5,
                "koliFiyati" to 630.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/antbcbk.jpeg"
            ),
            mapOf(
                "ad" to "kepek çubuk 350 gr",
                "bazFiyat" to 31.5,
                "koliFiyati" to 630.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ankpklcbk.png"
            ),
            mapOf(
                "ad" to "kepek burgu 350 gr",
                "bazFiyat" to 31.5,
                "koliFiyati" to 630.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ankpklbur.jpeg"
            ),
            mapOf(
                "ad" to "arpa şehriye 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anarpa.jpeg"
            ),
            mapOf(
                "ad" to "tel şehriye 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/antel.jpeg"
            ),
            mapOf(
                "ad" to "yıldız şehriye 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anyldz.jpeg"
            ),
            mapOf(
                "ad" to "kuskus şehriye 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ankus.jpeg"
            ),
            mapOf(
                "ad" to "burgu makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anburgu.jpeg"
            ),
            mapOf(
                "ad" to "fiyonk makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anfynk.jpeg"
            ),
            mapOf(
                "ad" to "kelebek makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anklbk.jpeg"
            ),
            mapOf(
                "ad" to "kalem makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ankalem.jpeg"
            ),
            mapOf(
                "ad" to "boncuk makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anbnck.jpeg"
            ),
            mapOf(
                "ad" to "dirsek makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/andrsk.jpeg"
            ),
            mapOf(
                "ad" to "mantı makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anmant.jpeg"
            ),
            mapOf(
                "ad" to "erişte makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/aners111.jpeg"
            ),
            mapOf(
                "ad" to "midye makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anmdy.jpeg"
            ),
            mapOf(
                "ad" to "bamya makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anbmy.jpeg"
            ),
            mapOf(
                "ad" to "ince uzun makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anuzun.jpeg"
            ),
            mapOf(
                "ad" to "yüksük makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anyksk.jpeg"
            ),
            mapOf(
                "ad" to "fırın makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anfrn.jpeg"
            ),
            mapOf(
                "ad" to "bukle makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ANBUK.jpeg"
            ),
            mapOf(
                "ad" to "çubuk makarna 500 gr",
                "bazFiyat" to 26.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/anspgtt.jpeg"
            ),
            mapOf(
                "ad" to "catring yüksük 5 kg",
                "bazFiyat" to 255.0,
                "koliFiyati" to 510.0,
                "koliAdet" to 2,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ancyksk%20(2).jpeg"
            ),
            mapOf(
                "ad" to "catring burgu 5 kg",
                "bazFiyat" to 255.0,
                "koliFiyati" to 510.0,
                "koliAdet" to 2,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ancburgu.jpeg"
            ),
            mapOf(
                "ad" to "catring fiyonk 5 kg",
                "bazFiyat" to 255.0,
                "koliFiyati" to 510.0,
                "koliAdet" to 2,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ancfynk.jpeg"
            ),
            mapOf(
                "ad" to "catring çubuk 5 kg",
                "bazFiyat" to 255.0,
                "koliFiyati" to 510.0,
                "koliAdet" to 2,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ancspgt.jpeg"
            ),
            mapOf(
                "ad" to "catring arpa 5 kg",
                "bazFiyat" to 255.0,
                "koliFiyati" to 510.0,
                "koliAdet" to 2,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ancarp.jpeg"
            ),
            mapOf(
                "ad" to "irmik 500 gr",
                "bazFiyat" to 27.0,
                "koliFiyati" to 540.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ankrmk500.png"
            )
        )

        "Duru" -> listOf(
            mapOf(
                "ad" to "keten tohumu 8 gr*10",
                "bazFiyat" to 20.8,
                "koliFiyati" to 208.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRKETEN.jpeg"
            ), mapOf(
                "ad" to "çiya tohumu 8 gr*10",
                "bazFiyat" to 30.4,
                "koliFiyati" to 304.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRCHA.jpeg"
            ), mapOf(
                "ad" to "haşl. mısır 400 gr",
                "bazFiyat" to 55.2,
                "koliFiyati" to 441.6,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRHASM.jpeg"
            ), mapOf(
                "ad" to "haşl. nohut 400 gr",
                "bazFiyat" to 36.0,
                "koliFiyati" to 288.0,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRHASN.jpeg"
            ), mapOf(
                "ad" to "haşl. krmzı fasulye 400 gr",
                "bazFiyat" to 49.6,
                "koliFiyati" to 396.8,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRHASKF.jpeg"
            ), mapOf(
                "ad" to "haşl. fasülye 400 gr",
                "bazFiyat" to 36.0,
                "koliFiyati" to 288.0,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRHASFA.jpeg"
            ), mapOf(
                "ad" to "haşl. bezelye 450 gr",
                "bazFiyat" to 44.1,
                "koliFiyati" to 352.8,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRHASBEZ.jpeg"
            ), mapOf(
                "ad" to "haşl. barbunya 400 gr",
                "bazFiyat" to 45.6,
                "koliFiyati" to 364.8,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRHASBRB.jpeg"
            ), mapOf(
                "ad" to "haşl. börülce 400 gr",
                "bazFiyat" to 38.4,
                "koliFiyati" to 307.2,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRHASBRLCE.jpeg"
            ), mapOf(
                "ad" to "garnitür 400 gr",
                "bazFiyat" to 49.6,
                "koliFiyati" to 396.8,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRGARN.jpeg"
            ), mapOf(
                "ad" to "haşl. mısır 3*235 gr",
                "bazFiyat" to 105.75,
                "koliFiyati" to 634.5,
                "koliAdet" to 6,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DR3LU.jpeg"
            ), mapOf(
                "ad" to "haşl.mısır 2,5 kg",
                "bazFiyat" to 247.45,
                "koliFiyati" to 989.8,
                "koliAdet" to 4,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRHAS2.5.jpeg"
            ), mapOf(
                "ad" to "baldo pirinç 1 kg",
                "bazFiyat" to 136.0,
                "koliFiyati" to 1632.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRB1.jpeg"
            ), mapOf(
                "ad" to "osmancık pirinç 1 kg",
                "bazFiyat" to 124.0,
                "koliFiyati" to 1488.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/drosman1.png"
            ), mapOf(
                "ad" to "pilavlık pirinç 1 kg",
                "bazFiyat" to 96.0,
                "koliFiyati" to 1152.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DR1.jpeg"
            ), mapOf(
                "ad" to "kırık pirinç 1kg",
                "bazFiyat" to 54.0,
                "koliFiyati" to 648.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRKP.jpeg"
            ), mapOf(
                "ad" to "basmati pirinç 1 kg",
                "bazFiyat" to 174.0,
                "koliFiyati" to 2088.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRBASM.jpeg"
            ), mapOf(
                "ad" to "sefer kitel 1 kg",
                "bazFiyat" to 62.0,
                "koliFiyati" to 744.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/drsefer.jpeg"
            ), mapOf ("ad" to "iri pilavlık bulgur 1 kg",
                "bazFiyat" to 62.0,
                "koliFiyati" to 744.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRIRI.jpeg"
            ), mapOf(
                "ad" to "çiğ köftelik bulgur 1 kg",
                "bazFiyat" to 62.0,
                "koliFiyati" to 744.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/drckof1.png"
            ), mapOf(
                "ad" to "esmer çiğ k. bulgur 1 kg",
                "bazFiyat" to 62.0,
                "koliFiyati" to 744.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRESMER.jpeg"
            ), mapOf(
                "ad" to "köftelik bulgur 1 kg",
                "bazFiyat" to 62.0,
                "koliFiyati" to 744.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRKOF.jpeg"
            ), mapOf(
                "ad" to "başbaşı bulgur 1 kg",
                "bazFiyat" to 62.0,
                "koliFiyati" to 744.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRBASBAS.jpeg"
            ), mapOf(
                "ad" to "pilavlık bulgur 1 kg",
                "bazFiyat" to 62.0,
                "koliFiyati" to 744.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRBUL1.jpeg"
            ), mapOf(
                "ad" to "nohut 8 mm 1 kg",
                "bazFiyat" to 108.0,
                "koliFiyati" to 1296.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DR8MM.jpeg"
            ), mapOf(
                "ad" to "nohut 9 mm 1 kg",
                "bazFiyat" to 148.0,
                "koliFiyati" to 1776.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DR9MM.jpeg"
            ), mapOf(
                "ad" to "nohut 10 mm 1 kg",
                "bazFiyat" to 154.0,
                "koliFiyati" to 1848.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DR10MM.jpeg"
            ), mapOf(
                "ad" to "fasülye 7 mm 1 kg",
                "bazFiyat" to 128.0,
                "koliFiyati" to 1536.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DR7MMFAS.JPG"
            ), mapOf(
                "ad" to "fasülye dermason 1 kg",
                "bazFiyat" to 134.0,
                "koliFiyati" to 1608.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRDERMA.jpeg"
            ), mapOf(
                "ad" to "kırmızı mercimek 1 kg",
                "bazFiyat" to 135.34,
                "koliFiyati" to 1624.08,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRK1.jpeg"
            ), mapOf(
                "ad" to "yeşil mercimek 1 kg",
                "bazFiyat" to 125.0,
                "koliFiyati" to 1500.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRYES.jpeg"
            ), mapOf(
                "ad" to "barbunya 1 kg",
                "bazFiyat" to 180.0,
                "koliFiyati" to 2160.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRBARBNY.jpeg"
            ), mapOf(
                "ad" to "aşurelik buğday 1 kg",
                "bazFiyat" to 60.0,
                "koliFiyati" to 720.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRASURE.jpeg"
            ), mapOf(
                "ad" to "patlayan mısır 1 kg",
                "bazFiyat" to 66.0,
                "koliFiyati" to 792.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRPATL.jpeg"
            ), mapOf(
                "ad" to "kırmızı mercimek 2 kg",
                "bazFiyat" to 270.68,
                "koliFiyati" to 2165.44,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRK2.jpeg"
            ), mapOf(
                "ad" to "pilavlık bulgur 2 kg",
                "bazFiyat" to 125.24,
                "koliFiyati" to 1001.92,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRPBUL2.jpeg"
            ), mapOf(
                "ad" to "köftelik bulgur 2 kg",
                "bazFiyat" to 125.24,
                "koliFiyati" to 1001.92,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRKBUL2.jpeg"
            ), mapOf(
                "ad" to "iri bulgur 2 kg",
                "bazFiyat" to 125.24,
                "koliFiyati" to 1001.92,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRIRIPB2.jpeg"
            ), mapOf(
                "ad" to "çiğ köft. bulgur 2 kg",
                "bazFiyat" to 125.24,
                "koliFiyati" to 1001.92,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRCKOF2.jpeg"
            ), mapOf(
                "ad" to "pilavlık pirinç 2 kg",
                "bazFiyat" to 192.0,
                "koliFiyati" to 1536.0,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRPP2.jpeg"
            ), mapOf(
                "ad" to "osmancık pirinç 2 kg",
                "bazFiyat" to 248.0,
                "koliFiyati" to 1984.0,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DROS2.jpeg"
            ), mapOf(
                "ad" to "baldo pirinç 2 kg",
                "bazFiyat" to 272.0,
                "koliFiyati" to 2176.0,
                "koliAdet" to 8,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRB2.jpeg"
            ), mapOf(
                "ad" to "basmati pirinç 5 kg",
                "bazFiyat" to 845.0,
                "koliFiyati" to 3380.0,
                "koliAdet" to 4,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/drbsmt5.png"
            ), mapOf(
                "ad" to "pilavlık pirinç 5 kg",
                "bazFiyat" to 456.0,
                "koliFiyati" to 1824.0,
                "koliAdet" to 4,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRPPRNC5.jpeg"
            ), mapOf(
                "ad" to "osmancık pirinç 5 kg",
                "bazFiyat" to 589.0,
                "koliFiyati" to 2356.0,
                "koliAdet" to 4,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DROS5.jpeg"
            ), mapOf(
                "ad" to "baldo pirinç 5 kg",
                "bazFiyat" to 646.0,
                "koliFiyati" to 2584.0,
                "koliAdet" to 4,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRBALDO5.jpeg"
            ), mapOf(
                "ad" to "pilavlık bulgur 10 kg",
                "bazFiyat" to 451.17,
                "koliFiyati" to 451.17,
                "koliAdet" to 1,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRPB10.jpeg"
            ), mapOf(
                "ad" to "osmancık pirinç 25 kg",
                "bazFiyat" to 1868.5,
                "koliFiyati" to 1868.5,
                "koliAdet" to 1,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DROS25CUV.jpeg"
            ), mapOf(
                "ad" to "kırmızı mercimek 25 kg",
                "bazFiyat" to 1363.5,
                "koliFiyati" to 1363.5,
                "koliAdet" to 1,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRKM25.jpeg"
            ), mapOf(
                "ad" to "esmer çiğ k. bulgur 25 kg",
                "bazFiyat" to 839.56,
                "koliFiyati" to 839.56,
                "koliAdet" to 1,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/duruesmer.png"
            ), mapOf(
                "ad" to "pilavlık bulgur 25 kg",
                "bazFiyat" to 844.61,
                "koliFiyati" to 844.61,
                "koliAdet" to 1,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/DRBUL25.jpeg"
            )
        )

        "Akçay"

            -> listOf(
            mapOf(
                "ad" to "sıvı sabun inci 4 lt",
                "bazFiyat" to 110.0,
                "koliFiyati" to 440.0,
                "koliAdet" to 4,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/akcbssbn.jpeg"
            ), mapOf(
                "ad" to "sıvı sabun mavi 4 lt",
                "bazFiyat" to 110.0,
                "koliFiyati" to 440.0,
                "koliAdet" to 4,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/akcmsvsbn.jpeg"
            ), mapOf(
                "ad" to "sıvı sabun zeytin 4 lt",
                "bazFiyat" to 110.0,
                "koliFiyati" to 440.0,
                "koliAdet" to 4,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/akcysvsbn.jpeg"
            ), mapOf(
                "ad" to "sıvı sabun kiraz 4 lt",
                "bazFiyat" to 110.0,
                "koliFiyati" to 440.0,
                "koliAdet" to 4,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/akcpsvsbn.jpeg"
            ), mapOf(
                "ad" to "çamaşır suyu 4 lt",
                "bazFiyat" to 75.0,
                "koliFiyati" to 300.0,
                "koliAdet" to 4,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCCMSRSY.jpeg"
            ), mapOf(
                "ad" to "bulaşık 750",
                "bazFiyat" to 38.0,
                "koliFiyati" to 760.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCBUL750.jpeg"
            ), mapOf(
                "ad" to "bulaşık 4 kg",
                "bazFiyat" to 120.0,
                "koliFiyati" to 480.0,
                "koliAdet" to 4,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCBLS.jpeg"
            ), mapOf(
                "ad" to "sprey unutma b. 150 ml",
                "bazFiyat" to 70.0,
                "koliFiyati" to 1400.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCUNUTMAS.jpeg"
            ), mapOf(
                "ad" to "safran 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCSAF.jpeg"
            ), mapOf(
                "ad" to "misk 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCMISK.jpeg"
            ), mapOf(
                "ad" to "limon 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCLMN.jpeg"
            ), mapOf(
                "ad" to "tütün 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCTTN.jpeg"
            ), mapOf(
                "ad" to "zeytin 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCZEYT.jpeg"
            ), mapOf(
                "ad" to "unutmabeni 400 ml",
                "bazFiyat" to 140.0,
                "koliFiyati" to 3360.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCUNUT.jpeg"
            ), mapOf(
                "ad" to "İstanbul 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCISTAN.jpeg"
            ), mapOf(
                "ad" to "incir 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCINCIR.jpeg"
            ), mapOf(
                "ad" to "paris 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKC.jpeg"
            ), mapOf(
                "ad" to "yozgat 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCYOZGAT.jpeg"
            ), mapOf(
                "ad" to "zambak 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCZAMBAK.jpeg"
            ), mapOf(
                "ad" to "iğde 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCIGDE.jpeg"
            ), mapOf(
                "ad" to "gizemli 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/GZML.jpeg"
            ), mapOf(
                "ad" to "lavanta 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/akclvnt.png"
            ), mapOf(
                "ad" to "yıldız 400 ml",
                "bazFiyat" to 105.0,
                "koliFiyati" to 2520.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCYLDZ.jpeg"
            ), mapOf(
                "ad" to "tütün 900 ml",
                "bazFiyat" to 165.0,
                "koliFiyati" to 1980.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/22.jpeg"
            ), mapOf(
                "ad" to "limon 900 ml",
                "bazFiyat" to 165.0,
                "koliFiyati" to 1980.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/23.jpeg"
            ), mapOf(
                "ad" to "hasbahçe 500 ml",
                "bazFiyat" to 85.0,
                "koliFiyati" to 1700.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/akcygde.png"
            ), mapOf(
                "ad" to "limko 350 ml",
                "bazFiyat" to 25.0,
                "koliFiyati" to 500.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/AKCLMKO.png"
            ), mapOf(
                "ad" to "oda kokusu 100 ml",
                "bazFiyat" to 80.0,
                "koliFiyati" to 1920.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/refs/heads/main/ODA%20KOKUSU.png"
            )
        )

        "Kedi" -> listOf(
            mapOf(
                "ad" to "Pelagos Kedi Maması Tavuklu 15kg",
                "bazFiyat" to 650.0,
                "koliFiyati" to 3800.0,
                "koliAdet" to 1,
                "onResim" to "https://i.ibb.co/fGPFf918/kedii.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Kedi Maması Etli 15kg",
                "bazFiyat" to 670.0,
                "koliFiyati" to 3900.0,
                "koliAdet" to 1,
                "onResim" to "https://i.ibb.co/fGPFf918/kedii.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Kedi Maması Balıklı 15kg",
                "bazFiyat" to 690.0,
                "koliFiyati" to 4000.0,
                "koliAdet" to 1,
                "onResim" to "https://i.ibb.co/fGPFf918/kedii.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Kuru Kedi Maması Kuzu 10kg",
                "bazFiyat" to 480.0,
                "koliFiyati" to 2800.0,
                "koliAdet" to 1,
                "onResim" to "https://i.ibb.co/fGPFf918/kedii.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Yavru Kedi Maması 2kg",
                "bazFiyat" to 150.0,
                "koliFiyati" to 850.0,
                "koliAdet" to 6,
                "onResim" to "https://i.ibb.co/fGPFf918/kedii.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Konserve Somonlu 400g",
                "bazFiyat" to 48.0,
                "koliFiyati" to 550.0,
                "koliAdet" to 24,
                "onResim" to "https://i.ibb.co/fGPFf918/kedii.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Konserve Tavuklu 400g",
                "bazFiyat" to 45.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 24,
                "onResim" to "https://i.ibb.co/fGPFf918/kedii.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Ödül Maması 60g",
                "bazFiyat" to 30.0,
                "koliFiyati" to 350.0,
                "koliAdet" to 12,
                "onResim" to "https://i.ibb.co/fGPFf918/kedii.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Kedi Kumu Bentonit 10L",
                "bazFiyat" to 95.0,
                "koliFiyati" to 550.0,
                "koliAdet" to 1,
                "onResim" to "https://i.ibb.co/fGPFf918/kedii.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Kedi Kumu Sabun Kokulu 10L",
                "bazFiyat" to 105.0,
                "koliFiyati" to 600.0,
                "koliAdet" to 1,
                "onResim" to "https://i.ibb.co/fGPFf918/kedii.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            )
        )

        "Köpek" -> listOf(
            mapOf(
                "ad" to "Pelagos Köpek Maması Etli 15kg",
                "bazFiyat" to 590.0,
                "koliFiyati" to 3500.0,
                "koliAdet" to 1,
                "onResim" to "https://i.ibb.co/4g0hkWmv/dog.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Köpek Maması Kuzu Etli 15kg",
                "bazFiyat" to 620.0,
                "koliFiyati" to 3650.0,
                "koliAdet" to 1,
                "onResim" to "https://i.ibb.co/4g0hkWmv/dog.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Ekonomik Köpek Maması 25kg",
                "bazFiyat" to 850.0,
                "koliFiyati" to 4900.0,
                "koliAdet" to 1,
                "onResim" to "https://i.ibb.co/4g0hkWmv/dog.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Yavru Köpek Maması 15kg",
                "bazFiyat" to 680.0,
                "koliFiyati" to 3950.0,
                "koliAdet" to 1,
                "onResim" to "https://i.ibb.co/4g0hkWmv/dog.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Konserve Köpek Maması 800g",
                "bazFiyat" to 75.0,
                "koliFiyati" to 850.0,
                "koliAdet" to 12,
                "onResim" to "https://i.ibb.co/4g0hkWmv/dog.jpg",
                "barkodResim" to "https://i.ibb.co/B2KxX6wf/ece.jpg"
            ), mapOf(
                "ad" to "Pelagos Köpek Ödül Çubuğu 5li",
                "bazFiyat" to 40.0,
                "koliFiyati" to 450.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Büyük Irk Köpek Maması 15kg",
                "bazFiyat" to 710.0,
                "koliFiyati" to 4100.0,
                "koliAdet" to 1,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Küçük Irk Köpek Maması 3kg",
                "bazFiyat" to 210.0,
                "koliFiyati" to 1200.0,
                "koliAdet" to 4,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Köpek Şampuanı 400ml",
                "bazFiyat" to 85.0,
                "koliFiyati" to 950.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Çiğneme Kemiği M Boy",
                "bazFiyat" to 50.0,
                "koliFiyati" to 580.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            )
        )

        "Kuş" -> listOf(
            mapOf(
                "ad" to "Pelagos Deluxe Kuş Yemi 1kg",
                "bazFiyat" to 45.0,
                "koliFiyati" to 520.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Muhabbet Kuşu Yemi 500g",
                "bazFiyat" to 25.0,
                "koliFiyati" to 280.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Papağan Yemi 1kg",
                "bazFiyat" to 65.0,
                "koliFiyati" to 750.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Kanarya Yemi 500g",
                "bazFiyat" to 35.0,
                "koliFiyati" to 400.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Kuş Krakeri Ballı",
                "bazFiyat" to 20.0,
                "koliFiyati" to 220.0,
                "koliAdet" to 24,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Kuş Vitamini 30ml",
                "bazFiyat" to 55.0,
                "koliFiyati" to 600.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Gagataşı Büyük",
                "bazFiyat" to 15.0,
                "koliFiyati" to 160.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Kumlu Kuş Yemi 1kg",
                "bazFiyat" to 48.0,
                "koliFiyati" to 550.0,
                "koliAdet" to 12,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Papağan Krakeri",
                "bazFiyat" to 30.0,
                "koliFiyati" to 340.0,
                "koliAdet" to 20,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "Pelagos Kuş Banyoluğu Aksesuar",
                "bazFiyat" to 45.0,
                "koliFiyati" to 500.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            )
        )

        else -> listOf(
            mapOf(
                "ad" to "$brandName Ürün 1",
                "bazFiyat" to 100.0,
                "koliFiyati" to 1100.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "$brandName Ürün 2",
                "bazFiyat" to 120.0,
                "koliFiyati" to 1300.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "$brandName Ürün 3",
                "bazFiyat" to 130.0,
                "koliFiyati" to 1400.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "$brandName Ürün 4",
                "bazFiyat" to 140.0,
                "koliFiyati" to 1500.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "$brandName Ürün 5",
                "bazFiyat" to 150.0,
                "koliFiyati" to 1600.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "$brandName Ürün 6",
                "bazFiyat" to 160.0,
                "koliFiyati" to 1700.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "$brandName Ürün 7",
                "bazFiyat" to 170.0,
                "koliFiyati" to 1800.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "$brandName Ürün 8",
                "bazFiyat" to 180.0,
                "koliFiyati" to 1900.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "$brandName Ürün 9",
                "bazFiyat" to 190.0,
                "koliFiyati" to 2000.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            ), mapOf(
                "ad" to "$brandName Ürün 10",
                "bazFiyat" to 200.0,
                "koliFiyati" to 2100.0,
                "koliAdet" to 10,
                "onResim" to "https://raw.githubusercontent.com/SORGUN-CALISKANOGULLARI/sorgun-toptan/6c805a38d5a5734efffd45dcd9b888cb6db1f299/ekici.png"
            )
        )
    }

    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBackClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B)),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(text = "← Geri", color = Color.White, fontSize = 14.sp)
                }

                Button(
                    onClick = onCartClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "🛒 Sepet ($cartCount)",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = brandName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            val chunkedList = productList.chunked(2)
            items(chunkedList, key = { row -> row.hashCode() }) { rowProducts ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (product in rowProducts) {
                        var discountInput by remember { mutableStateOf("") }
                        var customPriceInput by remember { mutableStateOf("") }
                        var quantityInput by remember { mutableStateOf("1") }
                        var isKoli by remember { mutableStateOf(false) }
                        var showBarcode by remember { mutableStateOf(false) }

                        val bazFiyat = product["bazFiyat"] as Double
                        val koliFiyati = product["koliFiyati"] as Double
                        val koliAdet = product["koliAdet"] as? Int ?: 1

                        val iskontoOrani = if (userRole == "admin") {
                            discountInput.toDoubleOrNull() ?: 0.0
                        } else {
                            0.0
                        }

                        val netBazFiyat = bazFiyat - (bazFiyat * iskontoOrani / 100.0)
                        val netKoliFiyati = koliFiyati - (koliFiyati * iskontoOrani / 100.0)

                        // 1. Admin tarafından elle girilen ham özel fiyat (varsa)
                        val girilenOzelFiyatHam =
                            if (userRole == "admin") customPriceInput.toDoubleOrNull() else null

                        // 2. Elle girilen özel fiyatın üzerine de iskontonun uygulanması:
                        val girilenOzelFiyatNet = if (girilenOzelFiyatHam != null) {
                            girilenOzelFiyatHam - (girilenOzelFiyatHam * iskontoOrani / 100.0)
                        } else {
                            null
                        }

                        val unitTypeName = if (isKoli) "Koli" else "Adet"

                        val finalUnitPrice = if (girilenOzelFiyatNet != null) {
                            if (isKoli) girilenOzelFiyatNet * koliAdet else girilenOzelFiyatNet
                        } else {
                            if (isKoli) netKoliFiyati else netBazFiyat
                        }

                        val adet = quantityInput.toIntOrNull() ?: 1
                        val hasBarcodeImage = product.containsKey("barkodResim")

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .wrapContentHeight(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clickable {
                                            if (hasBarcodeImage) {
                                                showBarcode = !showBarcode
                                            }
                                        }, contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = if (showBarcode && hasBarcodeImage) product["barkodResim"] else product["onResim"],
                                        contentDescription = product["ad"] as String,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Fit
                                    )

                                    if (hasBarcodeImage) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .background(
                                                    Color(0xAA000000),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (showBarcode) "Barkod" else "Ürün",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = product["ad"] as String,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.Black,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )

                                Text(
                                    text = "📦 Koli İçi: $koliAdet Adet",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00796B)
                                )

                                when (userRole) {
                                    "admin" -> {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(0.45f)
                                                        .height(34.dp)
                                                        .border(
                                                            1.dp,
                                                            Color.Gray,
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 4.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (discountInput.isEmpty()) {
                                                        Text(
                                                            "İsk%",
                                                            color = Color.Gray,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    BasicTextField(
                                                        value = discountInput,
                                                        onValueChange = { discountInput = it },
                                                        singleLine = true,
                                                        textStyle = TextStyle(
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Black
                                                        ),
                                                        keyboardOptions = KeyboardOptions(
                                                            keyboardType = KeyboardType.Number
                                                        ),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(0.55f)
                                                        .height(34.dp)
                                                        .border(
                                                            1.dp,
                                                            Color(0xFFCE93D8),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .background(
                                                            Color(0xFFF3E5F5),
                                                            shape = RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 4.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (customPriceInput.isEmpty()) {
                                                        val gostergeFiyat =
                                                            if (isKoli) netKoliFiyati else netBazFiyat
                                                        val fiyatBaslik =
                                                            if (isKoli) "Koli" else "Adet"
                                                        Text(
                                                            "$fiyatBaslik: ${
                                                                String.format(
                                                                    "%.1f", gostergeFiyat
                                                                )
                                                            }₺",
                                                            color = Color.DarkGray,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    BasicTextField(
                                                        value = customPriceInput,
                                                        onValueChange = { customPriceInput = it },
                                                        singleLine = true,
                                                        textStyle = TextStyle(
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Black
                                                        ),
                                                        keyboardOptions = KeyboardOptions(
                                                            keyboardType = KeyboardType.Number
                                                        ),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }

                                            if (isKoli && customPriceInput.isNotEmpty()) {
                                                val girilenAdetFiyati =
                                                    customPriceInput.toDoubleOrNull() ?: 0.0
                                                val koliToplam = girilenAdetFiyati * koliAdet
                                                Text(
                                                    text = "📦 Koli Toplam: ${
                                                        String.format(
                                                            "%.1f", koliToplam
                                                        )
                                                    }₺",
                                                    color = Color(0xFF7B1FA2),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(
                                                        start = 2.dp, top = 2.dp
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    "dealer" -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(34.dp)
                                                .background(
                                                    Color(0xFFE8F5E9),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    Color(0xFF81C784),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val fiyatBaslik = if (isKoli) "Koli" else "Adet"
                                            val gostergeFiyat =
                                                if (isKoli) netKoliFiyati else netBazFiyat
                                            Text(
                                                text = "$fiyatBaslik: ${
                                                    String.format(
                                                        "%.1f", gostergeFiyat
                                                    )
                                                }₺",
                                                fontSize = 11.sp,
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    else -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(34.dp)
                                                .background(
                                                    Color(0xFFE0E0E0),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .padding(4.dp), contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Fiyat Gizli",
                                                fontSize = 12.sp,
                                                color = Color.DarkGray,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { isKoli = !isKoli },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isKoli) Color(
                                                0xFF7E57C2
                                            ) else Color(0xFF90CAF9)
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = 2.dp, vertical = 0.dp
                                        ),
                                        modifier = Modifier
                                            .weight(0.32f)
                                            .height(34.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isKoli) "📦 Koli" else "📄 Adet",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(0.26f)
                                            .height(34.dp)
                                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (quantityInput.isEmpty()) {
                                            Text("Mik", color = Color.Gray, fontSize = 11.sp)
                                        }
                                        BasicTextField(
                                            value = quantityInput,
                                            onValueChange = { quantityInput = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            onAddToCart(
                                                CartItem(
                                                    productName = product["ad"] as String,
                                                    unitType = unitTypeName,
                                                    quantity = adet,
                                                    unitPrice = finalUnitPrice,
                                                    adetFiyat = bazFiyat,
                                                    koliFiyat = koliFiyati,
                                                    discountPercent = iskontoOrani
                                                )
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(
                                                0xFF80CBC4
                                            )
                                        ),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .weight(0.42f)
                                            .height(34.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "➕ Ekle",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Alttaki anlık genel toplam yazısı (Koli modunda "Genel Toplam", Adet modunda "Toplam" yazar)
                                if (userRole != "guest" && quantityInput.isNotEmpty()) {
                                    val anlikToplamTutar = finalUnitPrice * adet
                                    val etiketBaslik = if (isKoli) "Genel Toplam" else "Toplam"

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 2.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = "$etiketBaslik: ${
                                                String.format(
                                                    "%.1f", anlikToplamTutar
                                                )
                                            }₺",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isKoli) Color(0xFF7B1FA2) else Color(
                                                0xFF2E7D32
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (rowProducts.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(250.dp))
            }
        }
    }
}