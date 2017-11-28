package com.quixom.apps.weatherstream

import android.annotation.TargetApi
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.support.annotation.RequiresApi
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.location.places.ui.PlaceAutocomplete
import com.quixom.apps.weatherstream.Methods.Companion.isNetworkConnected
import com.quixom.apps.weatherstream.Methods.Companion.promptSpeechInput
import com.quixom.apps.weatherstream.adapters.LocationHistoryAdapter
import com.quixom.apps.weatherstream.dbconfig.UpgradeData
import com.quixom.apps.weatherstream.fragments.MainFragment
import com.quixom.apps.weatherstream.model.LocationSearchHistory
import com.quixom.apps.weatherstream.model.WeatherData
import com.quixom.apps.weatherstream.model.WeatherForecastData
import com.quixom.apps.weatherstream.slidingmenu.SlidingMenu
import com.quixom.apps.weatherstream.utilities.*
import com.quixom.apps.weatherstream.webservice.APIParameters
import com.quixom.apps.weatherstream.webservice.NetworkConfig
import com.raizlabs.android.dbflow.sql.language.Delete
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.leftmenu.*
import kotlinx.android.synthetic.main.settingmenu.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*


class MainActivity : AppCompatActivity(), View.OnLongClickListener, View.OnClickListener {

    var slidingMenuLeft: SlidingMenu? = null
    private var slidingMenuRight: SlidingMenu? = null
    private var fragmentContainer: FrameLayout? = null
    private var fragmentUtil: FragmentUtil? = null
    lateinit var preferenceUtil: PreferenceUtil
    lateinit var errorHandler: com.quixom.apps.weatherstream.webservice.ErrorHandler
    var lists = ArrayList<LocationSearchHistory>()

    @TargetApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceUtil = PreferenceUtil(this@MainActivity)
        ThemeSwitcher.onActivityCreateSetTheme(this)
        if (preferenceUtil.getBooleanPref(preferenceUtil.IS_APP_THEME_DAY)) {
            this@MainActivity.setTheme(R.style.AppTheme)
        } else {
            this@MainActivity.setTheme(R.style.NightModeTheme)
        }

        setContentView(R.layout.activity_main)
        errorHandler = com.quixom.apps.weatherstream.webservice.ErrorHandler(this@MainActivity)

        fragmentUtil = FragmentUtil(this@MainActivity)

        fragmentContainer = findViewById(R.id.fl_fragment_container)

        initSlideMenuLeft()
        initSlideMenuRight()

        ivSpeechToSearchMenu.setOnClickListener(this)
        ivSearchLocationIC.setOnClickListener(this)
        tvLocationSearchText.setOnClickListener(this)
        tvClearSearch.setOnClickListener(this)

        // Temperature switch
        rbCelsius.setOnClickListener(this)
        rbFahrenheit.setOnClickListener(this)

        // Speed switch
        rbMS.setOnClickListener(this)
        rbMPH.setOnClickListener(this)

        // Notification switch
        rbOnNotification.setOnClickListener(this)
        rbOffNotification.setOnClickListener(this)

        // Air, Sea level, Ground level pressure switch
        rbHPA.setOnClickListener(this)
        rbInHg.setOnClickListener(this)

        // Theme Mode
        rbDay.setOnClickListener(this)
        rbNight.setOnClickListener(this)

        /** Launch Main Fragment */
        fragmentUtil!!.clearBackStackFragmets()
        fragmentUtil!!.replaceFragment(MainFragment(), false, false)


        /*** Init RecyclerView here */
        val layoutManager = LinearLayoutManager(this@MainActivity)
        rvMenuLocationList.layoutManager = layoutManager

        val weatherData: WeatherData? = WeatherData.getLocationBasedWeatherDetails()
        if (weatherData == null && intent.extras != null && intent.extras != null) {
            var latitude: Double = intent.extras.getDouble(KeyUtil.LATITUDE_VALUE)
            var longitude: Double = intent.extras.getDouble(KeyUtil.LONGITUDE_VALUE)
            if (latitude != 0.0 && longitude != 0.0) {
                callSearchLocationApi(latitude, longitude)
            } else {
                WeatherStreamCallbackManager.updateHomeScreenData(1)
            }
        }

