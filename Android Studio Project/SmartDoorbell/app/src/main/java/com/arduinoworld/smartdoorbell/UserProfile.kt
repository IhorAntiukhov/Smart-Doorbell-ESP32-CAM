package com.arduinoworld.smartdoorbell

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.util.Patterns
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arduinoworld.smartdoorbell.databinding.ActivityUserProfileBinding
import com.arduinoworld.smartdoorbell.databinding.ProgressBarBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.ListResult
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage

@Suppress("DEPRECATION")
class UserProfile : AppCompatActivity() {
    private lateinit var binding : ActivityUserProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        var userUID: String
        var passwordToggle = false

        setSupportActionBar(binding.toolbarSignIn)

        binding.inputUserEmail.setText(MainActivity.sharedPreferences.getString("UserEmail", "").toString())
        binding.inputUserPassword.setText(MainActivity.sharedPreferences.getString("UserPassword", "").toString())

        if (MainActivity.sharedPreferences.getBoolean("UserLogged", false)) {
            supportActionBar?.title = "Профиль"
            binding.inputLayoutUserEmail.visibility = View.GONE
            binding.inputLayoutUserPassword.visibility = View.GONE
            binding.buttonSignIn.visibility = View.GONE
            binding.buttonResetPassword.visibility = View.GONE
            binding.imageViewBigUser.visibility = View.VISIBLE
            binding.layoutEmailPassword.visibility = View.VISIBLE
            binding.layoutUserSettings.visibility = View.VISIBLE
            binding.textViewEmailPassword.text = getString(R.string.concatenated_text, binding.inputUserEmail.text!!.toString(),
                hidePassword(binding.inputUserPassword.text!!.toString()))
            userUID = MainActivity.firebaseAuth.currentUser!!.uid
        } else {
            supportActionBar!!.title = "Вход"
        }

