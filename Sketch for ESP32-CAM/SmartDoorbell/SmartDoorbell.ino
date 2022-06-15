#include <WiFi.h> // библиотека для WiFi
#include <Firebase_ESP_Client.h> // библиотека для платформы Firebase

#include <addons/TokenHelper.h> // библиотека, которая предоставляет информацию во время создания токена
#include <addons/RTDBHelper.h> // аддон для работы с базой данных Realtime Database

#include <NTPClient.h> // библиотека для получения времени от NTP сервера
#include <WiFiUdp.h> // библиотека для WiFi Udp клиента

#include "SPIFFS.h" // библиотека для SPIFFS
#include "esp_wifi.h" // библиотека для расширенной работы с WiFi
#include "esp_system.h" // библиотека для системы ESP
#include "esp_camera.h" // библиотека для камеры
#include "driver/rtc_io.h" // библиотека для rtc

// библиотеки для управления детектором отключения питания
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

#define TIMEZONE 3 // ваш часовой пояс (если время не совпадает, добавьте, или отнимите единицу)
#define RESET_BUTTON_PIN 14 // пин кнопки для перехода в режим настройки параметров работы
#define DOORBELL_BUTTON_PIN 15 // пин кнопки звонка
#define CONNECT_TO_WIFI_MAX_ATTEMPTS 3 // максимальное количество попыток подключиться к WiFi сети

String ssidName = "";  // название WiFi сети
String ssidPass = "";  // пароль WiFi сети
String userEmail = ""; // почта пользователя
String userPass = "";  // пароль пользователя

int framesize = 3; // разрешение изображение
int whiteBalance = 0; // режим баланса белого
bool turnFlashOn = false; // включать ли вспышку при получении фотографии
bool flip = false; // будут ли переворачиваться фотографии
bool mirror = false; // будут ли отзеркаливаться фотографии
bool startSleep = false; // будет ли плата ESP32-CAM переходить в глубокий сон

bool changeStreamResultVariable = false; // переменная для того, чтобы каждый раз записывать данные, полученные через Realtime Database, в разные переменные
String streamResult[2] = {"", ""}; // массив для того, чтобы хранить предыдущие данные, полученные через Realtime Database, и сравнивать с полученными только что

bool notBeginStream = false; // переменная нужная для того, чтобы в переменную "streamStartedNow" не записывалось значение "true" после запуска слушателя
bool streamStartedNow = false; // переменная нужная для того, чтобы слушатель изменения данных не срабатывал при его запуске
bool setSettingsMode = false; // запущен ли режим настройки параметров работы
bool resetButtonFlag = false; // флажок кнопки для перехода в режим настройки параметров работы
unsigned long resetButtonMillis = 0; // время нажатия кнопки для перехода в режим настройки параметров работы в millis

RTC_DATA_ATTR int connectToWiFiAttempts = 0; // количество попыток подключиться к WiFi сети

WiFiServer server(80); // WiFi сервер для получения параметров работы

WiFiUDP ntpUDP; // WiFi Udp клиент
NTPClient timeClient(ntpUDP); // NTP клиент

FirebaseData firebaseData; // объект для управления данными в Firebase
FirebaseData firebaseStream; // слушатель изменения данных в Realtime Database
FirebaseAuth firebaseAuth; // система авторизации Firebase
FirebaseConfig firebaseConfig; // объект для настройки подключения к Firebase

