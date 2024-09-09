package com.dipta.bdrx


import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.opencsv.CSVReaderBuilder
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.android.gms.location.FusedLocationProviderClient
import android.util.Log
import android.location.Location
import android.location.LocationManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApp()
        }
    }
}



@Composable
fun MyApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val products = remember { loadProducts(context) }
    val order = remember { mutableStateOf(mutableMapOf<String, Int>()) }
    val userProfile = remember { mutableStateOf<UserProfile?>(null) } // Track user profile

    fun calculateTotalPrice(): Double {
        return calculateTotalPrice(products, order.value)
    }

    NavHost(navController, startDestination = "home") {
        composable("home") { HomePage(navController) }
        composable("gosearch") { SearchPage(navController, order) }
        composable("currentOrder") {
            CurrentOrderPage(navController = navController, products = products, order = order)
        }
        composable("pastOrder") { PastOrderPage() }
        composable("deliveryAddress") {
            DeliveryAddressPage { name, address, phoneNumber ->
                userProfile.value = UserProfile(name, address, phoneNumber)
                navController.navigate("checkOut")
            }
        }
        composable("checkOut") {
            userProfile.value?.let { userProfile ->
                CheckoutPage(
                    navController = navController,
                    name = userProfile.name,
                    address = userProfile.address,
                    phoneNumber = userProfile.phoneNumber,
                    totalPrice = calculateTotalPrice(products, order.value)
                )
            } ?: navController.popBackStack()
        }
    }
}

data class UserProfile(
    val name: String,
    val address: String,
    val phoneNumber: String
)


class OrderViewModel : ViewModel() {
    val products = mutableStateOf<List<Product>>(emptyList())
    val order = mutableStateOf<MutableMap<String, Int>>(mutableMapOf())
}

data class Product(
    val id: String,
    val name: String,
    val brand: String,
    val group: String,
    val description: String,
    val imageUrl: String,
    val price: Double,
)


@Composable
fun HomePage(navController: NavHostController) {
    // State for managing image selection
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmapImage by remember { mutableStateOf<Bitmap?>(null) }

    // Activity result launchers for image selection and capture
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        imageUri = uri
        uri?.let {
            uploadImageToFirebase(context, it)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmapImage = bitmap
        bitmap?.let {
            val imageUri = saveBitmapToGallery(context, it)
            uploadImageToFirebase(context, imageUri)
        }
    }

    // Navigation buttons
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Content area with your existing functionality
        // Replace with your existing buttons and image display
        Button(
            onClick = { navController.navigate("gosearch") },
            modifier = Modifier.padding(8.dp)
        ) {
            Text("ওষুধ সার্চ")
        }
        Button(
            onClick = {
                showImageDialog(context, pickImageLauncher, takePictureLauncher)
            },
            modifier = Modifier.padding(8.dp)
        ) {
            Text("প্রেসক্রিপশন এর ছবি")
        }

        Spacer(modifier = Modifier.height(116.dp))

        // Display selected image if any
        imageUri?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = null,
                modifier = Modifier.size(200.dp)
            )
        }

        bitmapImage?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(200.dp)
            )
        }

    Spacer(modifier = Modifier.height(16.dp))
    // Bottom tabs area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            TabButton(
                text = "সার্চ",
                icon = Icons.Filled.Search,
                onClick = { navController.navigate("gosearch") }
            )
            TabButton(
                text = "বর্তমান অর্ডার",
                icon = Icons.Filled.ShoppingCart,
                onClick = { navController.navigate("currentOrder") }
            )
            TabButton(
                text = "আগের অর্ডার",
                icon = Icons.Filled.CheckCircle,
                onClick = { navController.navigate("pastOrder") }
            )
       }
    }
}