        // Fetch searched location list from database.
        val searchedLocation: List<LocationSearchHistory> = LocationSearchHistory.getSearchedLocationList()
        if (searchedLocation.isNotEmpty()) {
            tvRecentSearch.visibility = View.VISIBLE
            tvClearSearch.visibility = View.VISIBLE
            for (searchedLC in searchedLocation) {
                lists.add(LocationSearchHistory(0, searchedLC.cityName, searchedLC.countyName, searchedLC.weatherType, searchedLC.temperature))
            }
            Collections.reverse(lists)
            rvMenuLocationList.adapter = LocationHistoryAdapter(preferenceUtil, lists, this@MainActivity)
        } else {
            tvRecentSearch.visibility = View.GONE
            tvClearSearch.visibility = View.GONE
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (supportFragmentManager.backStackEntryCount == 0) {
            finish()
        }

        if (slidingMenuLeft?.isMenuShowing!!) {
            slidingMenuLeft?.toggle()
        }

        if (slidingMenuRight?.isMenuShowing!!) {
            slidingMenuRight?.toggle()
        }
    }

    /***
     * Method for initialise slide menu drawer (Setting)
     * */
    fun initSlideMenuLeft() {

        slidingMenuLeft = SlidingMenu(this)
        slidingMenuLeft?.mode = SlidingMenu.LEFT
        slidingMenuLeft?.touchModeAbove = SlidingMenu.RIGHT
        slidingMenuLeft?.setShadowWidthRes(R.dimen.shadow_width)
        slidingMenuLeft?.setShadowDrawable(R.drawable.shadow)
        slidingMenuLeft?.setBehindOffsetRes(R.dimen.slidingmenu_offset)
        slidingMenuLeft?.setFadeDegree(0.8f)
        slidingMenuLeft?.attachToActivity(this, SlidingMenu.SLIDING_CONTENT)
        slidingMenuLeft?.setBehindWidth(850)
        slidingMenuLeft?.setMenu(R.layout.leftmenu)
        slidingMenuLeft?.isSlidingEnabled
    }

    /***
     * Method for Open/Close slide menu drawer */
    fun toggleSlideMenuLeft() {
        /*if (slidingMenuRight?.isMenuShowing!!) {
            slidingMenuRight?.toggle(true)
        }*/
        slidingMenuLeft?.toggle()
    }

    /***
     * Method for initialise slide menu drawer
     * */
    fun initSlideMenuRight() {

        slidingMenuRight = SlidingMenu(this)
        slidingMenuRight?.mode = SlidingMenu.RIGHT
        slidingMenuRight?.touchModeAbove = SlidingMenu.TOUCHMODE_NONE
        slidingMenuRight?.setShadowWidthRes(R.dimen.shadow_width)
        slidingMenuRight?.setShadowDrawable(R.drawable.shadow_right)
        slidingMenuRight?.setBehindOffsetRes(R.dimen.slidingmenu_offset)
        slidingMenuRight?.setFadeDegree(0.8f)
        slidingMenuRight?.attachToActivity(this, SlidingMenu.SLIDING_CONTENT)
        slidingMenuRight?.setBehindWidth(650)
        slidingMenuRight?.setMenu(R.layout.settingmenu)
        slidingMenuRight?.isSlidingEnabled

        // Set Notification switch status
        if (preferenceUtil.getBooleanPref(preferenceUtil.IS_NOTIFICATION_ON)) {
            rbOnNotification.isChecked = true
        } else {
            rbOffNotification.isChecked = true
        }

        // Set Temperature switch status
        if (preferenceUtil.getBooleanPref(preferenceUtil.IS_TEMPERATURE_UNIT_CELCIUS)) {
            rbCelsius.isChecked = true
        } else {
            rbFahrenheit.isChecked = true
        }

        // Set Speed switch status
        if (preferenceUtil.getBooleanPref(preferenceUtil.IS_SPEED_UNIT_METERS)) {
            rbMS.isChecked = true
        } else {
            rbMPH.isChecked = true
        }

        // Set Theme switch
        if (preferenceUtil.getBooleanPref(preferenceUtil.IS_APP_THEME_DAY)) {
            rbDay.isChecked = true
        } else {
            rbNight.isChecked = true
        }
    }

