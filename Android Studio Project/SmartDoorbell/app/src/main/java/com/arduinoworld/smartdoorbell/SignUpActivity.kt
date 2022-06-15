package com.arduinoworld.smartdoorbell

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.VibrationEffect
import android.util.Patterns
import android.view.HapticFeedbackConstants
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.arduinoworld.smartdoorbell.databinding.ActivitySignUpBinding
import com.arduinoworld.smartdoorbell.databinding.ProgressBarBinding
import com.google.firebase.auth.EmailAuthProvider

@Suppress("DEPRECATION")
class SignUpActivity : AppCompatActivity() {
    lateinit var binding : ActivitySignUpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarSignUp)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        if (intent.hasExtra("LaunchReason")) {
            val updateEmailOrPassword = intent.getBooleanExtra("LaunchReason", true)
            if (updateEmailOrPassword) {
                supportActionBar!!.title = "Обновить Почту"
                binding.buttonUpdateUser.visibility = View.VISIBLE
                binding.layoutNextBackButtons.visibility = View.GONE
                binding.textViewSignUp.text = getString(R.string.concatenated_text, "Введите новую почту", "вашего пользователя")
            } else {
                supportActionBar!!.title = "Обновить Пароль"
                binding.inputLayoutPassword.visibility = View.VISIBLE
                binding.inputLayoutEmail.visibility = View.GONE
                binding.textViewSignUp.text = getString(R.string.concatenated_text, "Введите новый пароль", "вашего пользователя")

                binding.buttonNext.setOnClickListener {
                    vibrate()
                    if (binding.inputPassword.text!!.isNotEmpty()) {
                        if (binding.inputPassword.text!!.length >= 6) {
                            if (containsNumber(binding.inputPassword.text!!.toString())) {
                                if (MainActivity.sharedPreferences.getString("UserPassword", "").toString()
                                != binding.inputPassword.text.toString()) {
                                    hideKeyboard()
                                    binding.textViewSignUp.text = getString(R.string.concatenated_text, "Подтвердите пароль", "вашего пользователя")
                                    binding.inputLayoutConfirmPassword.visibility = View.VISIBLE
                                    binding.buttonBack.visibility = View.VISIBLE
                                    binding.buttonUpdateUser.visibility = View.VISIBLE
                                    binding.inputLayoutPassword.visibility = View.GONE
                                    binding.buttonNext.visibility = View.GONE
                                    binding.inputLayoutPassword.isErrorEnabled = false
                                } else {
                                    binding.inputLayoutPassword.isErrorEnabled = true
                                    binding.inputLayoutPassword.error = "Введите новый пароль"
                                }
                            } else {
                                binding.inputLayoutPassword.isErrorEnabled = true
                                binding.inputLayoutPassword.error = "Пароль должен иметь хотя бы 1 цифру"
                            }
                        } else {
                            binding.inputLayoutPassword.isErrorEnabled = true
                            binding.inputLayoutPassword.error = "Пароль должен быть не меньше 6 символов"
                        }
                    } else {
                        binding.inputLayoutPassword.isErrorEnabled = true
                        binding.inputLayoutPassword.error = "Введите пароль пользователя"
                    }
                }
                binding.buttonBack.setOnClickListener {
                    vibrate()
                    hideKeyboard()
                    binding.textViewSignUp.text = getString(R.string.concatenated_text, "Введите новый пароль", "вашего пользователя")
                    binding.inputLayoutPassword.visibility = View.VISIBLE
                    binding.buttonNext.visibility = View.VISIBLE
                    binding.inputLayoutConfirmPassword.visibility = View.GONE
                    binding.buttonUpdateUser.visibility = View.GONE
                    binding.buttonBack.visibility = View.GONE
                }
            }
            binding.buttonUpdateUser.setOnClickListener {
                vibrate()
                if (isNetworkConnected()) {
                    if (updateEmailOrPassword) {
                        if (binding.inputEmail.text!!.isNotEmpty()) {
                            if (isValidEmail(binding.inputEmail.text!!)) {
                                if (MainActivity.sharedPreferences.getString("UserEmail", "").toString()
                                    != binding.inputEmail.text!!.toString()) {
                                    hideKeyboard()
                                    binding.inputLayoutEmail.isErrorEnabled = false
                                    MainActivity.firebaseAuth.currentUser!!.reauthenticate(
                                        EmailAuthProvider.getCredential(
                                            MainActivity.sharedPreferences.getString("UserEmail", "").toString(),
                                            MainActivity.sharedPreferences.getString("UserPassword", "").toString()))
                                        .addOnCompleteListener { reauthTask ->
                                        if (reauthTask.isSuccessful) {
                                            MainActivity.firebaseAuth.currentUser!!.updateEmail(binding.inputEmail.text.toString())
                                            MainActivity.editPreferences.putString("UserEmail", binding.inputEmail.text!!.toString()).apply()
                                            Toast.makeText(this, "Почта пользователя обновлена!", Toast.LENGTH_SHORT).show()
                                            val activity = Intent(this, UserProfile::class.java)
                                            startActivity(activity)
                                            finish()
                                        } else {
                                            Toast.makeText(this, "Не удалось переавторизироваться!", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    binding.inputLayoutEmail.isErrorEnabled = true
                                    binding.inputLayoutEmail.error = "Введите новую почту"
                                }
                            } else {
                                binding.inputLayoutEmail.isErrorEnabled = true
                                binding.inputLayoutEmail.error = "Неправильная почта"
                            }
                        } else {
                            binding.inputLayoutEmail.isErrorEnabled = true
                            binding.inputLayoutEmail.error = "Введите почту пользователя"
                        }
                    } else {
                        if (binding.inputConfirmPassword.text!!.isNotEmpty()) {
                            if (binding.inputConfirmPassword.text!!.toString() == binding.inputPassword.text!!.toString()) {
                                hideKeyboard()
                                MainActivity.firebaseAuth.currentUser!!.reauthenticate(
                                    EmailAuthProvider.getCredential(
                                        MainActivity.sharedPreferences.getString("UserEmail", "").toString(),
                                        MainActivity.sharedPreferences.getString("UserPassword", "").toString()))
                                    .addOnCompleteListener { reauthTask ->
                                    if (reauthTask.isSuccessful) {
                                        supportActionBar!!.title = "Профиль"
                                        MainActivity.firebaseAuth.currentUser!!.updatePassword(binding.inputPassword.text.toString())
                                        MainActivity.editPreferences.putString("UserPassword", binding.inputPassword.text!!.toString()).apply()
                                        Toast.makeText(this, "Пароль пользователя обновлён!", Toast.LENGTH_SHORT).show()
                                        val activity = Intent(this, UserProfile::class.java)
                                        startActivity(activity)
                                        finish()
                                    } else {
                                        Toast.makeText(this, "Не удалось переавторизироваться!", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                binding.inputLayoutConfirmPassword.isErrorEnabled = true
                                binding.inputLayoutConfirmPassword.error = "Пароль не совпадает"
                            }
                        } else {
                            binding.inputLayoutConfirmPassword.isErrorEnabled = true
                            binding.inputLayoutConfirmPassword.error = "Подтвердите пароль пользователя"
                        }
                    }
                } else {
                    Toast.makeText(this, "Вы не подключены\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            supportActionBar!!.title = "Регистрация"
            var userCredentialNumber = 0
            binding.buttonNext.setOnClickListener {
                vibrate()
                when (userCredentialNumber) {
                    0 -> {
                        if (binding.inputEmail.text!!.isNotEmpty()) {
                            if (isValidEmail(binding.inputEmail.text!!)) {
                                hideKeyboard()
                                userCredentialNumber =+ 1
                                binding.textViewSignUp.text = getString(R.string.concatenated_text, "Ваш пароль должен", "иметь хотя бы 1 цифру")
                                binding.inputLayoutEmail.visibility = View.GONE
                                binding.inputLayoutPassword.visibility = View.VISIBLE
                                binding.buttonBack.visibility = View.VISIBLE
                                binding.inputLayoutEmail.isErrorEnabled = false
                            } else {
                                binding.inputLayoutEmail.isErrorEnabled = true
                                binding.inputLayoutEmail.error = "Неправильная почта"
                            }
                        } else {
                            binding.inputLayoutEmail.isErrorEnabled = true
                            binding.inputLayoutEmail.error = "Введите почту пользователя"
                        }
                    }
                    1 -> {
                        if (binding.inputPassword.text!!.isNotEmpty()) {
                            if (binding.inputPassword.text!!.length >= 6) {
                                if (containsNumber(binding.inputPassword.text!!.toString())) {
                                    hideKeyboard()
                                    userCredentialNumber += 1
                                    binding.textViewSignUp.text = getString(R.string.concatenated_text, "Подтвердите пароль", "вашего пользователя")
                                    binding.inputLayoutConfirmPassword.visibility = View.VISIBLE
                                    binding.buttonSignUp.visibility = View.VISIBLE
                                    binding.inputLayoutPassword.visibility = View.GONE
                                    binding.buttonNext.visibility = View.GONE
                                    binding.inputLayoutPassword.isErrorEnabled = false
                                } else {
                                    binding.inputLayoutPassword.isErrorEnabled = true
                                    binding.inputLayoutPassword.error = "Пароль должен иметь хотя бы 1 цифру"
                                }
                            } else {
                                binding.inputLayoutPassword.isErrorEnabled = true
                                binding.inputLayoutPassword.error = "Пароль должен быть не меньше 6 символов"
                            }
                        } else {
                            binding.inputLayoutPassword.isErrorEnabled = true
                            binding.inputLayoutPassword.error = "Введите пароль пользователя"
                        }
                    }
                }
            }

            binding.buttonBack.setOnClickListener {
                vibrate()
                hideKeyboard()
                if (userCredentialNumber == 2) {
                    userCredentialNumber -= 1
                    binding.textViewSignUp.text = getString(R.string.concatenated_text, "Ваш пароль должен", "иметь хотя бы 1 цифру")
                    binding.inputLayoutPassword.visibility = View.VISIBLE
                    binding.buttonNext.visibility = View.VISIBLE
                    binding.buttonBack.visibility = View.VISIBLE
                    binding.inputLayoutConfirmPassword.visibility = View.GONE
                    binding.buttonSignUp.visibility = View.GONE
                } else if (userCredentialNumber == 1) {
                    userCredentialNumber -= 1
                    binding.textViewSignUp.text = getString(R.string.concatenated_text, "Придумайте почту", "вашего пользователя")
                    binding.inputLayoutEmail.visibility = View.VISIBLE
                    binding.inputLayoutPassword.visibility = View.GONE
                    binding.buttonBack.visibility = View.GONE
                }
            }

            binding.buttonSignUp.setOnClickListener {
                vibrate()
                if (isNetworkConnected()) {
                    if (binding.inputConfirmPassword.text!!.isNotEmpty()) {
                        if (binding.inputConfirmPassword.text!!.toString() == binding.inputPassword.text!!.toString()) {
                            hideKeyboard()
                            val alertDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(this)
                            val progressBarBinding : ProgressBarBinding = ProgressBarBinding.inflate(layoutInflater)
                            progressBarBinding.textViewProgress.text = "Создаём пользователя ..."
                            alertDialogBuilder.setView(progressBarBinding.root)
                            val alertDialog = alertDialogBuilder.create()
                            alertDialog.setCanceledOnTouchOutside(false)
                            alertDialog.show()
                            MainActivity.firebaseAuth.createUserWithEmailAndPassword(binding.inputEmail.text.toString(), binding.inputPassword.text.toString())
                                .addOnCompleteListener(this) { createUserTask ->
                                    if (createUserTask.isSuccessful) {
                                        Toast.makeText(this, "Пользователь успешно зарегистрирован!", Toast.LENGTH_SHORT).show()
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                            if (MainActivity.sharedPreferences.getBoolean("UserLogged", false)) {
                                                supportActionBar?.title = "Профиль"
                                            } else {
                                                supportActionBar?.title = "Вход"
                                            }
                                        }
                                        val activity = Intent(this, UserProfile::class.java)
                                        startActivity(activity)
                                        finish()
                                    } else {
                                        userCredentialNumber = 0
                                        binding.textViewSignUp.text = getString(R.string.concatenated_text, "Придумайте почту", "вашего пользователя")
                                        binding.inputLayoutEmail.visibility = View.VISIBLE
                                        binding.buttonNext.visibility = View.VISIBLE
                                        binding.inputLayoutConfirmPassword.visibility = View.GONE
                                        binding.buttonBack.visibility = View.GONE
                                        binding.buttonSignUp.visibility = View.GONE
                                        binding.inputLayoutEmail.isErrorEnabled = true
                                        binding.inputLayoutEmail.error = "Эта почта уже существует"
                                    }
                                    alertDialog.cancel()
                                }
                        } else {
                            binding.inputLayoutConfirmPassword.isErrorEnabled = true
                            binding.inputLayoutConfirmPassword.error = "Пароль не совпадает"
                        }
                    } else {
                        binding.inputLayoutConfirmPassword.isErrorEnabled = true
                        binding.inputLayoutConfirmPassword.error = "Подтвердите пароль пользователя"
                    }
                } else {
                    Toast.makeText(this, "Вы не подключены\nк Интернету!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item : MenuItem): Boolean {
        val id : Int = item.itemId
        if (id == android.R.id.home) {
            vibrate()
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun isValidEmail(inputText: CharSequence) : Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(inputText).matches()
    }

    private fun containsNumber(inputText: String) : Boolean {
        return inputText.contains("1") || inputText.contains("2") || inputText.contains("3") || inputText.contains("4") ||
                inputText.contains("5") || inputText.contains("6") || inputText.contains("7") || inputText.contains("8") ||
                inputText.contains("9") || inputText.contains("0")
    }

    private fun hideKeyboard() {
        this.currentFocus?.let { view ->
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputMethodManager!!.hideSoftInputFromWindow(view.windowToken, 0)
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
        if (MainActivity.vibrator.hasVibrator()) {
            if (MainActivity.isHapticFeedbackEnabled) {
                binding.buttonNext.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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