void setup() {
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0); // выключаем детектор отключения питания
  Serial.begin(115200);

  if (!SPIFFS.begin(true)) {
    Serial.println("Не удалось монтировать SPIFFS :(");
    ESP.restart();
  }

  if (SPIFFS.exists("/Settings.txt")) { // если параметры работы настроены
    File settingsFile = SPIFFS.open("/Settings.txt", FILE_READ);
    if (settingsFile) {
      String settings;
      while (settingsFile.available()) settings = settingsFile.readString(); // записываем параметры работы
      settingsFile.close();

      Serial.println("Параметры работы: " + String(settings));

      // записываем индексы разделительных символов
      int secondHashIndex = settings.indexOf("&", settings.indexOf("&") + 1);
      int thirdHashIndex = settings.indexOf("&", secondHashIndex + 1);
      int fourthHashIndex = settings.indexOf("&", thirdHashIndex + 1);
      int fifthHashIndex = settings.indexOf("&", fourthHashIndex + 1);
      int sixthHashIndex = settings.indexOf("&", fifthHashIndex + 1);
      int seventhHashIndex = settings.indexOf("&", sixthHashIndex + 1);

      ssidName = settings.substring(0, settings.indexOf("&")); // записываем название WiFi сети
      ssidPass = settings.substring(settings.indexOf("&") + 1, secondHashIndex); // записываем пароль WiFi сети
      userEmail = settings.substring(secondHashIndex + 1, thirdHashIndex); // записываем почту пользователя
      userPass = settings.substring(thirdHashIndex + 1, fourthHashIndex); // записываем пароль пользователя
      framesize = (String(settings.charAt(fourthHashIndex + 1))).toInt(); // записываем разрешение изображения
      whiteBalance = (String(settings.charAt(fourthHashIndex + 2))).toInt(); // записываем режим баланса белого
      if (settings.substring(fourthHashIndex + 3, fifthHashIndex).indexOf("true") != -1) turnFlashOn = true; // записываем то, включать ли вспышку при получении фотографии
      if (settings.substring(fifthHashIndex + 1, sixthHashIndex).indexOf("true") != -1) flip = true; // записываем то, будут ли переворачиваться фотографии
      if (settings.substring(sixthHashIndex + 1, seventhHashIndex).indexOf("true") != -1) mirror = true; // записываем то, будут ли отзеркаливаться фотографии
      if (settings.substring(seventhHashIndex + 1, settings.length()).indexOf("true") != -1) startSleep = true; // записываем то, будет ли плата ESP32-CAM переходить в глубокий сон

      Serial.println("Название WiFi сети: " + String(ssidName));
      Serial.println("Пароль WiFi сети: " + String(ssidPass));
      Serial.println("Почта пользователя: " + String(userEmail));
      Serial.println("Пароль пользователя: " + String(userPass));
      Serial.println("Разрешение: " + String(framesize));
      Serial.println("Баланс белого: " + String(whiteBalance));
      Serial.println("Включать вспышку: " + String(turnFlashOn));
      Serial.println("Переворачивать фотографии: " + String(flip));
      Serial.println("Отзеркаливать фотографии: " + String(mirror));
      Serial.println("Переходить в режим сна: " + String(startSleep));

      pinMode(RESET_BUTTON_PIN, INPUT);

      if (!digitalRead(RESET_BUTTON_PIN)) { // если кнопка для перехода в режим настройки параметров работы нажата
        for (int i = 0; i <= 3020; i++) { // проверяем удерживается ли кнопка на протяжении 3 секунд
          if (resetSettings()) break; // если кнопка отпущена, выходим из цикла
          delay(1);
        }
      }

      if (!setSettingsMode) { // если режим настройки параметров работы не запущен
        if (!setupCamera()) {
          Serial.println("Не удалось запустить камеру :(");
          delay(5000);
          ESP.restart();
        }
        pinMode(4, OUTPUT); // настраиваем пин вспышки как выход
        esp_wifi_set_ps(WIFI_PS_NONE); // отключаем режим энергосбережения WiFi для получения более качественных фотографий

        if (startSleep) {
          WiFi.onEvent([](WiFiEvent_t event, WiFiEventInfo_t info) { // устанавливаем слушатель отключения от WiFi сети
            connectToWiFiAttempts++; // увеличиваем количество попыток подключиться к WiFi сети
            if (connectToWiFiAttempts < CONNECT_TO_WIFI_MAX_ATTEMPTS) { // если количество попыток не исчерпано
              Serial.println("Не удалось подключиться к WiFi сети :( Количество попыток: " + String(connectToWiFiAttempts));
              /* переходим в глубокий сон на маленькое время для того, чтобы плата ESP32-CAM перезагрузилась,
                но при этом количество попыток подключиться к WiFi сети сохранилось */
              esp_sleep_enable_timer_wakeup(10000);
              esp_deep_sleep_start();
            } else {
              Serial.println("Количество попыток подключиться к WiFi сети истекло! Переходим в глубокий сон ...");
              connectToWiFiAttempts = 0;
              startDeepSleep();
            }
          }, WiFiEvent_t::SYSTEM_EVENT_STA_DISCONNECTED);
        } else {
          WiFi.onEvent([](WiFiEvent_t event, WiFiEventInfo_t info) { // устанавливаем слушатель отключения от WiFi сети
            Serial.println("Отключились от WiFi сети :( Перезагружаем плату ...");
            ESP.restart();
          }, WiFiEvent_t::SYSTEM_EVENT_STA_DISCONNECTED);
        }

        Serial.println("Подключаемся к " + String(ssidName) + " ...");
        WiFi.mode(WIFI_STA); // переходим в режим подключения к WiFi сети
        WiFi.begin(ssidName.c_str(), ssidPass.c_str());

        if (WiFi.waitForConnectResult() == WL_CONNECTED) {
          Serial.print("Подключились к WiFi сети! Локальный IP адрес: "); Serial.println(WiFi.localIP());

          // устанавливаем почту и пароль пользователя
          firebaseAuth.user.email = userEmail.c_str();
          firebaseAuth.user.password = userPass.c_str();

          firebaseConfig.api_key = "AIzaSyCUAgfNYcwafJD5xpq_hj68Qa-5rYO4bNo"; // устанавливаем API ключ проекта на платформе Firebase
          // устанавливаем URL базы данных Realtime Database
          if (!startSleep) firebaseConfig.database_url = "https://smartwifidoorbell-default-rtdb.europe-west1.firebasedatabase.app/";
          firebaseConfig.token_status_callback = tokenStatusCallback; // устанавливаем слушатель подключения, или отключения от Firebase

          Firebase.reconnectWiFi(false);
          firebaseConfig.fcs.upload_buffer_size = 512; // устанавливаем размер буфера для загрузки файлов в хранилище файлов Firebase Storage
          // устанавливаем ключ сервера для отправки облачных сообщений через Firebase Cloud Messaging
          Firebase.FCM.setServerKey(String("AAAAgs9fJ5s:APA91bEbIPBnnyg9tkxzaur7b3iYm85IJoiuo0PSxH3TO8AdN1ds1wXJWKySnUPB41OrSwp8BsI") +
                                    String("-vuBaRj2yuzFH3iHIo81cuBsiVtPw5wB7ThXRtDBIk__UDHD__SQ1uDS-qNqYOg_7"));
          Firebase.begin(&firebaseConfig, &firebaseAuth); // подключаемся к Firebase

          if (startSleep) {
            takePhoto();
            sendMessage();
            sendPhoto();
            startDeepSleep();
          } else {
            pinMode(DOORBELL_BUTTON_PIN, INPUT);

            if (!Firebase.RTDB.beginStream(&firebaseStream, ("/users/" + firebaseAuth.token.uid).c_str())) { // запускаем слушатель изменения данных в Realtime Database
              Serial.printf("Не удалось установить слушатель изменения данных в Firebase, %s\n\n", firebaseStream.errorReason().c_str());
              ESP.restart();
            }

            timeClient.begin(); // запускаем NTP клиент
            timeClient.setTimeOffset(TIMEZONE * 3600); // устанавливаем часовой пояс
          }
        } else if (WiFi.waitForConnectResult() == WL_CONNECT_FAILED) {
          Serial.println("Не удалось подключиться к WiFi сети :(");
        }
      }
    } else {
      Serial.println("Не удалось открыть файл с параметрами работы :(");
      ESP.restart();
    }
  } else {
    WiFi.mode(WIFI_AP); // переходим в режим WiFi точки
    WiFi.softAP("Умный Звонок", "", 1, false, 1); // запускаем WiFi точку для настройки параметров работы (максимум одно подключение)
    server.begin(); // запускаем WiFi сервер
    setSettingsMode = true;
    Serial.println("Перешли в режим настройки параметров работы!");
  }
}