@Composable
fun TabButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
@Composable
fun SearchPage(navController: NavHostController, order: MutableState<MutableMap<String, Int>>) {
    val context = LocalContext.current
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val products = remember { loadProducts(context) }
    var filteredProducts by remember { mutableStateOf(products) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(filteredProducts) { product ->
                ProductItemComposable(product, order)
            }
        }
        TextField(
            value = query,
            onValueChange = {
                query = it
                filteredProducts = if (query.text.isEmpty()) {
                    products.take(5)
                } else {
                    products.filter { product ->
                        product.name.contains(query.text, ignoreCase = true)
                    }.take(5)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text("Search products") }
        )
        Button(
            onClick = {
                navController.navigate("currentOrder")
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
        ) {
            Text("Continue")
        }
    }
}




@Composable
fun ProductItemComposable(product: Product, order: MutableState<MutableMap<String, Int>>) {
    var quantity by remember { mutableStateOf(order.value[product.id] ?: 0) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(text = product.name, style = MaterialTheme.typography.bodyMedium)
            Text(text = "${product.brand}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "${product.group}", style = MaterialTheme.typography.bodySmall)
            Text(text = "${product.description}", style = MaterialTheme.typography.bodySmall)
        }

        Text(
            text = "${product.price}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp)
        )

        Spacer(modifier = Modifier.weight(1f))  // Pushes the buttons to the right

        AsyncImage(
            model = product.imageUrl,
            contentDescription = null,
            modifier = Modifier.size(64.dp).padding(end = 8.dp),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.placeholder)
        )

        if (quantity > 0) {
            IconButton(
                onClick = {
                    if (quantity > 0) {
                        quantity--
                        if (quantity > 0) {
                            order.value[product.id] = quantity
                        } else {
                            order.value.remove(product.id)
                        }
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
            Text(
                text = quantity.toString(),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        IconButton(
            onClick = {
                quantity++
                order.value[product.id] = quantity
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.Filled.AddCircle, contentDescription = "Add")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
}





fun loadProducts(context: Context): List<Product> {
    val products = mutableListOf<Product>()
    val assetManager = context.assets

    assetManager.open("products.csv").use { inputStream ->
        InputStreamReader(inputStream).use { reader ->
            CSVReaderBuilder(reader).build().use { csvReader ->
                var record: Array<String>?
                csvReader.skip(1) // Skip header

                while (csvReader.readNext().also { record = it } != null) {
                    // Assuming record has at least 7 columns
                    val id = record!![0]
                    val name = record!![1]
                    val brand = record!![2]
                    val group = record!![3]
                    val description = record!![4]
                    val imageUrl = record!![5]
                    val priceStr = record!![6]

                    // Parse price to Double
                    val price: Double = try {
                        priceStr.toDouble()
                    } catch (e: NumberFormatException) {
                        // Handle the case where price is not a valid number
                        0.0 // Or any default value you prefer
                    }

                    val product = Product(id, name, brand, group, description, imageUrl, price)
                    products.add(product)
                }
            }
        }
    }
    return products
}




@Composable
fun ProductItem(product: Product, order: MutableState<MutableMap<String, Int>>) {
    var quantity by remember { mutableStateOf(order.value[product.id] ?: 0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(text = product.name)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = {
                if (quantity > 0) {
                    quantity--
                    order.value[product.id] = quantity
                }
            }) {
                Text("-")
            }
            Text(text = quantity.toString())
            Button(onClick = {
                quantity++
                order.value[product.id] = quantity
            }) {
                Text("+")
            }
        }
    }
}
@Composable
fun CurrentOrderPage(
    navController: NavHostController,
    products: List<Product>,
    order: MutableState<MutableMap<String, Int>>
) {
    var showCheaperOptions by remember { mutableStateOf(false) }
    var selectedBrand by remember { mutableStateOf("") }
    var filteredProducts by remember { mutableStateOf(products) }
    val displayedProducts by remember { derivedStateOf {
        if (showCheaperOptions) {
            findCheaperOptions(products, order.value)
        } else {
            products.filter { it.id in order.value.keys }
        }
    }}

    // Calculate total price
    val totalPrice = remember {
        var total = 0.0
        for ((productId, quantity) in order.value) {
            val product = products.find { it.id == productId }
            if (product != null) {
                total += product.price * quantity
            }
        }
        total
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("বর্তমান অর্ডার", style = MaterialTheme.typography.headlineSmall)
        Text(
            "মোট মূল্য: $totalPrice",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Display the list of selected products
        LazyColumn {
            items(displayedProducts) { product ->
                ProductItemComposable(product, order)
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    // Toggle showing cheaper options
                    showCheaperOptions = !showCheaperOptions
                },
                modifier = Modifier.padding(8.dp)
            ) {
                Text(if (showCheaperOptions) "বর্তমান ওষুধ" else "সাশ্রয়ী বিকল্প")
            }

            DropdownButton(
                items = getBrandDropdownItems(products, order.value),
                selectedItem = selectedBrand,
                onItemSelected = { brand ->
                    selectedBrand = brand
                    filteredProducts = filterProductsByBrand(products, brand, order.value)
                },
                modifier = Modifier.padding(8.dp)
            )
        }

        Button(
            onClick = {
                // Navigate to the DeliveryAddressPage
                navController.navigate("deliveryAddress")
            },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp)
        ) {
            Text("ডেলিভারি এড্রেস")
        }
    }
}



private fun findCheaperOptions(products: List<Product>, order: Map<String, Int>): List<Product> {
    val cheaperProducts = mutableListOf<Product>()
    val productGroups = products.groupBy { it.group }

    order.keys.forEach { productId ->
        val product = products.find { it.id == productId }
        product?.let {
            val groupProducts = productGroups[product.group] ?: emptyList()
            val cheapestProduct = groupProducts.minByOrNull { it.price }
            if (cheapestProduct != null && cheapestProduct.price < product.price) {
                cheaperProducts.add(cheapestProduct)
            } else {
                cheaperProducts.add(product)
            }
        }
    }

    return cheaperProducts
}



private fun getBrandDropdownItems(products: List<Product>, order: Map<String, Int>): List<String> {
    // Get the groups of the products in the current order
    val orderGroups = products.filter { order.containsKey(it.id) }.map { it.group }.distinct()

    // Extract unique brands relevant to the groups in the current order
    val brands = products.filter { it.group in orderGroups }.map { it.brand }.distinct()

    return listOf("All") + brands  // Include "All" option as well
}


private fun filterProductsByBrand(products: List<Product>, brand: String, order: Map<String, Int>): List<Product> {
    if (brand == "All") {
        return products.filter { order.containsKey(it.id) }
    } else {
        return products.filter { it.brand == brand && order.containsKey(it.id) }
    }
}



private fun loadCurrentOrder(context: Context): Map<Product, Int> {
    // Load order from storage (e.g., SharedPreferences)
    // Placeholder implementation:
    return mapOf()
}

@Composable
fun DeliveryAddressPage(onAddressSaved: (name: String, address: String, phoneNumber: String) -> Unit
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val mapView = rememberMapViewWithLifecycle()

    // Permission request launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, handle location logic
            handleLocationPermissionGranted(fusedLocationClient, mapView, context)
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to handle location permission logic
    fun handleLocationPermissionGranted(fusedLocationClient: FusedLocationProviderClient) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    showCurrentLocationOnMap(context, mapView, location)
                } else {
                    Toast.makeText(context, "Last known location is null", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Failed to get location: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Check if location permission is granted
    val permission = Manifest.permission.ACCESS_FINE_LOCATION
    val isPermissionGranted = remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    ) }

    // Check if GPS is enabled
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    // Request permission and handle GPS enablement
    LaunchedEffect(Unit) {
        if (!isPermissionGranted.value) {
            requestPermissionLauncher.launch(permission)
        } else if (!isGPSEnabled) {
            Toast.makeText(context, "Please enable GPS for accurate location", Toast.LENGTH_SHORT).show()
            val enableGPSIntent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            enableGPSIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(enableGPSIntent)
        } else {
            // Permission granted and GPS enabled, fetch location
            handleLocationPermissionGranted(fusedLocationClient)
        }
    }

    // UI for Delivery Address page
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Add Address for Delivery", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AndroidView({ mapView }) { mapView ->
                // Map initialization and handling
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        var name by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var phoneNumber by remember { mutableStateOf("") }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("নাম") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("এড্রেস") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("ফোন নম্বর") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                // Validate inputs
                if (name.isNotBlank() && address.isNotBlank() && phoneNumber.isNotBlank()) {
                    // Save address and navigate to checkout page
                    onAddressSaved(name, address, phoneNumber)
                } else {
                    Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Continue")
        }
    }
}

// Function to show current location on the map
private fun showCurrentLocationOnMap(mapView: MapView, location: Location, context: Context) {
    val currentLatLng = LatLng(location.latitude, location.longitude)
    mapView.getMapAsync { googleMap ->
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                googleMap.isMyLocationEnabled = true
                googleMap.clear()
                googleMap.addMarker(MarkerOptions().position(currentLatLng).title("Deliver Here"))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
            } catch (e: SecurityException) {
                // Handle exception gracefully
                e.printStackTrace()
            }
        } else {
            // Handle case where permission is not granted
            Toast.makeText(context, "Location permission is needed to show the map", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun handleLocationPermissionGranted(
    fusedLocationClient: FusedLocationProviderClient,
    mapView: MapView,
    context: Context  // Add context parameter to check permissions
) {
    // Check if the app has the necessary permission before proceeding
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        // Permission is granted, proceed to get the last known location
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                showCurrentLocationOnMap(context, mapView, location)
            } else {
                // Handle case where location is null
                Log.w("Location", "Last known location is null")
            }
        }.addOnFailureListener { e ->
            // Handle failure to get location
            Log.e("Location", "Error getting location", e)
            // Consider displaying a Snackbar or Toast to inform the user
        }
    } else {
        // Permission is not granted
        Log.e("Location", "Permission denied")
        // Consider requesting the permission from the user
        // You can show a dialog or request the permission here
    }
}



private fun showCurrentLocationOnMap(context: Context, mapView: MapView, location: Location) {
    val currentLatLng = LatLng(location.latitude, location.longitude)
    mapView.getMapAsync { googleMap ->
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                googleMap.isMyLocationEnabled = true
            }
            googleMap.clear()
            googleMap.addMarker(MarkerOptions().position(currentLatLng).title("Deliver Here"))
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
        } catch (e: SecurityException) {
            // Handle the exception, e.g., show a message to the user
            Toast.makeText(context, "Location permission is needed to show the current location", Toast.LENGTH_SHORT).show()
        }
    }
}


