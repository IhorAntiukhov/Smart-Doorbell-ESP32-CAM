package com.arduinoworld.smartdoorbell

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.arduinoworld.smartdoorbell.databinding.ActivitySettingsBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ListResult
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage

class SettingsActivity : AppCompatActivity() {
    lateinit var binding : ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarSettings)
        supportActionBar!!.title = "Настройки"

        if (!MainActivity.isHapticFeedbackEnabled) {
            binding.radioGroupVibrationTypes.check(R.id.radioButtonNormalVibration)
        } else {
            binding.radioGroupVibrationTypes.check(R.id.radioButtonHapticFeedback)
        }
        if (MainActivity.sharedPreferences.getBoolean("DeleteImagesMode", false)) {
            binding.radioGroupDeleteImagesMode.check(R.id.radioButtonDeleteAllImages)
        } else {
            binding.radioGroupDeleteImagesMode.check(R.id.radioButtonDeleteOldestImage)
        }

        binding.inputMaxImages.setText(MainActivity.sharedPreferences.getInt("MaxImages", 5).toString())
        binding.inputImagesDirectory.setText(MainActivity.sharedPreferences.getString("ImagesDirectory", "Pictures/Умный Звонок"))

        binding.radioGroupVibrationTypes.setOnCheckedChangeListener { _, checkedId ->
            vibrate()
            when (checkedId) {
                R.id.radioButtonNormalVibration -> {
                    MainActivity.isHapticFeedbackEnabled = false
                    MainActivity.editPreferences.putBoolean("HapticFeedback", false).apply()
                }
                R.id.radioButtonHapticFeedback -> {
                    MainActivity.isHapticFeedbackEnabled = true
                    MainActivity.editPreferences.putBoolean("HapticFeedback", true).apply()
                }
            }
        }

        binding.radioGroupDeleteImagesMode.setOnCheckedChangeListener { _, checkedId ->
            vibrate()
            when (checkedId) {
                R.id.radioButtonDeleteAllImages -> {
                    MainActivity.editPreferences.putBoolean("DeleteImagesMode", true).apply()
                }
                R.id.radioButtonDeleteOldestImage -> {
                    MainActivity.editPreferences.putBoolean("DeleteImagesMode", false).apply()
                }
            }
        }

        binding.buttonSelectDirectory.setOnClickListener {
            vibrate()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val directoryChooser = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                directoryChooser.addCategory(Intent.CATEGORY_DEFAULT)
                getResult.launch(Intent.createChooser(directoryChooser, "Выберете Папку"))
            } else {
                Toast.makeText(baseContext, "Ваша версия Android\nSDK меньше чем Lollipop!", Toast.LENGTH_LONG).show()
            }
        }

        binding.buttonDeleteImages.setOnClickListener {
            vibrate()
            val alertDialogDeleteImagesBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
            alertDialogDeleteImagesBuilder.setTitle("Удаление Фотографий")
            alertDialogDeleteImagesBuilder.setMessage("Вы точно хотите удалить все фотографии?")
            alertDialogDeleteImagesBuilder.setCancelable(true)
            alertDialogDeleteImagesBuilder.setPositiveButton("Подтвердить") { _, _ ->
                vibrate()
                val listAllTask : Task<ListResult> = Firebase.storage.reference.child(MainActivity.firebaseAuth.currentUser!!.uid).listAll()
                listAllTask.addOnCompleteListener { result ->
                    val items: List<StorageReference> = result.result.items
                    items.forEach { item ->
                        item.delete().addOnCompleteListener { deletePhotoTask ->
                            if (!deletePhotoTask.isSuccessful) {
                                Toast.makeText(baseContext, "Не удалось удалить фото ${item.name}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    Toast.makeText(baseContext, "Фотографии удалены!", Toast.LENGTH_SHORT).show()
                }
            }
            alertDialogDeleteImagesBuilder.setNegativeButton("Отмена") { _, _ ->
                vibrate()
            }
            val alertDialogDeleteImages = alertDialogDeleteImagesBuilder.create()
            alertDialogDeleteImages.show()
        }

        binding.buttonDefaultSettings.setOnClickListener {
            vibrate()
            binding.radioGroupVibrationTypes.check(R.id.radioButtonHapticFeedback)
            binding.radioGroupDeleteImagesMode.check(R.id.radioButtonDeleteOldestImage)
            binding.inputMaxImages.setText("5")
            binding.inputImagesDirectory.setText(getString(R.string.images_directory))
            MainActivity.editPreferences.putString("ImagesDirectory", getString(R.string.images_directory)).apply()
        }
    }

    private val getResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode == Activity.RESULT_OK) {
                val directory = it.data!!.data!!.path.toString().substring(14, it.data!!.data!!.path.toString().length) + "/Умный Звонок"
                binding.inputImagesDirectory.setText(directory)
                MainActivity.editPreferences.putString("ImagesDirectory", directory).apply()
            }
        }

    override fun onPause() {
        super.onPause()
        if (binding.inputMaxImages.text.toString().toInt() <= 10 && binding.inputMaxImages.text.toString().toInt() != 0) {
            MainActivity.editPreferences.putInt("MaxImages", binding.inputMaxImages.text.toString().toInt()).apply()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val actionButtonsInflater = menuInflater
        actionButtonsInflater.inflate(R.menu.settings_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (binding.inputMaxImages.text.toString().toInt() > 10) {
            Toast.makeText(baseContext, "Максимальное количество\nфотографий - 10", Toast.LENGTH_LONG).show()
        }
        return when(item.itemId) {
            R.id.buttonHome -> {
                vibrate()
                val activity = Intent(this, MainActivity::class.java)
                startActivity(activity)
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
                val activity = Intent(this, ESP32SettingsActivity::class.java)
                startActivity(activity)
                true
            }
            else->super.onOptionsItemSelected(item)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (MainActivity.vibrator.hasVibrator()) {
            if (MainActivity.isHapticFeedbackEnabled) {
                binding.buttonDefaultSettings.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    MainActivity.vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    MainActivity.vibrator.vibrate(20)
                }
            }
        }
    }
}