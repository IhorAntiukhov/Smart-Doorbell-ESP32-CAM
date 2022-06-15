package com.arduinoworld.smartdoorbell

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arduinoworld.smartdoorbell.databinding.ActivityEsp32SettingsBinding
import com.google.firebase.database.FirebaseDatabase
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException


class ESP32SettingsActivity : AppCompatActivity() {
    lateinit var binding: ActivityEsp32SettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEsp32SettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarESP32Settings)
        supportActionBar!!.title = "Настройка ESP32-CAM"
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val resolutionsArrayAdapter = ArrayAdapter(applicationContext, R.layout.resolution_dropdown_menu_item, listOf("UXGA", "SXGA", "XGA", "SVGA", "VGA", "CIF", "QVGA"))
        val whiteBalanceArrayAdapter = ArrayAdapter(applicationContext, R.layout.white_balance_dropdown_menu_item, listOf("Авто Баланс", "Солнечный", "Облачный", "Офисный", "Домашний"))
        binding.inputResolution.setAdapter(resolutionsArrayAdapter)
        binding.inputWhiteBalance.setAdapter(whiteBalanceArrayAdapter)
        binding.inputResolution.threshold = 1
        binding.inputWhiteBalance.threshold = 1

        binding.inputWiFiSsid.setText(MainActivity.sharedPreferences.getString("WiFiSsid", "").toString())
        binding.inputWiFiPassword.setText(MainActivity.sharedPreferences.getString("WiFiPassword", "").toString())
        binding.inputResolution.setText(MainActivity.sharedPreferences.getString("Resolution", "SVGA"), false)
        binding.inputWhiteBalance.setText(MainActivity.sharedPreferences.getString("WhiteBalance", "Авто Баланс"), false)

        var flashState = MainActivity.sharedPreferences.getBoolean("FlashState", false)
        var flip = MainActivity.sharedPreferences.getBoolean("Flip", false)
        var mirror = MainActivity.sharedPreferences.getBoolean("Mirror", false)
        if (flashState) binding.buttonFlash.setImageResource(R.drawable.ic_flash_on)
        if (mirror) binding.buttonFlash.setBackgroundResource(R.drawable.image_button_background_style)
        binding.switchStartSleep.isChecked = MainActivity.sharedPreferences.getBoolean("StartSleep", false)

        var resolution = 0
        var whiteBalance = 0

        when {
            binding.inputResolution.text.toString() == "UXGA" -> {
                resolution = 0
            }
            binding.inputResolution.text.toString() == "SXGA" -> {
                resolution = 1
            }
            binding.inputResolution.text.toString() == "XGA" -> {
                resolution = 2
            }
            binding.inputResolution.text.toString() == "SVGA" -> {
                resolution = 3
            }
            binding.inputResolution.text.toString() == "VGA" -> {
                resolution = 4
            }
            binding.inputResolution.text.toString() == "CIF" -> {
                resolution = 5
            }
            binding.inputResolution.text.toString() == "QVGA" -> {
                resolution = 6
            }
        }

        when {
            binding.inputWhiteBalance.text.toString() == "Авто Баланс" -> {
                whiteBalance = 0
            }
            binding.inputWhiteBalance.text.toString() == "Солнечный" -> {
                whiteBalance = 1
            }
            binding.inputWhiteBalance.text.toString() == "Облачный" -> {
                whiteBalance = 2
            }
            binding.inputWhiteBalance.text.toString() == "Офисный" -> {
                whiteBalance = 3
            }
            binding.inputWhiteBalance.text.toString() == "Домашний" -> {
                whiteBalance = 4
            }
        }
        
        binding.inputResolution.setOnItemClickListener { _, _, position, _ ->
            vibrate()
            resolution = position
        }
        binding.inputWhiteBalance.setOnItemClickListener { _, _, position, _ ->
            vibrate()
            whiteBalance = position
        }

        binding.buttonSendESP32Settings.setOnClickListener {
            vibrate()
            if (binding.inputWiFiSsid.text!!.isNotEmpty() && binding.inputWiFiPassword.text!!.isNotEmpty()) {
                if (binding.inputWiFiPassword.text!!.length >= 8) {
                    val alertDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
                    alertDialogBuilder.setTitle("Выберете Способ Отправки")
                    alertDialogBuilder.setItems(arrayOf("Отправить по WiFi", "Отправить по Интернету")) { _, position ->
                        hideKeyboard()
                        MainActivity.editPreferences.putString("WiFiSsid", binding.inputWiFiSsid.text.toString())
                        MainActivity.editPreferences.putString("WiFiPassword", binding.inputWiFiPassword.text.toString())
                        MainActivity.editPreferences.putString("Resolution", binding.inputResolution.text.toString())
                        MainActivity.editPreferences.putString("WhiteBalance", binding.inputWhiteBalance.text.toString())
                        MainActivity.editPreferences.putBoolean("FlashState", flashState)
                        MainActivity.editPreferences.putBoolean("Flip", flip)
                        MainActivity.editPreferences.putBoolean("Mirror", mirror)
                        MainActivity.editPreferences.putBoolean("StartSleep", binding.switchStartSleep.isChecked).apply()

                        when (position) {
                            0 -> {
                                vibrate()
                                Thread {
                                    val client = OkHttpClient()
                                    val request = Request.Builder().url(
                                            "http://192.168.4.1/${binding.inputWiFiSsid.text}&" +
                                                    "${binding.inputWiFiPassword.text}&${MainActivity.sharedPreferences.getString("UserEmail", "")}" +
                                                    "&${MainActivity.sharedPreferences.getString("UserPassword", "")}" +
                                                    "&$resolution$whiteBalance$flashState&$flip&$mirror&${binding.switchStartSleep.isChecked}").build()
                                    runOnUiThread {
                                        Toast.makeText(baseContext, "Отправляем параметры\nработы ...", Toast.LENGTH_SHORT).show()
                                    }
                                    try {
                                        client.newCall(request).execute()
                                    } catch (i: IOException) { }
                                    val activity = Intent(this, MainActivity::class.java)
                                    startActivity(activity)
                                    finish()
                                }.start()
                            }
                            1 -> {
                                vibrate()
                                FirebaseDatabase.getInstance().reference.child("users").child(MainActivity.firebaseAuth.currentUser!!.uid).setValue("Ё${binding.inputWiFiSsid.text}&" +
                                "${binding.inputWiFiPassword.text}&${MainActivity.sharedPreferences.getString("UserEmail", "")}&${MainActivity.sharedPreferences.getString("UserPassword", "")}" +
                                "&$resolution$whiteBalance$flashState&$flip&$mirror&${binding.switchStartSleep.isChecked}")
                                        .addOnCompleteListener { setValueTask ->
                                            if (setValueTask.isSuccessful) {
                                                Toast.makeText(baseContext, "Параметры работы отправлены!", Toast.LENGTH_SHORT).show()
                                                val activity = Intent(this, MainActivity::class.java)
                                                startActivity(activity)
                                                finish()
                                            } else {
                                                Toast.makeText(baseContext, "Не удалось отправить\nпараметры работы!", Toast.LENGTH_LONG).show()
                                            }
                                        }
                            }
                        }
                    }
                    val alertDialog = alertDialogBuilder.create()
                    alertDialog.show()
                } else {
                    binding.inputLayoutWiFiSsid.isErrorEnabled = false
                    binding.inputLayoutWiFiPassword.isErrorEnabled = true
                    binding.inputLayoutWiFiPassword.error = "Пароль должен быть не меньше 8 символов"
                }
            } else {
                if (binding.inputWiFiSsid.text!!.isEmpty()) {
                    binding.inputLayoutWiFiSsid.isErrorEnabled = true
                    binding.inputLayoutWiFiSsid.error = "Введите название WiFi сети"
                }
                if (binding.inputWiFiPassword.text!!.isEmpty()) {
                    binding.inputLayoutWiFiPassword.isErrorEnabled = true
                    binding.inputLayoutWiFiPassword.error = "Введите пароль WiFi сети"
                }
            }
        }

        binding.buttonFlash.setOnClickListener {
            vibrate()
            flashState = !flashState
            if (flashState) {
                binding.buttonFlash.setImageResource(R.drawable.ic_flash_on)
            } else {
                binding.buttonFlash.setImageResource(R.drawable.ic_flash_off)
            }
        }

        binding.buttonFlip.setOnClickListener {
            vibrate()
            flip = !flip
            if (flip) {
                Toast.makeText(this, "Фотографии будут переворачиваться", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Фотографии не будут переворачиваться", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonMirror.setOnClickListener {
            vibrate()
            mirror = !mirror
            if (mirror) {
                binding.buttonMirror.setBackgroundResource(R.drawable.image_button_background_style)
            } else {
                binding.buttonMirror.setBackgroundResource(R.drawable.mirror_button_background_style)
            }
        }

        binding.switchStartSleep.setOnCheckedChangeListener { _, _ ->
            vibrate()
            Log.i("test", binding.switchStartSleep.isChecked.toString())
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id : Int = item.itemId
        if (id == android.R.id.home) {
            vibrate()
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun hideKeyboard() {
        this.currentFocus?.let { view ->
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputMethodManager!!.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (MainActivity.vibrator.hasVibrator()) {
            if (MainActivity.isHapticFeedbackEnabled) {
                binding.buttonSendESP32Settings.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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