@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context)
    }
    DisposableEffect(Unit) {
        mapView.onCreate(null)
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDestroy()
        }
    }
    return mapView
}

private fun showImageDialog(
    context: Context,
    pickImageLauncher: ManagedActivityResultLauncher<String, Uri?>,
    takePictureLauncher: ManagedActivityResultLauncher<Void?, Bitmap?>
) {
    val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")

    AlertDialog.Builder(context)
        .setTitle("Select Image")
        .setItems(options) { dialog, which ->
            when (which) {
                0 -> {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(
                            context as Activity,
                            arrayOf(Manifest.permission.CAMERA),
                            1001 // Arbitrary request code
                        )
                    } else {
                        takePictureLauncher.launch(null)
                    }
                }
                1 -> pickImageLauncher.launch("image/*")
                2 -> dialog.dismiss()
            }
        }
        .show()
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BDRX")
    }

    val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        val outputStream: OutputStream? = context.contentResolver.openOutputStream(it)
        outputStream?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        }
    }
    return uri ?: Uri.EMPTY
}

private fun uploadImageToFirebase(context: Context, fileUri: Uri) {
    val storage = Firebase.storage
    val storageRef = storage.reference

    // Generate a unique filename based on current date and time
    val currentDateTime = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "image_$currentDateTime.jpg"

    // Create a reference to 'images/<fileName>'
    val imagesRef = storageRef.child("images/$fileName")

    val uploadTask = imagesRef.putFile(fileUri)

    uploadTask.addOnSuccessListener {
        Toast.makeText(context, "আপনার প্রেসক্রিপশন আপলোড করা হয়েছে", Toast.LENGTH_SHORT).show()
    }.addOnFailureListener { exception ->
        Toast.makeText(context, "Image Upload Failed: ${exception.message}", Toast.LENGTH_SHORT).show()
        exception.printStackTrace()  // Print the stack trace for more information
    }
}


