package com.arduinoworld.smartdoorbell

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.arduinoworld.smartdoorbell.databinding.ActivityMainBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ListResult
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    companion object {
        lateinit var firebaseAuth: FirebaseAuth
        lateinit var sharedPreferences: SharedPreferences
        lateinit var editPreferences: SharedPreferences.Editor
        lateinit var vibrator: Vibrator
        var isHapticFeedbackEnabled = true
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var fileOutputStream: FileOutputStream
    private lateinit var menuItem: Menu
    private lateinit var imageRecyclerAdapter: ImageRecyclerAdapter
    private var deleteImageRequest = false
    private var downloadImageRequest = false
    private var isUserLogged = false
    private val imagesList = ArrayList<Image>()

    @SuppressLint("CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarMainActivity)
        supportActionBar!!.title = "Главная"

        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        firebaseAuth = FirebaseAuth.getInstance()
        val realtimeDatabase = FirebaseDatabase.getInstance().reference

        sharedPreferences = getSharedPreferences("SharedPreferences", MODE_PRIVATE)
        editPreferences = sharedPreferences.edit()

        isHapticFeedbackEnabled = sharedPreferences.getBoolean("HapticFeedback", true)
        isUserLogged = sharedPreferences.getBoolean("UserLogged", false)

        if (isUserLogged) {
            binding.buttonOpenUserProfile.visibility = View.GONE
            binding.buttonOr.visibility = View.GONE
            binding.buttonOpenSignUp.visibility = View.GONE
            binding.linearLayout.visibility = View.GONE
            binding.progressBarImages.visibility = View.VISIBLE

            val storage = Firebase.storage

            val listAllTask : Task<ListResult> = storage.reference.child(firebaseAuth.currentUser!!.uid).listAll()
            listAllTask.addOnCompleteListener { result ->
                binding.fabSendImage.visibility = View.VISIBLE
                val items: List<StorageReference> = result.result.items

                if (items.isNotEmpty()) {
                    if (items.size > sharedPreferences.getInt("MaxImages", 5) && sharedPreferences.getBoolean("DeleteImagesMode", false)) {
                        items.forEach { item ->
                            item.delete().addOnCompleteListener { deleteImageTask ->
                                if (!deleteImageTask.isSuccessful) {
                                    Toast.makeText(baseContext, "Не удалось удалить фото ${item.name}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        Toast.makeText(baseContext, "Старые фото удалены!", Toast.LENGTH_SHORT).show()
                        binding.linearLayout.visibility = View.VISIBLE
                        binding.textViewNoImagesReceived.visibility = View.VISIBLE
                        binding.progressBarImages.visibility = View.GONE
                    } else {
                        val imageUrlsList = ArrayList<String>()
                        val imageNamesList = ArrayList<String>()
                        val imageDatesList = ArrayList<Date>()

                        val calendar = Calendar.getInstance()
                        val simpleDateFormat = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.US)
                        items.forEach { item ->
                            item.downloadUrl.addOnCompleteListener { downloadUrlTask ->
                                if (downloadUrlTask.isSuccessful) {
                                    val imageName = item.name.replace(".jpg", "")
                                    imageUrlsList.add(downloadUrlTask.result.toString())
                                    imageNamesList.add(imageName)
                                    val date = simpleDateFormat.parse(imageName)
                                    if (date != null) imageDatesList.add(date)

                                    if (items.size == imageUrlsList.size) {
                                        imageDatesList.sortDescending()
                                        imageDatesList.forEach {
                                            calendar.time = it
                                            var hours = calendar.get(Calendar.HOUR_OF_DAY).toString()
                                            var minutes = calendar.get(Calendar.MINUTE).toString()
                                            var days = calendar.get(Calendar.DAY_OF_MONTH).toString()
                                            var month = (calendar.get(Calendar.MONTH) + 1).toString()

                                            if (hours.toInt() < 10) hours = "0$hours"
                                            if (minutes.toInt() < 10) minutes = "0$minutes"
                                            if (days.toInt() < 10) days = "0$days"
                                            if (month.toInt() < 10) month = "0$month"

                                            val index = imageNamesList.indexOf("$hours:$minutes $days.$month.${calendar.get(Calendar.YEAR)}")
                                            imagesList.add(Image(imageUrlsList[index], imageNamesList[index]))
                                        }

                                        if (items.size > sharedPreferences.getInt("MaxImages", 5) && !sharedPreferences.getBoolean("DeleteImagesMode", false)) {
                                            storage.getReferenceFromUrl(imagesList[imagesList.size - 1].imageUrl).delete()
                                                    .addOnCompleteListener { deleteImageTask ->
                                                        if (deleteImageTask.isSuccessful) {
                                                            imagesList.removeAt(imagesList.size - 1)
                                                        } else {
                                                            Toast.makeText(baseContext, "Не удалось удалить фото ${imagesList[imagesList.size - 1].imageName}!", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                        }

                                        imageRecyclerAdapter = ImageRecyclerAdapter(imagesList)
                                        imageRecyclerAdapter.setOnItemClickListener(imageRecyclerAdapterClickListener)
                                        binding.imagesRecyclerView.apply {
                                            adapter = imageRecyclerAdapter
                                            layoutManager = LinearLayoutManager(this@MainActivity)
                                        }
                                        binding.linearLayout.visibility = View.VISIBLE
                                        binding.imagesRecyclerView.visibility = View.VISIBLE
                                        binding.fabDeleteImage.visibility = View.VISIBLE
                                        binding.fabDownloadImage.visibility = View.VISIBLE
                                        binding.progressBarImages.visibility = View.GONE
                                    }
                                }
                            }
                        }
                    }
                } else {
                    binding.linearLayout.visibility = View.VISIBLE
                    binding.textViewNoImagesReceived.visibility = View.VISIBLE
                    binding.progressBarImages.visibility = View.GONE
                }

                binding.fabDeleteImage.setOnClickListener {
                    vibrate()
                    deleteImageRequest = true
                    supportActionBar!!.title = "Удаление Фото"
                    menuItem.findItem(R.id.buttonBack).isVisible = true
                    menuItem.findItem(R.id.buttonUserProfile).isVisible = false
                    menuItem.findItem(R.id.buttonESP32Settings).isVisible = false
                    menuItem.findItem(R.id.buttonSettings).isVisible = false
                    binding.fabDeleteImage.visibility = View.GONE
                    binding.fabDownloadImage.visibility = View.GONE
                    binding.fabSendImage.visibility = View.GONE
                }

                binding.fabDownloadImage.setOnClickListener {
                    vibrate()
                    downloadImageRequest = true
                    supportActionBar!!.title = "Загрузить Фото"
                    menuItem.findItem(R.id.buttonBack).isVisible = true
                    menuItem.findItem(R.id.buttonUserProfile).isVisible = false
                    menuItem.findItem(R.id.buttonESP32Settings).isVisible = false
                    menuItem.findItem(R.id.buttonSettings).isVisible = false
                    binding.fabDeleteImage.visibility = View.GONE
                    binding.fabDownloadImage.visibility = View.GONE
                    binding.fabSendImage.visibility = View.GONE
                }

                binding.fabSendImage.setOnClickListener {
                    vibrate()
                    if (isNetworkConnected()) {
                        realtimeDatabase.child("users").child(firebaseAuth.currentUser!!.uid).setValue((1000..9999).random().toString())
                                .addOnCompleteListener { setValueTask ->
                                    if (setValueTask.isSuccessful) {
                                        var getPhotoRequest = true
                                        Toast.makeText(baseContext, "Ждём получения фотографии ...", Toast.LENGTH_SHORT).show()
                                        realtimeDatabase.child("users").child(firebaseAuth.currentUser!!.uid)
                                                .addValueEventListener(object : ValueEventListener {
                                                    override fun onDataChange(snapshot: DataSnapshot) {
                                                        if (getPhotoRequest) {
                                                            val snapshotValue = snapshot.getValue(String::class.java)!!
                                                            if (snapshotValue.length != 4) {
                                                                if (snapshotValue != "error") {
                                                                    val image = Firebase.storage.getReferenceFromUrl(snapshotValue)
                                                                    image.downloadUrl.addOnCompleteListener { downloadUrlTask ->
                                                                        if (downloadUrlTask.isSuccessful) {
                                                                            imagesList.add(0, Image(downloadUrlTask.result.toString(), image.name.replace(".jpg", "")))
                                                                            if (items.isEmpty()) {
                                                                                binding.imagesRecyclerView.visibility = View.VISIBLE
                                                                                binding.fabDeleteImage.visibility = View.VISIBLE
                                                                                binding.fabDownloadImage.visibility = View.VISIBLE
                                                                                binding.textViewNoImagesReceived.visibility = View.GONE
                                                                                imageRecyclerAdapter = ImageRecyclerAdapter(imagesList)
                                                                                imageRecyclerAdapter.setOnItemClickListener(imageRecyclerAdapterClickListener)
                                                                                binding.imagesRecyclerView.apply {
                                                                                    adapter = imageRecyclerAdapter
                                                                                    layoutManager = LinearLayoutManager(this@MainActivity)
                                                                                }
                                                                            } else {
                                                                                if (imagesList.size > sharedPreferences.getInt("MaxImages", 5) && !sharedPreferences.getBoolean("DeleteImagesMode", false)) {
                                                                                    storage.getReferenceFromUrl(imagesList[imagesList.size - 1].imageUrl).delete()
                                                                                            .addOnCompleteListener { deleteImageTask ->
                                                                                                if (deleteImageTask.isSuccessful) {
                                                                                                    imagesList.removeAt(imagesList.size - 1)
                                                                                                } else {
                                                                                                    Toast.makeText(baseContext, "Не удалось удалить фото ${imagesList[imagesList.size - 1].imageName}!", Toast.LENGTH_LONG).show()
                                                                                                    Log.i("test", deleteImageTask.exception.toString())
                                                                                                }
                                                                                            }
                                                                                }
                                                                                (binding.imagesRecyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
                                                                                imageRecyclerAdapter.notifyItemInserted(0)
                                                                            }
                                                                        }
                                                                    }
                                                                } else {
                                                                    Toast.makeText(baseContext, "Не удалось\nзагрузить фото\nв Firebase!", Toast.LENGTH_LONG).show()
                                                                }
                                                                getPhotoRequest = false
                                                            }
                                                        }
                                                    }

                                                    override fun onCancelled(error: DatabaseError) {
                                                        Toast.makeText(baseContext, "Не удалось установить\nслушатель изменения\nданных!", Toast.LENGTH_LONG).show()
                                                    }
                                                })
                                    } else {
                                        Toast.makeText(baseContext, "Не удалось отправить\nкоманду на получение\nфото!", Toast.LENGTH_LONG).show()
                                    }
                                }
                    } else {
                        Toast.makeText(baseContext, "Вы не подключены\nк Интернету!", Toast.LENGTH_LONG).show()
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                if (!sharedPreferences.getBoolean("NotificationGroupCreated", false)) {
                    notificationManager.createNotificationChannelGroup(NotificationChannelGroup("SmartDoorbell", "Умный Звонок"))
                    editPreferences.putBoolean("NotificationGroupCreated", true).apply()
                }
                if (!sharedPreferences.getBoolean("NotificationChannelCreated", false)) {
                    val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                    val notificationChannel = NotificationChannel("SmartDoorbellNotification", "Сигнал Звонка", NotificationManager.IMPORTANCE_HIGH)
                    notificationChannel.description = "Уведомление которое отображается при получении сообщения от умного звонка"
                    notificationChannel.enableLights(true)
                    notificationChannel.enableVibration(false)
                    notificationChannel.setSound(Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + R.raw.notification), audioAttributes)
                    notificationChannel.group = "SmartDoorbell"
                    notificationChannel.lockscreenVisibility = View.VISIBLE
                    notificationManager.createNotificationChannel(notificationChannel)
                    editPreferences.putBoolean("NotificationChannelCreated", true).apply()
                }
            }
        } else {
            binding.buttonOpenUserProfile.setOnClickListener {
                vibrate()
                val activity = Intent(this, UserProfile::class.java)
                startActivity(activity)
            }

            binding.buttonOpenSignUp.setOnClickListener {
                vibrate()
                val activity = Intent(this, SignUpActivity::class.java)
                startActivity(activity)
            }
        }
    }

    private val imageRecyclerAdapterClickListener = object : ImageRecyclerAdapter.OnItemClickListener {
        override fun onItemClick(position: Int) {
            if (deleteImageRequest) {
                if (isNetworkConnected()) {
                    vibrate()
                    Firebase.storage.getReferenceFromUrl(imagesList[position].imageUrl).delete()
                            .addOnCompleteListener { deleteImageTask ->
                                if (deleteImageTask.isSuccessful) {
                                    imagesList.removeAt(position)
                                    imageRecyclerAdapter.notifyItemRemoved(position)
                                    Toast.makeText(baseContext, "Фото удалено!", Toast.LENGTH_SHORT).show()
                                    if (imagesList.isEmpty()) {
                                        deleteImageRequest = false
                                        downloadImageRequest = false
                                        supportActionBar!!.title = "Главная"
                                        menuItem.findItem(R.id.buttonBack).isVisible = false
                                        menuItem.findItem(R.id.buttonUserProfile).isVisible = true
                                        menuItem.findItem(R.id.buttonESP32Settings).isVisible = true
                                        menuItem.findItem(R.id.buttonSettings).isVisible = true
                                        binding.imagesRecyclerView.visibility = View.GONE
                                        binding.textViewNoImagesReceived.visibility = View.VISIBLE
                                        binding.fabSendImage.visibility = View.VISIBLE
                                    }
                                } else {
                                    Toast.makeText(baseContext, "Не удалось\nудалить фото!", Toast.LENGTH_LONG).show()
                                }
                            }
                } else {
                    Toast.makeText(baseContext, "Вы не подключены\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }
            if (downloadImageRequest) {
                if (isNetworkConnected()) {
                    vibrate()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE), 1)
                    }
                    Firebase.storage.getReferenceFromUrl(imagesList[position].imageUrl).getBytes(1024 * 100).addOnSuccessListener { bytes ->
                        val imageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        fileOutputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val myContentResolver = this@MainActivity.contentResolver
                            val contentValues = ContentValues()
                            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, (imagesList[position].imageName).replace(":", "-") + ".jpg")
                            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, sharedPreferences.getString("ImagesDirectory", "Pictures/Умный Звонок").toString())
                            val imageUri = myContentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
                            myContentResolver.openOutputStream(imageUri) as FileOutputStream
                        } else {
                            val imagesDir = File(sharedPreferences.getString("ImagesDirectory", "Pictures/Умный Звонок").toString())
                            if (!imagesDir.exists()) {
                                imagesDir.mkdir()
                            }
                            FileOutputStream(File(imagesDir, (imagesList[position].imageName).replace(":", "-") + ".jpg"))
                        }
                        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
                        fileOutputStream.flush()
                        fileOutputStream.close()
                        Toast.makeText(baseContext, "Фото сохранено!", Toast.LENGTH_LONG).show()
                    }.addOnFailureListener {
                        Toast.makeText(baseContext, "Не удалось\nсохранить фото!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(baseContext, "Вы не подключены\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val actionButtonsInflater = menuInflater
        actionButtonsInflater.inflate(R.menu.activity_main_menu, menu)
        menuItem = menu!!
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.buttonBack -> {
                vibrate()
                deleteImageRequest = false
                downloadImageRequest = false
                supportActionBar!!.title = "Главная"
                menuItem.findItem(R.id.buttonBack).isVisible = false
                menuItem.findItem(R.id.buttonUserProfile).isVisible = true
                menuItem.findItem(R.id.buttonESP32Settings).isVisible = true
                menuItem.findItem(R.id.buttonSettings).isVisible = true
                binding.fabDeleteImage.visibility = View.VISIBLE
                binding.fabDownloadImage.visibility = View.VISIBLE
                binding.fabSendImage.visibility = View.VISIBLE
                true
            }
            R.id.buttonUserProfile -> {
                vibrate()
                val activity = Intent(this, UserProfile::class.java)
                startActivity(activity)
                true
            }
            R.id.buttonESP32Settings -> {
                vibrate()
                if (isUserLogged) {
                    val activity = Intent(this, ESP32SettingsActivity::class.java)
                    startActivity(activity)
                } else {
                    Toast.makeText(baseContext, "Вы не вошли\nв пользователя!", Toast.LENGTH_LONG).show()
                }
                true
            }
            R.id.buttonSettings -> {
                vibrate()
                val activity = Intent(this, SettingsActivity::class.java)
                startActivity(activity)
                true
            }
            else->super.onOptionsItemSelected(item)
        }
    }

    private fun isNetworkConnected() : Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            return networkInfo.isConnected
        }
    }

    private fun vibrate() {
        if (vibrator.hasVibrator()) {
            if (isHapticFeedbackEnabled) {
                binding.buttonOpenUserProfile.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(20)
                }
            }
        }
    }
}