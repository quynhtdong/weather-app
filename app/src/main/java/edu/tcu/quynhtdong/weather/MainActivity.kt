package edu.tcu.quynhtdong.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.snackbar.Snackbar
import edu.tcu.bmei.weather.model.WeatherResponse
import edu.tcu.quynhtdong.weather.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var view: View
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var dialog: Dialog
    private lateinit var service: WeatherService
    private lateinit var weatherResponse: WeatherResponse

    val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Snackbar.make(view, R.string.location_permission_granted, Snackbar.LENGTH_SHORT).show()
                updateLocationAndWeatherRepeatedly()
            } else {
                Snackbar.make(view, R.string.location_permission_denied, Snackbar.LENGTH_LONG).show()
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Step 4: requestLocationPermission()
        //
        binding = ActivityMainBinding.inflate(layoutInflater)
        view = binding.root
        setContentView(view)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val retrofit = Retrofit.Builder().baseUrl(getString(R.string.base_url))
            .addConverterFactory(GsonConverterFactory.create()).build()

        service = retrofit.create(WeatherService::class.java)
        requestLocationPermission()
    }

    fun requestLocationPermission() {

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                    updateLocationAndWeatherRepeatedly()
                }

            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                val snackbar = Snackbar.make(
                    view,
                    R.string.location_permission_required,
                    Snackbar.LENGTH_INDEFINITE
                )

                snackbar.setAction("OK"){
                    requestPermissionLauncher.launch(
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                }.show()
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }

        }
    }

    private fun updateLocationAndWeatherRepeatedly() {
        lifecycleScope.launch(Dispatchers.IO) {
            while(true) {
                withContext(Dispatchers.Main) { updateLocationAndWeather() }
                delay(15000)
            }
        }

    }

    private suspend fun updateLocationAndWeather() {
        when(PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) -> {
                showInProgress()
                val cancellationTokenSource = CancellationTokenSource()
                var taskSuccessful = false

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null).addOnSuccessListener {
                    taskSuccessful = true
                    if(it != null){
                        updateWeather(it)
                    } else {
                        displayUpdateFailed()
                    }

                }
                withContext(Dispatchers.IO){
                    delay(10000)
                    if(!taskSuccessful){
                        cancellationTokenSource.cancel()
                        withContext(Dispatchers.Main) {
                            displayUpdateFailed()
                        }
                    }
                }
            }
        }
    }

    private fun updateWeather(location: Location) {
        val call = service.getWeather(
            location.latitude,
            location.longitude,
            getString(R.string.appid),
            "imperial"
        )

        call.enqueue(
            object: Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    val weatherResponseNullable = response.body()
                    if(weatherResponseNullable != null){
                        weatherResponse = weatherResponseNullable
                        displayWeather()
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    displayUpdateFailed()
                }
            }
        )
    }

    private fun displayWeather() {
        dialog.dismiss()
        binding.connectionTv.text = "Updated Just Now"
        binding.cityTv.text = weatherResponse.name
        binding.temperatureTv.text = getString(R.string.temperature, weatherResponse.main.temp)
        var desc = weatherResponse.weather[0].description.split(" ")
        var description = ""
        for(ch in desc) {
            description += ch.replaceFirstChar { c -> c.uppercase() } + " "
        }
        binding.descriptionTv.text = getString(R.string.description, description, weatherResponse.main.temp_max, weatherResponse.main.temp_min)
        binding.windDataTv.text = getString(R.string.wind_data, weatherResponse.wind.speed, weatherResponse.wind.deg, weatherResponse.wind.gust)
        binding.precipitationDataTv.text = getString(R.string.precipitation_data, weatherResponse.main.humidity, weatherResponse.clouds.all)

        when(weatherResponse.weather[0].icon) {
            "01d" -> binding.conditionIv.setImageResource(R.drawable.ic_01d)
            "01n" -> binding.conditionIv.setImageResource(R.drawable.ic_01n)
            "02d" -> binding.conditionIv.setImageResource(R.drawable.ic_02d)
            "02n" -> binding.conditionIv.setImageResource(R.drawable.ic_02n)
            "03d", "03n" -> binding.conditionIv.setImageResource(R.drawable.ic_03)
            "04d", "04n" -> binding.conditionIv.setImageResource(R.drawable.ic_04)
            "09d", "09n" -> binding.conditionIv.setImageResource(R.drawable.ic_09)
            "10d" -> binding.conditionIv.setImageResource(R.drawable.ic_10d)
            "10n" -> binding.conditionIv.setImageResource(R.drawable.ic_10n)
            "11d", "11n" -> binding.conditionIv.setImageResource(R.drawable.ic_11)
            "13d", "13n" -> binding.conditionIv.setImageResource(R.drawable.ic_13)
            "50d", "50n" -> binding.conditionIv.setImageResource(R.drawable.ic_50)

        }
        binding.otherDataTv.text = getString(R.string.other_data, weatherResponse.main.feels_like, weatherResponse.visibility* 0.00062137119, weatherResponse.main.pressure*0.02953)
        val riseTime = Date(weatherResponse.sys.sunrise.toLong() * 1000)
        var sunrise = SimpleDateFormat("hh:mm a").format(riseTime)
        val setTime = Date(weatherResponse.sys.sunset.toLong() * 1000)
        var sunset = SimpleDateFormat("hh:mm a").format(setTime)
        binding.sunDataTv.text = getString(R.string.sun_data, sunrise, sunset)
    }

    private fun displayUpdateFailed() {
        dialog.dismiss()
    }

    private fun showInProgress() {
        dialog = Dialog(this)
        dialog.setContentView(R.layout.in_progress)
        dialog.setCancelable(false)
    }
}