@Composable
fun PastOrderPage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Past Order Page")
    }
}

@Composable
fun CheckoutPage(
    navController: NavHostController,
    name: String,
    phoneNumber: String,
    address: String,
    totalPrice: Double
) {
    var editedName by remember { mutableStateOf(name) }
    var editedPhoneNumber by remember { mutableStateOf(phoneNumber) }
    var editedAddress by remember { mutableStateOf(address) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text("Delivery Address", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Name: $editedName")
                Text("Phone Number: $editedPhoneNumber")
                Text("Address: $editedAddress")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    // Navigate back to DeliveryAddressPage for editing
                    navController.navigate("deliveryAddress")
                }) {
                    Text("Edit Address")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Order Summary", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Total Price: $totalPrice",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Button(
            onClick = {
                // Perform checkout logic
                // For example, navigate to a confirmation page
                navController.navigate("confirmation")
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Proceed to Payment")
        }
    }
}


@Composable
fun DropdownButton(
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf(selectedItem) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = selectedText.ifEmpty { "Select a Brand" })
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { label ->
                DropdownMenuItem(
                    text = { Text(text = label) },
                    onClick = {
                        selectedText = label
                        onItemSelected(label)
                        expanded = false
                    }
                )
            }
        }
    }
}

// Placeholder function, adjust as per your actual logic
fun calculateTotalPrice(products: List<Product>, order: Map<String, Int>): Double {
    var totalPrice = 0.0 // Initialize totalPrice as a Double

    for ((productId, quantity) in order) {
        val product = products.find { it.id == productId }
        product?.let {
            totalPrice += it.price * quantity // Accumulate the total price
        }
    }

    return totalPrice
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    {
    }
}