void loop() {
  if (setSettingsMode) {
    WiFiClient client = server.available(); // получаем WiFi клиент
    if (!client) return; // если пользователь не подключился к WiFi точке, выходим

    while (!client.available()) delay(1); // ждём пока клиент получит данные
    String request = client.readStringUntil('\r'); // записываем данные, полученные от клиента

    if (request.indexOf("&") != -1) { // если получены параметры работы
      File settingsFile = SPIFFS.open("/Settings.txt", FILE_WRITE);
      if (settingsFile) {
        String settings = request.substring(5, request.indexOf(" HTTP/1.1")); // записываем параметры работы
        Serial.println("Параметры работы: " + String(settings));
        if (settingsFile.print(settings)) {
          Serial.println("Файл с параметрами работы сохранён!");
          settingsFile.close();
          server.stop();
          WiFi.softAPdisconnect(true);
          ESP.restart();
        } else {
          Serial.println("Не удалось сохранить файл с параметрами работы :(");
          settingsFile.close();
        }
      } else {
        Serial.println("Не удалось открыть файл с параметрами работы :(");
        return;
      }
    } else {
      client.stop();
      return;
    }
  } else {
    if (WiFi.status() == WL_CONNECTED && Firebase.ready()) {
      if (!Firebase.RTDB.readStream(&firebaseStream)) Serial.printf("Не удалось прочитать слушатель изменения данных, %s\n", firebaseStream.errorReason().c_str());
      if (firebaseStream.streamTimeout()) {
        Serial.println("Таймаут слушателя изменения данных истёк");
        if (!firebaseStream.httpConnected()) Serial.printf("Код ошибки слушателя изменения данных: %d, причина: %s", firebaseStream.httpCode(), firebaseStream.errorReason().c_str());
      }

      if (firebaseStream.streamAvailable()) { // если слушатель изменения данных сработал, или, проще говоря, получена команда от пользователя
        if (streamStartedNow) { // если слушатель изменения данных в Realtime Database сработал не при его запуске
          String result = firebaseStream.to<String>(); // записываем данные, полученные через базу данных Realtime Database
          // делаем так, чтобы данные, полученные через Realtime Database, записались в другую переменную
          changeStreamResultVariable = !changeStreamResultVariable;
          if (changeStreamResultVariable) {
            streamResult[0] = result;
          } else {
            streamResult[1] = result;
          }
          if (streamResult[0] != streamResult[1]) { // если предыдущие данные, полученные через Realtime Database, отличаются от полученных только что
            Firebase.RTDB.endStream(&firebaseStream); // останавливаем слушатель изменения данных для того, чтобы в дальнейшем можно было загрузить фотографию в Firebase Storage
            if (result.indexOf("Ё") == -1) { // если получен запрос на загрузку фотографии в хранилище файлов Firebase Storage
              takePhoto();

              while (!timeClient.update()) timeClient.forceUpdate(); // обновляем данные от NTP клиента
              String currentTime = String(timeClient.getFormattedTime()).substring(0, 5); // записываем отфарматированное время
              String date = timeClient.getFormattedDate(); // записываем отфарматированную дату
              String years = date.substring(0, 4);
              String month = date.substring(5, 7);
              String days = date.substring(8, 10);

              String imageName = "/" + String(currentTime) + " " + String(days) + "." + String(month) + "." + String(years) + ".jpg";

              Serial.println("Название фото: " + String(imageName) + "\nЗагружаем фото в Firebase ...");
              // загружаем фотографию в хранилище файлов Firebase Storage
              if (Firebase.Storage.upload(&firebaseData, "smartwifidoorbell.appspot.com", "/Photo.jpg", mem_storage_type_flash,
                                          ((firebaseAuth.token.uid).c_str() + imageName).c_str(), "image/jpg")) {
                Serial.println("Фото загружено в Firebase!");
                // записываем в базу данных Realtime Database ссылку на скачивание фотографии из Firebase Storage
                if (!Firebase.RTDB.setString(&firebaseData, ("/users/" + firebaseAuth.token.uid).c_str(), String(firebaseData.downloadURL().c_str())))
                  Serial.println("Не удалось сохранить ссылку на скачивание фотографии в Firebase :( Причина ошибки: " + String(firebaseData.errorReason().c_str()));
              } else {
                Serial.println("Не удалось загрузить фото в Firebase :( Причина ошибки: " + String(firebaseData.errorReason().c_str()));
                // записываем в базу данных Realtime Database то, что фотографию загрузить не удалось
                if (!Firebase.RTDB.setString(&firebaseData, ("/users/" + firebaseAuth.token.uid).c_str(), "error"))
                  Serial.println("Не удалось сохранить то, что не удалось загрузить фото в Firebase :( Причина ошибки: " + String(firebaseData.errorReason().c_str()));
              }
              // запускаем слушатель изменения данных в Realtime Database
              if (!Firebase.RTDB.beginStream(&firebaseStream, ("/users/" + firebaseAuth.token.uid).c_str()))
                Serial.printf("Не удалось установить слушатель изменения данных в Firebase, %s\n", firebaseStream.errorReason().c_str());
              streamStartedNow = false;
              notBeginStream = true;
            } else { // если получен запрос на изменение параметров работы
              if (!Firebase.RTDB.setString(&firebaseData, ("/users/" + firebaseAuth.token.uid).c_str(), "0000"))
                Serial.println("Не удалось сбросить параметры работы в Firebase :( Причина ошибки: " + String(firebaseData.errorReason().c_str()));
              File settingsFile = SPIFFS.open("/Settings.txt", FILE_WRITE);
              if (settingsFile) {
                result.replace("Ё", ""); // удаляем сортировочный символ, который обозначает параметры работы
                Serial.println("Параметры работы: " + String(result));
                if (settingsFile.print(result)) {
                  Serial.println("Файл с параметрами работы сохранён!");
                  settingsFile.close();
                  ESP.restart();
                } else {
                  Serial.println("Не удалось записать файл с параметрами работы :(");
                  settingsFile.close();
                }
              } else {
                Serial.println("Не удалось открыть файл с параметрами работы :(");
                return;
              }
            }
          }
        }
        if (!notBeginStream) {
          streamStartedNow = true;
        } else {
          notBeginStream = false;
        }
      }

      if (!digitalRead(DOORBELL_BUTTON_PIN) && !resetButtonFlag && millis() - resetButtonMillis >= 150) { // если кнопка удерживается
        resetButtonMillis = millis(); // записываем время нажатия кнопки
        Firebase.RTDB.endStream(&firebaseStream); // останавливаем слушатель изменения данных для того, чтобы в дальнейшем можно было загрузить фотографию в Firebase Storage
        sendMessage();
        takePhoto();
        sendPhoto();
        // запускаем слушатель изменения данных в Realtime Database
        if (!Firebase.RTDB.beginStream(&firebaseStream, ("/users/" + firebaseAuth.token.uid).c_str())) {
          Serial.printf("Не удалось установить слушатель изменения данных в Firebase, %s\n\n", firebaseStream.errorReason().c_str());
          ESP.restart();
        }
        streamStartedNow = false;
      }
    }
  }
}

