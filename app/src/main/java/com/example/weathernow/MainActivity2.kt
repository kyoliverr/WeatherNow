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
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity2 : AppCompatActivity()  {

    private val API: String = "100423194109345"
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        val goBackButton = findViewById<Button>(R.id.goBackButton)
        goBackButton.setOnClickListener {
            goBack()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestLocation()
    }

    //button in the left|top corner
    private fun goBack(){
        val mainScreen = Intent(this, MainActivity::class.java)
        startActivity(mainScreen)
    }

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
        val mainContainer = findViewById<RelativeLayout>(R.id.mainContainer2)

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

            //Data and preparations for THE LOOP :O
            val dateWeatherDescriptions = mutableMapOf<String, MutableList<String>>()
            val dateTemps = mutableMapOf<String, MutableList<Double>>()
            val dateWindSpeeds = mutableMapOf<String, MutableList<Double>>()

            //"THE LOOP 1" :O
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val dateTime = item.getString("dt_txt")
                val date = dateTime.substring(0, 10) // yyyy-MM-dd
                val temp = item.getJSONObject("main").getDouble("temp")
                val wind = item.getJSONObject("wind").getDouble("speed")
                val weather = item.getJSONArray("weather").getJSONObject(0).getString("description").replaceFirstChar { it.uppercase() }

                if(!dateWeatherDescriptions.containsKey(date)){
                    dateWeatherDescriptions[date] = mutableListOf()
                }
                dateWeatherDescriptions[date]?.add(weather)

                if (!dateTemps.containsKey(date)) {
                    dateTemps[date] = mutableListOf()
                }
                dateTemps[date]?.add(temp)

                if (!dateWindSpeeds.containsKey(date)) {
                    dateWindSpeeds[date] = mutableListOf()
                }
                dateWindSpeeds[date]?.add(wind)
            }
            val sortedDates = dateTemps.keys.sorted()

            //"THE LOOP 2" his little brother :O
            for (i in 0..4) {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, i)
                val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)
                val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault()).format(calendar.time)

                val date = sortedDates.getOrNull(i) ?: continue
                val weathers = dateWeatherDescriptions[date] ?: continue
                val temps = dateTemps[date] ?: continue
                val min = temps.minOrNull()?.toInt() ?: 0
                val max = temps.maxOrNull()?.toInt() ?: 0
                val winds = dateWindSpeeds[date] ?: continue
                val windAvg = winds.average()
                val formattedWind = String.format("%.1f km/h", windAvg)

                val idDay = resources.getIdentifier("day$i", "id", packageName)
                val idDate = resources.getIdentifier("date$i", "id", packageName)
                val idWeather = resources.getIdentifier("wind$i", "id", packageName)
                val idMin = resources.getIdentifier("temp_min$i", "id", packageName)
                val idMax = resources.getIdentifier("temp_max$i", "id", packageName)
                val idWind = resources.getIdentifier("wind$i", "id", packageName)

                findViewById<TextView>(idDay)?.text = dayFormat.replaceFirstChar { it.uppercase() }
                findViewById<TextView>(idDate)?.text = dateFormat
                findViewById<TextView>(idWeather)?.text = "$weathers"
                findViewById<TextView>(idMin)?.text = "$min° Min"
                findViewById<TextView>(idMax)?.text = "$max° Max"
                findViewById<TextView>(idWind)?.text = formattedWind

            }

            findViewById<ProgressBar>(R.id.loader).visibility = View.GONE
            findViewById<RelativeLayout>(R.id.mainContainer2).visibility = View.VISIBLE

        } catch (e: Exception) {
            Log.e("ForecastError", "Erro ao atualizar UI: ${e.message}", e)
            showError()
        }
    }

    //Function to show error message
    private fun showError() {
        findViewById<ProgressBar>(R.id.loader).visibility = View.GONE
        findViewById<RelativeLayout>(R.id.mainContainer2).visibility = View.GONE
        findViewById<TextView>(R.id.errortext).visibility = View.VISIBLE
    }
}
