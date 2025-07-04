package com.example.weathernow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import java.text.SimpleDateFormat
import java.util.*
import java.net.URL
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val API: String = "9940c6b52513697e07e091ec423f83f4"
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val buttonForecast = findViewById<Button>(R.id.buttonForecast) //Buttons in the bottom
        buttonForecast.setOnClickListener {
            goToForecast()
        }
        val buttonForecast2 = findViewById<LinearLayout>(R.id.nextDaysContainer)
        buttonForecast2.setOnClickListener {
            goToForecast()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestLocation()
    }

    private fun goToForecast(){
        val forecastScreen = Intent(this, MainActivity2::class.java)
        startActivity(forecastScreen)
    } //go to the secondScreen

    private fun requestLocation(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                if (!addresses.isNullOrEmpty()) {
                    val city = addresses[0].locality ?:addresses[0].subAdminArea
                    val country = addresses[0].countryCode

                    if (city != null && country != null) {
                        val locationString = "$city,$country"
                        Log.d("Location", "City: $city, Country: $country")
                        fetchForecastWeather(locationString)
                    } else {
                        showError()
                    }
                } else {
                    showError()
                }
            } else {
                showError()
            }
        }
    }

    private fun fetchForecastWeather(city: String){
        val loader = findViewById<ProgressBar>(R.id.loader)
        val errorText = findViewById<TextView>(R.id.errortext)
        val mainContainer = findViewById<RelativeLayout>(R.id.mainContainer)

        loader.visibility = View.VISIBLE
        errorText.visibility = View.GONE
        mainContainer.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = URL("https://api.openweathermap.org/data/2.5/forecast?q=$city&units=metric&lang=pt_br&appid=$API")
                    .readText()

                withContext(Dispatchers.Main) {
                    updateUIForecast(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError()
                }
            }
        }
    }

    private fun updateUIForecast(response: String){
        try {
            val forecastData = JSONObject(response)
            val list = forecastData.getJSONArray("list")
            //Today data
            val address = forecastData.getJSONObject("city").getString("name")
            val current = list.getJSONObject(0)
            val currentTemp = current.getJSONObject("main").getDouble("temp").toInt().toString() + "°"
            val weatherDescription = current.getJSONArray("weather").getJSONObject(0).getString("description").replaceFirstChar { it.uppercase() }

            findViewById<TextView>(R.id.address).text = address
            findViewById<TextView>(R.id.temp).text = currentTemp
            findViewById<TextView>(R.id.status).text = weatherDescription

            //Preparing for "the loop"
            val dateTemps = mutableMapOf<String, MutableList<Double>>()

            //"the first loop"
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val dateTime = item.getString("dt_txt")
                val date = dateTime.substring(0, 10) // yyyy-MM-dd
                val temp = item.getJSONObject("main").getDouble("temp")

                if (!dateTemps.containsKey(date)) {
                    dateTemps[date] = mutableListOf()
                }
                dateTemps[date]?.add(temp)
            }

            //"the second loop"
            val sortedDates = dateTemps.keys.sorted()
            for (i in 0..2) {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, i)
                val dateFormat = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)

                val date = sortedDates.getOrNull(i) ?: continue
                val temps = dateTemps[date] ?: continue
                val min = temps.minOrNull()?.toInt() ?: 0
                val max = temps.maxOrNull()?.toInt() ?: 0

                val minTemp = resources.getIdentifier("min_temp$i", "id", packageName)
                val maxTemp = resources.getIdentifier("max_temp$i", "id", packageName)
                val idMin = resources.getIdentifier("temp_min$i", "id", packageName)
                val idMax = resources.getIdentifier("temp_max$i", "id", packageName)
                val idDay = resources.getIdentifier("day$i", "id", packageName)

                findViewById<TextView>(minTemp)?.text = "$min°"
                findViewById<TextView>(maxTemp)?.text = "/ $max°"
                findViewById<TextView>(idMin)?.text = "$min° Min"
                findViewById<TextView>(idMax)?.text = "$max° Max"
                findViewById<TextView>(idDay)?.text = dateFormat.replaceFirstChar { it.uppercase() }
            }

            findViewById<ProgressBar>(R.id.loader).visibility = View.GONE
            findViewById<RelativeLayout>(R.id.mainContainer).visibility = View.VISIBLE


        } catch (e: Exception) {
            Log.e("ForecastError", "Erro ao atualizar UI: ${e.message}", e)
            showError()
        }
    }

    private fun showError() {
        findViewById<ProgressBar>(R.id.loader).visibility = View.GONE
        findViewById<RelativeLayout>(R.id.mainContainer).visibility = View.GONE
        findViewById<TextView>(R.id.errortext).visibility = View.VISIBLE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocation()
        } else {
            showError()
        }
    }
}