// функция для получения и сохранения фотографии в SPIFFS
void takePhoto() {
  camera_fb_t * fb = NULL; // создаём объект для фотографии
  unsigned int imageSize = 0; // размер фотографии в байтах
  do { // делаем фотографию пока она не будет получена правильно
    Serial.println("Получаем фото ...");
    digitalWrite(4, turnFlashOn); // включаем, или не включаем вспышку
    delay(50);

    if (fb) esp_camera_fb_return(fb);
    fb = esp_camera_fb_get(); // записываем фотографию в буфер
    digitalWrite(4, LOW); // выключаем вспышку

    File photoFile = SPIFFS.open("/Photo.jpg", FILE_WRITE);
    if (photoFile) {
      photoFile.write(fb->buf, fb->len); // записываем фотографию в SPIFFS
      imageSize = photoFile.size();
      Serial.println("Размер: " + String(imageSize) + " байт");
    } else {
      Serial.println("Не удалось открыть файл для фото :(");
      ESP.restart();
    }
    photoFile.close();
  } while (imageSize == 0);
}

// функция для отправки облачного сообщения через Firebase Cloud Messaging и загрузки фотографии в хранилище файлов Firebase Storage
void sendMessage() {
  Serial.println("Отправляем облачное сообщение в Firebase ...");
  FCM_Legacy_HTTP_Message message;
  message.targets.to = String("/topics/") + (firebaseAuth.token.uid).c_str(); // топик на который нужно отправить сообщение

  message.options.time_to_live = "1000"; // как долго будет отображаться уведомление
  message.options.priority = "high"; // приоритет уведомления

  message.payloads.notification.title = "Умный Звонок"; // заголовок уведомления
  message.payloads.notification.body = "Получен сигнал звонка"; // текст уведомления
  message.payloads.notification.icon = "myicon"; // иконка уведомления (будет иконка приложения)
  message.payloads.notification.click_action = "OPEN_MAIN_ACTIVITY"; /* название действия, которое нужно выполнить при нажатии
                                                                        на уведомление (В нашем случае будет открываться главный экран) */

  if (Firebase.FCM.send(&firebaseData, &message)) { // отправляем облачное сообщение
    Serial.printf("Сообщение отправлено! \n%s\n", Firebase.FCM.payload(&firebaseData).c_str());
  } else {
    Serial.println("Не удалось отправить сообщение :( Причина ошибки: " + String(firebaseData.errorReason()));
  }
}