    /***
     * Method for Open/Close slide setting drawer */
    fun toggleSlideMenuRight() {
        if (slidingMenuLeft?.isMenuShowing!!) {
            slidingMenuLeft?.toggle()
        }
        slidingMenuRight?.toggle()
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onClick(view: View?) {
        when (view) {
            ivSpeechToSearchMenu -> {
                Methods.avoidDoubleClicks(ivSpeechToSearchMenu)
                promptSpeechInput(this@MainActivity)
            }
            tvLocationSearchText -> {
                Methods.avoidDoubleClicks(tvLocationSearchText)
                showGoogleAutoLocationSearch("")
            }
            rbOnNotification -> {
                preferenceUtil.setBooleanPref(preferenceUtil.IS_NOTIFICATION_ON, true)
            }
            rbOffNotification -> {
                preferenceUtil.setBooleanPref(preferenceUtil.IS_NOTIFICATION_ON, false)
            }
            rbCelsius -> {
                preferenceUtil.setBooleanPref(preferenceUtil.IS_TEMPERATURE_UNIT_CELCIUS, true)
                WeatherStreamCallbackManager.updateHomeScreenData(3)
            }
            rbFahrenheit -> {
                preferenceUtil.setBooleanPref(preferenceUtil.IS_TEMPERATURE_UNIT_CELCIUS, false)
                WeatherStreamCallbackManager.updateHomeScreenData(3)
            }
            rbMS -> {
                preferenceUtil.setBooleanPref(preferenceUtil.IS_SPEED_UNIT_METERS, true)
                WeatherStreamCallbackManager.updateHomeScreenData(3)
            }
            rbMPH -> {
                preferenceUtil.setBooleanPref(preferenceUtil.IS_SPEED_UNIT_METERS, false)
                WeatherStreamCallbackManager.updateHomeScreenData(3)
            }
            rbHPA -> {
                preferenceUtil.setBooleanPref(preferenceUtil.IS_AIR_PRESSURE_HPA, true)
                WeatherStreamCallbackManager.updateHomeScreenData(3)
            }
            rbInHg -> {
                preferenceUtil.setBooleanPref(preferenceUtil.IS_AIR_PRESSURE_HPA, false)
                WeatherStreamCallbackManager.updateHomeScreenData(3)
            }
            rbDay -> {
                preferenceUtil.setBooleanPref(preferenceUtil.IS_APP_THEME_DAY, true)
                ThemeSwitcher.changeToTheme(this, KeyUtil.THEME_DEFAULT)
            }
            rbNight -> {
                preferenceUtil.setBooleanPref(preferenceUtil.IS_APP_THEME_DAY, false)
                ThemeSwitcher.changeToTheme(this, KeyUtil.THEME_NIGHT)
            }
            tvClearSearch -> {
                UpgradeData.clearApplicationData()
                UpgradeData.clearSearchHistory()

                val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notificationManager != null && notificationManager.activeNotifications.isNotEmpty()) {
                    notificationManager.cancelAll()
                }

                tvClearSearch.visibility = View.GONE
                tvRecentSearch.visibility = View.GONE

                rvMenuLocationList?.adapter?.notifyDataSetChanged()
                toggleSlideMenuLeft()

                WeatherStreamCallbackManager.updateHomeScreenData(4)
            }
        }
    }

