package eu.tutorials.weatherapp

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import eu.tutorials.weatherapp.databinding.ActivityMainBinding
import eu.tutorials.weatherapp.models.WeatherResponse
import eu.tutorials.weatherapp.network.WeatherService
import kotlinx.coroutines.DelicateCoroutinesApi
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mLatitude : Double = 0.0
    private var mLongitude : Double = 0.0
    private var mProgressDialog : Dialog? = null
    private var binding : ActivityMainBinding? = null
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(
            this@MainActivity)
        mSharedPreferences = getSharedPreferences(
            Constants.PREFERENCE_NAME,
            Context.MODE_PRIVATE)

        setupUI()

        if(!isLocationEnabled()){
            Toast.makeText(this@MainActivity,
                "Your location is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else{
            Dexter.withContext(this)
                .withPermissions(
                    ACCESS_FINE_LOCATION,
                    ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            val weatherResponseJsonString = mSharedPreferences.getString(
                                Constants.WEATHER_RESPONSE_DATA,"")
                            if(weatherResponseJsonString.isNullOrEmpty()) {
                                requestLocationData()
                            }
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission." +
                                        "Please allow it is mandatory.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }


    }

    private fun getLocationWeatherDetails(latitude : Double, longitude : Double){
        if (Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service : WeatherService = retrofit
                .create<WeatherService>(WeatherService::class.java)

            val listCall : Call<WeatherResponse> = service.getWeather(
                latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID
            )


            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response.isSuccessful){
                        hideProgressDialog()
                        val weatherList : WeatherResponse = response.body()!!
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()
                        setupUI()
                        Log.i("Response Result","$weatherList")
                    } else {
                        when(response.code()){
                            400 -> Log.e("Error 400","Bad Connection")
                            404 -> Log.e("Error 404", "Not Found")
                            else -> Log.e("Error", "Generic Error")
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Response error", t.message.toString())
                }
            })
        }else{
            Toast.makeText(
                this@MainActivity,
                "You have not connected to the internet.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature." +
                    " It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }


    private fun isLocationEnabled(): Boolean {
        // This provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private val mLocationCallBack = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation : Location = locationResult.lastLocation!!
            mLatitude = mLastLocation.latitude
            mLongitude = mLastLocation.longitude

            Log.i("Current Latitude", "$mLatitude")
            Log.i("Current Longitude", "$mLongitude")
            getLocationWeatherDetails(mLatitude,mLongitude)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        showCustomProgressDialog()
        val mLocationRequest =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000)
                .setWaitForAccurateLocation(true)
                .setMinUpdateIntervalMillis(500)
                .setMaxUpdateDelayMillis(1000)
                .setMaxUpdates(1)
                .build()

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallBack, Looper.myLooper())
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this@MainActivity)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if (mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(
            Constants.WEATHER_RESPONSE_DATA,"")

        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)

            for (i in weatherList.weather.indices){
                binding?.tvMain?.text = weatherList.weather[i].main
                binding?.tvMainDescription?.text = weatherList.weather[i].description
                binding?.tvTemp?.text = buildString {
                    append(weatherList.main.temp.toString())
                    append(getUnit(application.resources.configuration.toString()))
                }
                binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
                binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset)
                binding?.tvMax?.text = buildString {
                    append(weatherList.main.temp_max.toString())
                    append(getUnit(application.resources.configuration.toString()))
                    append(" Max")
                }
                binding?.tvMin?.text = buildString {
                    append(weatherList.main.temp_min.toString())
                    append(getUnit(application.resources.configuration.toString()))
                    append(" Min")
                }
                binding?.tvSpeed?.text = weatherList.wind.speed.toString()
                binding?.tvName?.text = weatherList.name
                binding?.tvCountry?.text = weatherList.sys.country
                binding?.tvHumidity?.text = buildString {
                    append(weatherList.main.humidity.toString())
                    append(" %")
                }
                    when(weatherList.weather[i].icon){
                        "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                        "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                        "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                        "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                        "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                        "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                        "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                        "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                        "01n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                        "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                        "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                        "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                        "11n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                        "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                    }
            }
        }else{
            Log.e("Shared Preferences","Empty Or Null Json In Shared Preferences")
        }
    }

    private fun getUnit (unit: String):String{
        var value = "°C"
        if(unit == "US"  || "LR" == unit || "MM" == unit){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long) : String?{
        val date = Date(timex*1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                true}
            else -> super.onOptionsItemSelected(item)
        }
    }

}