void sendPhoto() {
  timeClient.begin(); // запускаем NTP клиент
  timeClient.setTimeOffset(TIMEZONE * 3600); // устанавливаем часовой пояс

  while (!timeClient.update()) timeClient.forceUpdate(); // обновляем данные от NTP клиента
  String currentTime = String(timeClient.getFormattedTime()).substring(0, 5); // записываем отфарматированное время
  String date = timeClient.getFormattedDate(); // записываем отфарматированную дату
  String years = date.substring(0, 4);
  String month = date.substring(5, 7);
  String days = date.substring(8, 10);

  String imageName = "/" + String(currentTime) + " " + String(days) + "." + String(month) + "." + String(years) + ".jpg";

  Serial.println("Название фото: " + String(imageName) + "\nЗагружаем фото в Firebase ...");
  // загружаем фотографию в хранилище файлов Firebase Storage
  if (Firebase.Storage.upload(&firebaseData, "smartwifidoorbell.appspot.com", "/Photo.jpg", mem_storage_type_flash,
                              ((firebaseAuth.token.uid).c_str() + imageName).c_str(), "image/jpg")) {
    Serial.println("Фото загружено в Firebase!");
  } else {
    Serial.println("Не удалось загрузить фото в Firebase :(");
  }
}

bool setupCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = 5;
  config.pin_d1 = 18;
  config.pin_d2 = 19;
  config.pin_d3 = 21;
  config.pin_d4 = 36;
  config.pin_d5 = 39;
  config.pin_d6 = 34;
  config.pin_d7 = 35;
  config.pin_xclk = 0;
  config.pin_pclk = 22;
  config.pin_vsync = 25;
  config.pin_href = 23;
  config.pin_sscb_sda = 26;
  config.pin_sscb_scl = 27;
  config.pin_pwdn = 32;
  config.pin_reset = -1;
  config.xclk_freq_hz = 20000000; // частота работы камеры
  config.pixel_format = PIXFORMAT_JPEG; // формат изображения (PIXFORMAT_ + YUV422|GRAYSCALE|RGB565|JPEG)
  config.frame_size = FRAMESIZE_SVGA; // разрешение изображения (FRAMESIZE_ + UXGA|SXGA|XGA|HD|SVGA|VGA|HVGA|CIF|QVGA|240X240|HQVGA|QCIF|QQVGA|96X96)
  config.jpeg_quality = 12; // качество изображения (1 - 63, чем ниже, тем лучше)
  config.fb_count = 1; // максимальное количество фотографий в буфере

  esp_err_t err = esp_camera_init(&config); // настраиваем камеру
  if (err != ESP_OK) {
    Serial.printf("Возникла следующая ошибка камеры: 0x%x ", err);
    return false;
  }

  sensor_t * camera = esp_camera_sensor_get();
  // настраиваем разрешение изображения
  if (framesize == 0) {
    camera->set_framesize(camera, FRAMESIZE_UXGA);
  } else if (framesize == 1) {
    camera->set_framesize(camera, FRAMESIZE_SXGA);
  } else if (framesize == 2) {
    camera->set_framesize(camera, FRAMESIZE_XGA);
  } else if (framesize == 3) {
    camera->set_framesize(camera, FRAMESIZE_SVGA);
  } else if (framesize == 4) {
    camera->set_framesize(camera, FRAMESIZE_VGA);
  } else if (framesize == 5) {
    camera->set_framesize(camera, FRAMESIZE_CIF);
  }  else if (framesize == 6) {
    camera->set_framesize(camera, FRAMESIZE_QVGA);
  }

  camera->set_wb_mode(camera, whiteBalance); // настраиваем режим баланса белого
  if (flip) {
    camera->set_vflip(camera, 1); // настраиваем то, будут ли переворачиваться фотографии
  }
  if (mirror) {
    camera->set_hmirror(camera, 1); // настраиваем то, будут ли отзеркаливаться фотографии
  }
  delay(500);
  return true;
}

