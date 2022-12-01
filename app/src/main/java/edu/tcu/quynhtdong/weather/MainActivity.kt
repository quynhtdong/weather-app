package edu.tcu.quynhtdong.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
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
        binding.windCv
        setContentView(R.layout.activity_main)

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
        binding.cityTv.text = weatherResponse.name
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