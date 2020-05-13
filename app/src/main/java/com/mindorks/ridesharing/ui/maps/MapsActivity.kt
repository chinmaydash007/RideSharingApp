package com.mindorks.ridesharing.ui.maps

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.util.MapUtils
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.mindorks.ridesharing.R
import com.mindorks.ridesharing.data.network.NetworkService
import com.mindorks.ridesharing.utils.AnimationUtils
import com.mindorks.ridesharing.utils.MapsUtls
import com.mindorks.ridesharing.utils.PermissionsUtils
import com.mindorks.ridesharing.utils.ViewUtils
import kotlinx.android.synthetic.main.activity_maps.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, MapsView {
    companion object {
        private const val TAG = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 101
        private const val PICKUP_REQUEST_CODE = 102
        private const val DROP_REQUEST_CODE = 103
    }

    private lateinit var mMap: GoogleMap
    private lateinit var presenter: MapsPresenter
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLatLng: LatLng? = null
    private var pickUpLatLng: LatLng? = null
    private var dropLatLng: LatLng? = null
    private var greyPolyLine: Polyline? = null
    private var blackPolyLine: Polyline? = null
    private var nearByCabsMarkerList = arrayListOf<Marker>()
    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var movingCabmarker: Marker? = null
    private var previousLatLngFromServer: LatLng? = null
    private var currentLatLngFromServer: LatLng? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        ViewUtils.enableTransparentStatusBar(window)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        presenter = MapsPresenter(NetworkService())
        presenter.onAttach(this)
        setupLocationListerner()
        setUpClickListerner()

    }

    private fun setUpClickListerner() {
        pickUpTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(PICKUP_REQUEST_CODE)
        }
        dropTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(DROP_REQUEST_CODE)
        }
        requestCabButton.setOnClickListener {
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = getString(R.string.requesting_your_cab)
            requestCabButton.isEnabled = false
            pickUpTextView.isEnabled = false
            dropTextView.isEnabled = false
            presenter.requestCab(pickUpLatLng!!, dropLatLng!!)
        }
        nextRideButton.setOnClickListener {
            reset()
        }
    }

    private fun reset() {
        statusTextView.visibility = View.GONE
        nextRideButton.visibility = View.GONE
        nearByCabsMarkerList.forEach { it.remove() }
        nearByCabsMarkerList.clear()
        previousLatLngFromServer = null
        currentLatLngFromServer = null
        if (currentLatLng != null) {
            moveCamera(currentLatLng)
            animateCamera(currentLatLng)
            setCurrentLocationAtPickUp()
            presenter.requestNearByCabs(currentLatLng!!)
        } else {
            pickUpTextView.text = ""
        }
        pickUpTextView.isEnabled = true
        dropTextView.isEnabled = true
        dropTextView.text = ""
        movingCabmarker?.remove()
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
        dropLatLng = null
        greyPolyLine = null
        blackPolyLine = null
        originMarker = null
        destinationMarker = null
        movingCabmarker = null
    }

    private fun launchLocationAutoCompleteActivity(requestCode: Int) {
        val fields: List<Place.Field> =
            listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent =
            Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this)
        startActivityForResult(intent, requestCode)
    }

    private fun moveCamera(latLng: LatLng?) {
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun enableMyLocationMap() {
        mMap.isMyLocationEnabled = true
        mMap.setPadding(0, ViewUtils.dpToPx(48f), 0, 0)
    }

    private fun animateCamera(latLng: LatLng?) {
        val cameraPosition = CameraPosition.Builder().target(latLng).zoom(15.5f).build()
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun addCarMarkerAndGet(latLng: LatLng): Marker {
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapsUtls.getCarBitMap(this))
        return mMap.addMarker(MarkerOptions().position(latLng).flat(false).icon(bitmapDescriptor))
    }

    private fun addOriginDetinationMarkerAndGet(latLng: LatLng): Marker {
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapsUtls.getDesinationBitmap())
        return mMap.addMarker(MarkerOptions().position(latLng).flat(false).icon(bitmapDescriptor))

    }

    private fun setCurrentLocationAtPickUp() {
        pickUpLatLng = currentLatLng
        pickUpTextView.text = getString(R.string.current_location)
    }

    private fun checkAndShowRequestButton() {
        if (pickUpLatLng != null && dropLatLng != null) {
            requestCabButton.visibility = View.VISIBLE
            requestCabButton.isEnabled = true
        }
    }


    fun setupLocationListerner() {
        fusedLocationProviderClient = FusedLocationProviderClient(this)
        //for getting the current Location update after every 2 seconds
        val locationRequest = LocationRequest().setInterval(2000).setFastestInterval(2000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)
                locationResult?.let {
                    if (currentLatLng == null) {
                        for (location in locationResult.locations) {
                            if (currentLatLng == null) {
                                Log.d(TAG, "${location.latitude} ${location.longitude}")
                                currentLatLng = LatLng(location.latitude, location.longitude)
                                setCurrentLocationAtPickUp()
                                enableMyLocationMap()
                                //moveCamera(currentLatLng)
                                animateCamera(currentLatLng)
                                presenter.requestNearByCabs(currentLatLng!!)

                            }
                        }
                    }
                    //update the location of user on server

                }
            }

            override fun onLocationAvailability(p0: LocationAvailability?) {
                super.onLocationAvailability(p0)
            }
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
    }

    override fun onStart() {
        super.onStart()
        when {
            PermissionsUtils.isAccessFineLocationGranted(this) -> {
                when {
                    PermissionsUtils.isLocationEnabled(this) -> {
                        setupLocationListerner()
                    }
                    else -> {
                        PermissionsUtils.showGpsNotEnabledDialog(
                            this
                        )
                    }
                }

            }
            else -> {
                PermissionsUtils.requestAccessFineLocation(this, LOCATION_PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onDetach()
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    when {
                        PermissionsUtils.isLocationEnabled(this) -> {
                            //Fetch Location
                            setupLocationListerner()

                        }
                        else -> {
                            PermissionsUtils.showGpsNotEnabledDialog(
                                this
                            )
                        }
                    }
                } else {
                    Toast.makeText(this, "Location Permission Not Granted", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    override fun showNearByCabs(latLngList: List<LatLng>) {
        nearByCabsMarkerList.clear()
        latLngList.forEach {
            Log.d(TAG, "near By Cabs: ${it.latitude} ${it.longitude}")
            addCarMarkerAndGet(it)
        }
    }

    override fun informCabBook() {
        nearByCabsMarkerList.forEach { it.remove() }
        nearByCabsMarkerList.clear()
        requestCabButton.visibility = View.GONE
        statusTextView.text = getString(R.string.your_cab_is_booked)
    }

    override fun showPath(latLngList: List<LatLng>) {
        val builder = LatLngBounds.Builder()
        for (latLng in latLngList) {
            builder.include(latLng)
        }
        val bounds = builder.build()
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 2))
        val polyLineOptions = PolylineOptions()
        polyLineOptions.color(Color.GRAY)
        polyLineOptions.width(5f)
        polyLineOptions.addAll(latLngList)
        greyPolyLine = mMap.addPolyline(polyLineOptions)

        val blackpolyLineOptions = PolylineOptions()
        blackpolyLineOptions.color(Color.GRAY)
        blackpolyLineOptions.width(5f)
        blackPolyLine = mMap.addPolyline(blackpolyLineOptions)

        originMarker = addOriginDetinationMarkerAndGet(latLngList[0])
        originMarker?.setAnchor(0.5f, 0.5f)
        destinationMarker = addOriginDetinationMarkerAndGet(latLngList[latLngList.size - 1])

        val polyLineAnimator = AnimationUtils.polyLineAnimator()
        polyLineAnimator.addUpdateListener { animation: ValueAnimator ->
            val percentValue = (animation.animatedValue as Int)
            val index = (greyPolyLine?.points!!.size) * (percentValue / 100.0f).toInt()
            blackPolyLine?.points = greyPolyLine?.points!!.subList(0, index)
        }
        polyLineAnimator.start()
    }

    override fun updateCabLocation(latLng: LatLng) {
        Log.d("hello", "${latLng.latitude} ${latLng.longitude}")
        if (movingCabmarker == null) {
            movingCabmarker = addCarMarkerAndGet(latLng)
        }
        if (previousLatLngFromServer == null) {
            currentLatLngFromServer = latLng
            previousLatLngFromServer = currentLatLngFromServer
            movingCabmarker?.position = currentLatLngFromServer
            movingCabmarker?.setAnchor(0.5f, 0.5f)
            animateCamera(currentLatLngFromServer)
        } else {
            previousLatLngFromServer = currentLatLngFromServer
            currentLatLngFromServer = latLng
            val valueAnimator = AnimationUtils.cabAnimator()
            valueAnimator.addUpdateListener { va ->
                if (currentLatLngFromServer != null && previousLatLngFromServer != null) {
                    val multiplier = va.animatedFraction
                    val nextLocation =
                        LatLng(
                            multiplier * currentLatLngFromServer!!.latitude + (1 - multiplier) * previousLatLngFromServer!!.latitude,
                            multiplier * currentLatLngFromServer!!.longitude + (1 - multiplier) * previousLatLngFromServer!!.longitude
                        )
                    movingCabmarker?.position = nextLocation
                    val rotation = MapsUtls.getRotation(previousLatLngFromServer!!, nextLocation)
                    if (!rotation.isNaN()) {
                        movingCabmarker?.rotation = rotation
                    }
                    movingCabmarker?.setAnchor(.5f, .5f)
                    animateCamera(nextLocation)
                }

            }
            valueAnimator.start()
        }

    }

    override fun informCabIsArriving() {
        statusTextView.text = "Your cab is arriving"
    }

    override fun informCabArrived() {
        statusTextView.text = "Your cab has arrived"
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun informTripStart() {
        statusTextView.text = "You are on a trip"
        previousLatLngFromServer = null
    }

    override fun informTripEnd() {
        statusTextView.text = "Trip End"
        nextRideButton.visibility = View.VISIBLE
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun showRoutesNotAvailableError() {
    }

    override fun showDirectionApiFailedError(error: String) {
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICKUP_REQUEST_CODE || requestCode == DROP_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    when (requestCode) {
                        PICKUP_REQUEST_CODE -> {
                            pickUpTextView.text = place.name
                            pickUpLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                        DROP_REQUEST_CODE -> {
                            dropTextView.text = place.name
                            dropLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    val status: Status = Autocomplete.getStatusFromIntent(data!!)
                    Log.d(TAG, status.statusMessage)
                }
                AutocompleteActivity.RESULT_CANCELED -> {


                }
            }
        }
    }
}