// функция для проверки того, удерживается ли кнопка для перехода в режим настройки параметров работы дольше 3 секунд
bool resetSettings() {
  bool resetButtonState = !digitalRead(RESET_BUTTON_PIN);
  if (resetButtonState && !resetButtonFlag && millis() - resetButtonMillis >= 100) { // если кнопка удерживается
    resetButtonMillis = millis(); // записываем время нажатия кнопки
    resetButtonFlag = true; // поднимаем флажок
    Serial.println("Кнопка сброса настроек нажата!");
  }

  if (!resetButtonState && resetButtonFlag && millis() - resetButtonMillis >= 250) { // если кнопка отпущена
    resetButtonMillis = millis(); // записываем время отпускания кнопки
    resetButtonFlag = false; // опускаем флажок
    Serial.println("Кнопка сброса настроек отпущена!");
    return false;
  }

  if (resetButtonState && resetButtonFlag && millis() - resetButtonMillis >= 3000) { // если кнопка удерживается более 3 секунд
    connectToWiFiAttempts = 0;
    WiFi.mode(WIFI_AP); // переходим в режим WiFi точки
    WiFi.softAP("Умный Звонок", "", 1, false, 1); // запускаем WiFi точку для настройки параметров работы (максимум одно подключение)
    server.begin(); // запускаем WiFi сервер
    setSettingsMode = true;
    Serial.println("Перешли в режим настройки параметров работы!");
    return true;
  }
  return false;
}

void startDeepSleep() {
  digitalWrite(4, LOW); // выключаем вспышку
  rtc_gpio_hold_en(GPIO_NUM_4); // делаем так, чтобы вспышка не включалась в режиме глубокого сна
  esp_sleep_enable_ext0_wakeup((gpio_num_t)DOORBELL_BUTTON_PIN, 0); // устанавливаем пин для выхода из глубокого сна
  esp_deep_sleep_start(); // переходим в глубокий сон
}