    override fun onLongClick(p0: View?): Boolean = false

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {

            KeyUtil.REQ_CODE_SPEECH_INPUT -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    showGoogleAutoLocationSearch(result[0])
                }
            }

            KeyUtil.PLACE_AUTOCOMPLETE_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val place = PlaceAutocomplete.getPlace(this, data)
                    callSearchLocationApi(place.latLng.latitude, place.latLng.longitude)
                } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                    Methods.hideKeyboard(this@MainActivity)
                    val status = PlaceAutocomplete.getStatus(this, data)
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    Methods.hideKeyboard(this@MainActivity)
                    // The user canceled the operation.
                }
            }
        }
    }

    fun showGoogleAutoLocationSearch(locationName: String) {
        try {
            val intent = PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY).build(this)
            intent.putExtra("initial_query", locationName)
            startActivityForResult(intent, KeyUtil.PLACE_AUTOCOMPLETE_REQUEST_CODE)
        } catch (e: GooglePlayServicesRepairableException) {
            e.printStackTrace()
        } catch (e: GooglePlayServicesNotAvailableException) {
            e.printStackTrace()
        }
    }

    /***
     * API Method for get the weather information of specific location
     * */
    var locationSearchCall: Call<WeatherData>? = null

    fun callSearchLocationApi(lat: Double, lon: Double) = if (isNetworkConnected(this@MainActivity)) {

        val hashMap = APIParameters.getParam()
        hashMap.put(APIParameters.LocationSearch.lat, "" + lat)
        hashMap.put(APIParameters.LocationSearch.lon, "" + lon)
        hashMap.put(APIParameters.LocationSearch.type, KeyUtil.TYPES_ACCURATE)
        hashMap.put(APIParameters.LocationSearch.units, "" + KeyUtil.UNITS_METRIC)

        locationSearchCall = NetworkConfig.getWebApis().getWeatherDetail(APIParameters.KEY_OPEN_WEATHER_MAP_KEY, hashMap)
        locationSearchCall!!.enqueue(object : Callback<WeatherData> {

            @RequiresApi(Build.VERSION_CODES.N)
            override fun onResponse(call: Call<WeatherData>, response: Response<WeatherData>) {
                if (call.isCanceled)
                    return

                if (response != null) {
                    if (response.isSuccessful) {
                        if (response.body() != null) {
                            if (response.body()?.cod == 200) {
                                Methods.hideKeyboard(this@MainActivity)

                                UpgradeData.clearApplicationData()

                                val weatherDetail: WeatherData = response.body()!!
                                val innerWeatherDetail: Array<WeatherData.Weather>? = response.body()!!.weather
                                val sysWeatherDetail: WeatherData.Sys = response.body()!!.sys!!
                                val coordDetail: WeatherData.Coord = response.body()!!.coord!!
                                val mainWeatherDetail: WeatherData.Main = response.body()!!.main!!
                                val windWeatherDetail: WeatherData.Wind = response.body()!!.wind!!
                                val cloudsWeatherDetail: WeatherData.Clouds = response.body()!!.clouds!!
                                weatherDetail.save()

                                coordDetail.id = 0
                                coordDetail.save()

                                sysWeatherDetail.sysId = 0
                                sysWeatherDetail.save()

                                mainWeatherDetail.id = 0
                                mainWeatherDetail.save()

                                windWeatherDetail.id = 0
                                windWeatherDetail.save()

                                cloudsWeatherDetail.id = 0
                                cloudsWeatherDetail.save()

                                for (inWeatherDt in innerWeatherDetail!!) {
                                    inWeatherDt.wId = 0
                                    inWeatherDt.save()
                                }

                                WeatherStreamCallbackManager.updateHomeScreenData(1)

                                /*** Weather notification */
                                if (preferenceUtil.getBooleanPref(preferenceUtil.IS_NOTIFICATION_ON)) {
                                    val localNotification = LocalNotification(this@MainActivity)
                                    val loc = Locale("", sysWeatherDetail.country)
                                    val title = weatherDetail.name.plus(", ").plus(loc.displayCountry)
                                    val message: String? = if (preferenceUtil.getBooleanPref(preferenceUtil.IS_TEMPERATURE_UNIT_CELCIUS)) {
                                        Math.round(mainWeatherDetail.temp!!.toDouble()).toString().plus(resources.getString(R.string.c_symbol)).plus(" " + innerWeatherDetail[0].description)
                                    } else {
                                        Math.round(Methods.convertCelsiusToFahrenheit(mainWeatherDetail.temp?.toFloat()!!)).toString().plus(resources.getString(R.string.f_symbol)).plus(" " + innerWeatherDetail[0].description)
                                    }
                                    val time = DateUtil.getDateFromMillis(System.currentTimeMillis(), DateUtil.timeHourFormat, false)
                                    val weatherType = innerWeatherDetail[0].id
                                    if (message != null) {
                                        localNotification.showCustomLayoutHeadsUpNotification(this@MainActivity, title, message, time,
                                                WeatherToImage.getWeatherTypeConditionCode(null, null, weatherType.toString()))
                                    }
                                }

                                /** called here location weather forecasting api */
                                callWeatherForecasting(lat, lon)

                                if (weatherDetail != null) {
                                    val searchedLocation: List<LocationSearchHistory> = LocationSearchHistory.getSearchedLocationList()
                                    if (searchedLocation.isNotEmpty()) {
                                        searchedLocation.filter { it.id == response.body()?.id }.forEach { return }
                                        setSideMenuSearchEntry(weatherDetail)
                                    } else {
                                        setSideMenuSearchEntry(weatherDetail)
                                    }
                                }
                                if (slidingMenuLeft?.isMenuShowing!!) {
                                    slidingMenuLeft?.toggle()
                                }
                            }
                        } else {
                            Methods.showSnackBar(coordinatorLayoutMain, response.message(), ContextCompat.getColor(this@MainActivity, R.color.brink_pink), this@MainActivity)
                        }
                    } else {
                        Methods.showSnackBar(coordinatorLayoutMain, response.message(), ContextCompat.getColor(this@MainActivity, R.color.brink_pink), this@MainActivity)
                    }
                } else {
                    Methods.showSnackBar(coordinatorLayoutMain, errorHandler.parseError(response).message(), ContextCompat.getColor(this@MainActivity, R.color.brink_pink), this@MainActivity)
                }
            }

            override fun onFailure(call: Call<WeatherData>?, t: Throwable?) {
                if (call!!.isCanceled)
                    return
                errorHandler.setExceptionMessage(t)
            }
        })
    } else {
        Methods.showSnackBar(coordinatorLayoutMain, resources.getString(R.string.no_internet_connection), ContextCompat.getColor(this@MainActivity, R.color.brink_pink), this@MainActivity)
    }

    /***
     * API Method for get the weather information of specific location
     * */
    var weatherForecastingCall: Call<WeatherForecastData>? = null

    fun callWeatherForecasting(lat: Double, lon: Double) = if (isNetworkConnected(this@MainActivity)) {

        val hashMap = APIParameters.getParam()
        hashMap.put(APIParameters.ForecastingWeather.lat, "" + lat)
        hashMap.put(APIParameters.ForecastingWeather.lon, "" + lon)
        hashMap.put(APIParameters.ForecastingWeather.units, "" + KeyUtil.UNITS_METRIC)
        hashMap.put(APIParameters.ForecastingWeather.type, "" + KeyUtil.TYPES_ACCURATE)

        weatherForecastingCall = NetworkConfig.getWebApis().getWeatherForecasting(APIParameters.KEY_OPEN_WEATHER_MAP_KEY, hashMap)
        weatherForecastingCall!!.enqueue(object : Callback<WeatherForecastData> {

            override fun onResponse(call: Call<WeatherForecastData>, response: Response<WeatherForecastData>) {
                if (call.isCanceled)
                    return

                if (response != null) {
                    if (response.isSuccessful) {
                        if (response.body() != null) {
                            if (response.body()?.cod == "200") {
                                Methods.hideKeyboard(this@MainActivity)

                                val weatherForecastData: WeatherForecastData = response.body()!!
                                weatherForecastData.id = 0
                                weatherForecastData.save()

                                val forecastingList: Array<WeatherForecastData.ForecastList> = response.body()?.list!!
                                if (forecastingList != null && forecastingList.isNotEmpty()) {

                                    for (hourlyWeatherData in forecastingList) {
                                        hourlyWeatherData.id = 0
                                        hourlyWeatherData.save()

                                        val sysWeatherDetail: WeatherData.Sys = hourlyWeatherData.sys!!
                                        if (sysWeatherDetail != null) {
                                            sysWeatherDetail.sysId = 0
                                            sysWeatherDetail.save()
                                        }

                                        val mainWeatherDetail: WeatherData.Main = hourlyWeatherData.main!!
                                        mainWeatherDetail.id = 0
                                        mainWeatherDetail.save()

                                        val windWeatherDetail: WeatherData.Wind = hourlyWeatherData.wind!!
                                        windWeatherDetail.id = 0
                                        windWeatherDetail.save()

                                        val cloudsWeatherDetail: WeatherData.Clouds = hourlyWeatherData.clouds!!
                                        cloudsWeatherDetail.id = 0
                                        cloudsWeatherDetail.save()

                                        if (hourlyWeatherData.rain != null) {
                                            val rainData: WeatherForecastData.Rain = hourlyWeatherData.rain!!
                                            rainData.id = 0
                                            rainData.save()
                                        } else {
                                            Delete.table(WeatherForecastData.Rain::class.java)
                                        }

                                        val innerWeatherDetail: Array<WeatherData.Weather>? = hourlyWeatherData.weather
                                        if (innerWeatherDetail != null) {
                                            for (innerWDetail in innerWeatherDetail) {
                                                innerWDetail.wId = 0
                                                innerWDetail.save()
                                            }
                                        }
                                    }
                                    WeatherStreamCallbackManager.updateHomeScreenData(2)
                                }
//                                Methods.showSnackBar(coordinatorLayoutMain, response.code().toString(), ContextCompat.getColor(this@MainActivity, R.color.fruit_salad), this@MainActivity)
                            }
                        } else {
                            Methods.showSnackBar(coordinatorLayoutMain, response.message(), ContextCompat.getColor(this@MainActivity, R.color.brink_pink), this@MainActivity)
                        }
                    } else {
                        Methods.showSnackBar(coordinatorLayoutMain, response.message(), ContextCompat.getColor(this@MainActivity, R.color.brink_pink), this@MainActivity)
                    }
                } else {
                    Methods.showSnackBar(coordinatorLayoutMain, errorHandler.parseError(response).message(), ContextCompat.getColor(this@MainActivity, R.color.brink_pink), this@MainActivity)
                }
            }

            override fun onFailure(call: Call<WeatherForecastData>?, t: Throwable?) {
                if (call!!.isCanceled)
                    return
                errorHandler.setExceptionMessage(t)
            }
        })
    } else {
        Methods.showSnackBar(coordinatorLayoutMain, resources.getString(R.string.no_internet_connection), ContextCompat.getColor(this@MainActivity, R.color.brink_pink), this@MainActivity)
    }

    fun setSideMenuSearchEntry(weatherDetail: WeatherData) {
        val locationSearch = LocationSearchHistory()

        if (weatherDetail != null) {
            tvRecentSearch.visibility = View.VISIBLE
            tvClearSearch.visibility = View.VISIBLE
        } else {
            tvRecentSearch.visibility = View.GONE
            tvClearSearch.visibility = View.GONE
        }

        locationSearch.id = weatherDetail.id
        locationSearch.cityName = weatherDetail.name
        locationSearch.countyName = weatherDetail.sys?.country
        locationSearch.temperature = weatherDetail.main?.temp?.toDouble()
        locationSearch.weatherType = weatherDetail.weather?.get(0)?.id
        locationSearch.lat = weatherDetail.coord?.lat
        locationSearch.lon = weatherDetail.coord?.lon
        locationSearch.save()

        lists.add(LocationSearchHistory(locationSearch.id, locationSearch.cityName, locationSearch.countyName, locationSearch.weatherType, locationSearch.temperature))
        rvMenuLocationList.adapter = LocationHistoryAdapter(preferenceUtil, lists, this@MainActivity)
    }
}