        binding.buttonSignIn.setOnClickListener {
            vibrate()
            if (isNetworkConnected()) {
                if (binding.inputUserEmail.text!!.isNotEmpty() && binding.inputUserPassword.text!!.isNotEmpty()) {
                    hideKeyboard()
                    val alertDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(this)
                    val progressBarBinding : ProgressBarBinding = ProgressBarBinding.inflate(layoutInflater)
                    progressBarBinding.textViewProgress.text = "Входим в пользователя ..."
                    alertDialogBuilder.setView(progressBarBinding.root)
                    val alertDialog = alertDialogBuilder.create()
                    alertDialog.setCanceledOnTouchOutside(false)
                    alertDialog.show()
                    MainActivity.firebaseAuth.signInWithEmailAndPassword(binding.inputUserEmail.text.toString(), binding.inputUserPassword.text.toString())
                        .addOnCompleteListener(this) { signInTask ->
                            if (signInTask.isSuccessful) {
                                supportActionBar?.title = "Профиль"
                                binding.inputLayoutUserEmail.visibility = View.GONE
                                binding.inputLayoutUserPassword.visibility = View.GONE
                                binding.buttonSignIn.visibility = View.GONE
                                binding.buttonResetPassword.visibility = View.GONE
                                binding.imageViewBigUser.visibility = View.VISIBLE
                                binding.layoutEmailPassword.visibility = View.VISIBLE
                                binding.layoutUserSettings.visibility = View.VISIBLE
                                binding.inputLayoutUserEmail.isErrorEnabled = false
                                binding.inputLayoutUserPassword.isErrorEnabled = false
                                binding.textViewEmailPassword.text = getString(R.string.concatenated_text, binding.inputUserEmail.text!!.toString(),
                                    hidePassword(binding.inputUserPassword.text!!.toString()))
                                userUID = MainActivity.firebaseAuth.currentUser!!.uid
                                MainActivity.editPreferences.putBoolean("UserLogged", true)
                                MainActivity.editPreferences.putString("UserEmail", binding.inputUserEmail.text!!.toString())
                                MainActivity.editPreferences.putString("UserPassword", binding.inputUserPassword.text!!.toString()).apply()
                                if (!MainActivity.sharedPreferences.getBoolean(MainActivity.firebaseAuth.currentUser!!.uid, false)) {
                                    FirebaseDatabase.getInstance().reference.child("users").child(MainActivity.firebaseAuth.currentUser!!.uid).setValue("0000")
                                }
                                if (!MainActivity.sharedPreferences.getBoolean("TopicSubscribed", false)) {
                                    FirebaseMessaging.getInstance().subscribeToTopic(userUID).addOnCompleteListener { subscribeToTopicTask ->
                                        if (subscribeToTopicTask.isSuccessful) {
                                            MainActivity.editPreferences.putBoolean("TopicSubscribed", true).apply()
                                        } else {
                                            Toast.makeText(baseContext, "Не удалось подписаться\nна топик уведомлений.\nПопробуйте выйти и\nвойти в пользователя", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            } else {
                                try {
                                    throw signInTask.exception!!
                                } catch (e: FirebaseAuthInvalidCredentialsException) {
                                    binding.inputLayoutUserEmail.isErrorEnabled = false
                                    binding.inputLayoutUserPassword.isErrorEnabled = true
                                    binding.inputLayoutUserPassword.error = "Неверный пароль"
                                } catch (e: FirebaseAuthInvalidUserException) {
                                    when (e.errorCode) {
                                        "ERROR_USER_NOT_FOUND" -> {
                                            binding.inputLayoutUserPassword.isErrorEnabled = false
                                            binding.inputLayoutUserEmail.isErrorEnabled = true
                                            binding.inputLayoutUserEmail.error = "Почта не обнаружена"
                                        }
                                    }
                                }
                            }
                            alertDialog.cancel()
                        }
                } else {
                    if (binding.inputUserEmail.text!!.isEmpty() && binding.inputUserPassword.text!!.isEmpty()) {
                        binding.inputLayoutUserEmail.isErrorEnabled = true
                        binding.inputLayoutUserPassword.isErrorEnabled = true
                        binding.inputLayoutUserEmail.error = "Введите почту пользователя"
                        binding.inputLayoutUserPassword.error = "Введите пароль пользователя"
                    } else if (binding.inputUserEmail.text!!.isEmpty() && binding.inputUserPassword.text!!.isNotEmpty()) {
                        binding.inputLayoutUserEmail.isErrorEnabled = true
                        binding.inputLayoutUserPassword.isErrorEnabled = false
                        binding.inputLayoutUserEmail.error = "Введите почту пользователя"
                    }  else if (binding.inputUserEmail.text!!.isNotEmpty() && binding.inputUserPassword.text!!.isEmpty()) {
                        binding.inputLayoutUserEmail.isErrorEnabled = false
                        binding.inputLayoutUserPassword.isErrorEnabled = true
                        binding.inputLayoutUserPassword.error = "Введите пароль пользователя"
                    }
                }
            } else {
                Toast.makeText(this, "Вы не подключены к Интернету!", Toast.LENGTH_LONG).show()
            }
        }

        binding.buttonResetPassword.setOnClickListener {
            vibrate()
            if (isNetworkConnected()) {
                if (binding.inputUserEmail.text!!.isNotEmpty()) {
                    if (isValidEmail(binding.inputUserEmail.text!!.toString())) {
                        hideKeyboard()
                        val alertDialogResetPasswordBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
                        alertDialogResetPasswordBuilder.setTitle("Сброс Пароля")
                        alertDialogResetPasswordBuilder.setMessage("Отправка письма для сброса пароля на почту ${binding.inputUserEmail.text}")
                        alertDialogResetPasswordBuilder.setPositiveButton("Продолжить") { _, _ ->
                            vibrate()
                            MainActivity.firebaseAuth.sendPasswordResetEmail(binding.inputUserEmail.text.toString()).addOnCompleteListener(this) { resetPasswordTask ->
                                if (resetPasswordTask.isSuccessful) {
                                    Toast.makeText(this, "Письмо для сброса \n пароля отправлено!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this, "Не удалось отправить \n письмо для сброса пароля!", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        alertDialogResetPasswordBuilder.setNegativeButton("Отмена") { _, _ ->
                            vibrate()
                        }
                        val alertDialogResetPassword = alertDialogResetPasswordBuilder.create()
                        alertDialogResetPassword.show()
                    } else {
                        binding.inputLayoutUserEmail.isErrorEnabled = true
                        binding.inputLayoutUserEmail.error = "Неправильная почта"
                    }
                } else {
                    binding.inputLayoutUserEmail.isErrorEnabled = true
                    binding.inputLayoutUserEmail.error = "Введите почту пользователя"
                }
            } else {
                Toast.makeText(this, "Вы не подключены к Интернету!", Toast.LENGTH_LONG).show()
            }
        }

        binding.buttonPasswordToggle.setOnClickListener {
            vibrate()
            passwordToggle = !passwordToggle
            if (passwordToggle) {
                binding.textViewEmailPassword.text = getString(R.string.concatenated_text, binding.inputUserEmail.text!!.toString(),
                        binding.inputUserPassword.text!!.toString())
                binding.buttonPasswordToggle.setImageResource(R.drawable.ic_hide_password)
            } else {
                binding.textViewEmailPassword.text = getString(R.string.concatenated_text, binding.inputUserEmail.text!!.toString(),
                        hidePassword(binding.inputUserPassword.text!!.toString()))
                binding.buttonPasswordToggle.setImageResource(R.drawable.ic_show_password)
            }
        }

        binding.fabCreateUser.setOnClickListener {
            vibrate()
            val activity = Intent(this, SignUpActivity::class.java)
            startActivity(activity)
        }

        binding.buttonLogout.setOnClickListener {
            vibrate()
            if (isNetworkConnected()) {
                supportActionBar!!.title = "Вход"
                binding.inputLayoutUserEmail.visibility = View.VISIBLE
                binding.inputLayoutUserPassword.visibility = View.VISIBLE
                binding.buttonSignIn.visibility = View.VISIBLE
                binding.buttonResetPassword.visibility = View.VISIBLE
                binding.imageViewBigUser.visibility = View.GONE
                binding.layoutEmailPassword.visibility = View.GONE
                binding.layoutUserSettings.visibility = View.GONE
                MainActivity.editPreferences.putBoolean("UserLogged", false)
                MainActivity.editPreferences.putBoolean("TopicSubscribed", false).apply()
            } else {
                Toast.makeText(this, "Вы не подключены к Интернету!", Toast.LENGTH_LONG).show()
            }
        }

        binding.buttonChangeEmail.setOnClickListener {
            vibrate()
            val activity = Intent(this, SignUpActivity::class.java)
            activity.putExtra("LaunchReason", true)
            startActivity(activity)
        }

        binding.buttonChangePassword.setOnClickListener {
            vibrate()
            val activity = Intent(this, SignUpActivity::class.java)
            activity.putExtra("LaunchReason", false)
            startActivity(activity)
        }

        binding.buttonDeleteUser.setOnClickListener {
            vibrate()
            if (isNetworkConnected()) {
                val alertDialogDeleteUserBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
                alertDialogDeleteUserBuilder.setTitle("Удаление Пользователя")
                alertDialogDeleteUserBuilder.setMessage("Вы точно хотите удалить пользователя ${MainActivity.sharedPreferences.getString("UserEmail", "").toString()}?")
                alertDialogDeleteUserBuilder.setPositiveButton("Подтвердить") { _, _ ->
                    vibrate()
                    val alertDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(this)
                    val progressBarBinding : ProgressBarBinding = ProgressBarBinding.inflate(layoutInflater)
                    progressBarBinding.textViewProgress.text = "Удаляем пользователя ..."
                    alertDialogBuilder.setView(progressBarBinding.root)
                    val alertDialog = alertDialogBuilder.create()
                    alertDialog.setCanceledOnTouchOutside(false)
                    alertDialog.show()

                    val realtimeDatabase = FirebaseDatabase.getInstance().reference
                    realtimeDatabase.child("users").child(MainActivity.firebaseAuth.currentUser!!.uid).child("settings").removeValue()
                    realtimeDatabase.child("users").child(MainActivity.firebaseAuth.currentUser!!.uid).child("sendPhoto").removeValue()

                    val listAllTask : Task<ListResult> = Firebase.storage.reference.child(MainActivity.firebaseAuth.currentUser!!.uid).listAll()
                    listAllTask.addOnCompleteListener { result ->
                        val items: List<StorageReference> = result.result.items
                        if (result.result.items.isNotEmpty()) {
                            items.forEach { item ->
                                item.delete()
                            }
                            MainActivity.firebaseAuth.currentUser!!.reauthenticate(
                                EmailAuthProvider.getCredential(binding.inputUserEmail.text.toString(),
                                    binding.inputUserPassword.text.toString())).addOnCompleteListener { reauthTask ->
                                if (reauthTask.isSuccessful) {
                                    MainActivity.firebaseAuth.currentUser!!.delete().addOnCompleteListener { deleteUserTask ->
                                        if (deleteUserTask.isSuccessful) {
                                            binding.inputUserEmail.setText("")
                                            binding.inputUserPassword.setText("")
                                            MainActivity.editPreferences.putString("UserEmail", "")
                                            MainActivity.editPreferences.putString("UserPassword", "")
                                            Toast.makeText(this, "Пользователь удалён!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(this, "Не удалось удалить пользователя!", Toast.LENGTH_LONG).show()
                                        }
                                        supportActionBar!!.title = "Вход"
                                        binding.inputLayoutUserEmail.visibility = View.VISIBLE
                                        binding.inputLayoutUserPassword.visibility = View.VISIBLE
                                        binding.buttonSignIn.visibility = View.VISIBLE
                                        binding.buttonResetPassword.visibility = View.VISIBLE
                                        binding.imageViewBigUser.visibility = View.GONE
                                        binding.layoutEmailPassword.visibility = View.GONE
                                        binding.layoutUserSettings.visibility = View.GONE
                                        MainActivity.editPreferences.putBoolean(binding.inputUserEmail.text!!.toString(), false)
                                        MainActivity.editPreferences.putBoolean("UserLogged", false)
                                        MainActivity.editPreferences.putBoolean("TopicSubscribed", false).apply()
                                        alertDialog.cancel()
                                    }
                                } else {
                                    supportActionBar!!.title = "Вход"
                                    binding.inputLayoutUserEmail.visibility = View.VISIBLE
                                    binding.inputLayoutUserPassword.visibility = View.VISIBLE
                                    binding.buttonSignIn.visibility = View.VISIBLE
                                    binding.buttonResetPassword.visibility = View.VISIBLE
                                    binding.imageViewBigUser.visibility = View.GONE
                                    binding.layoutEmailPassword.visibility = View.GONE
                                    binding.layoutUserSettings.visibility = View.GONE
                                    MainActivity.editPreferences.putBoolean("UserLogged", false)
                                    MainActivity.editPreferences.putBoolean("TopicSubscribed", false).apply()
                                    Toast.makeText(this, "Не удалось переавторизироваться!", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                alertDialogDeleteUserBuilder.setNegativeButton("Отмена") { _, _ ->
                    vibrate()
                }
                val alertDialogDeleteUser = alertDialogDeleteUserBuilder.create()
                alertDialogDeleteUser.show()
            } else {
                Toast.makeText(this, "Вы не подключены\nк Интернету!", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val actionButtonsInflater = menuInflater
        actionButtonsInflater.inflate(R.menu.user_profile_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.buttonHome -> {
                vibrate()
                val activity = Intent(this, MainActivity::class.java)
                startActivity(activity)
                true
            }
            R.id.buttonESP32Settings -> {
                vibrate()
                val activity = Intent(this, ESP32SettingsActivity::class.java)
                startActivity(activity)
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

    private fun isValidEmail(inputText: CharSequence) : Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(inputText).matches()
    }

    private fun hidePassword(inputText: String) : String {
        var hiddenPassword = ""
        for (i in inputText.indices) {
            hiddenPassword += "•"
        }
        return hiddenPassword
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
                binding.buttonSignIn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING + HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
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