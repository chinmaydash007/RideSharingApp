package com.mindorks.ridesharing.ui.maps


import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.gson.JsonObject
import com.mindorks.ridesharing.data.network.NetworkService
import com.mindorks.ridesharing.simulator.WebSocket
import com.mindorks.ridesharing.simulator.WebSocketListener
import com.mindorks.ridesharing.utils.Constants
import org.json.JSONObject
import kotlin.math.log

class MapsPresenter(private val networkService: NetworkService) : WebSocketListener {
    companion object {
        private const val TAG = "MapsPresenter"
    }

    private var view: MapsView? = null
    private lateinit var webSocket: WebSocket

    fun onAttach(view: MapsView) {
        this.view = view
        webSocket = networkService.createWenSocket(this)
        webSocket.connect()
    }

    fun onDetach() {
        webSocket.disconnect()
        view = null
    }

    override fun onConnect() {
        Log.d(TAG, "onConnect")
    }

    override fun onMessage(data: String) {
        val jsonObject = JSONObject(data)
        when (jsonObject.getString(Constants.TYPE)) {
            Constants.NEAR_BY_CABS -> {
                Log.d(TAG, "NEAR BY CARS")

                handleOnMessageNearByCabs(jsonObject)
            }
            Constants.CAB_BOOKED -> {
                view?.informCabBook()
                Log.d(TAG, "CAR BOOKED")
            }
            Constants.PICKUP_PATH, Constants.TRIP_PATH -> {
                Log.d(TAG, "PICKUP PATH")

                val jsonArray = jsonObject.getJSONArray("path")
                val pickUpPath = arrayListOf<LatLng>()
                for (i in 0 until jsonArray.length()) {
                    val lat = (jsonArray.get(i) as JSONObject).getDouble(Constants.LAT)
                    val lng = (jsonArray.get(i) as JSONObject).getDouble(Constants.LNG)
                    val latLng = LatLng(lat, lng)
                    pickUpPath.add(latLng)
                    Log.d("mytag", "pickup ${latLng.latitude} ${latLng.longitude}")

                }
                view?.showPath(pickUpPath)
            }
            Constants.LOCATION -> {
                val latCurrent = jsonObject.getDouble("lat")
                val lngCurrent = jsonObject.getDouble("lng")
                Log.d("mytag", "cab location  ${latCurrent} ${lngCurrent}")
                view?.updateCabLocation(LatLng(latCurrent, lngCurrent))
            }
            Constants.CAB_IS_ARRIVING -> {
                view?.informCabIsArriving()
            }
            Constants.CAB_ARRIVED -> {
                view?.informCabArrived()
            }
            Constants.TRIP_START -> {
                view?.informTripStart()
            }
            Constants.TRIP_END -> {
                view?.informTripEnd()
            }
        }

    }

    private fun handleOnMessageNearByCabs(jsonObject: JSONObject) {
        val nearByCabsLcocation = arrayListOf<LatLng>()
        val jsonArray = jsonObject.getJSONArray(Constants.LOCATIONS)
        for (i in 0 until jsonArray.length()) {
            val lat = (jsonArray.get(i) as JSONObject).getDouble(Constants.LAT)
            val lng = (jsonArray.get(i) as JSONObject).getDouble(Constants.LNG)
            val latLng = LatLng(lat, lng)
            nearByCabsLcocation.add(latLng)

        }
        view?.showNearByCabs(nearByCabsLcocation)
    }

    override fun onDisconnect() {
        Log.d(TAG, "onDisconnect")
    }

    override fun onError(error: String) {
        Log.d(TAG, "error :${error}")
    }

    fun requestNearByCabs(latLng: LatLng) {
        val jsonObject = JSONObject()
        jsonObject.put(Constants.TYPE, Constants.NEAR_BY_CABS)
        jsonObject.put(Constants.LAT, latLng.latitude)
        jsonObject.put(Constants.LNG, latLng.longitude)
        webSocket.sendMessage(jsonObject.toString())
    }

    fun requestCab(pickUpLatLng: LatLng, dropLatLng: LatLng) {
        val jsonObject = JSONObject()
        jsonObject.put(Constants.TYPE, Constants.REQUEST_CABS)
        jsonObject.put("pickUpLat", pickUpLatLng.latitude)
        jsonObject.put("pickUpLng", pickUpLatLng.longitude)
        jsonObject.put("dropLat", dropLatLng.latitude)
        jsonObject.put("dropLng", dropLatLng.longitude)
        webSocket.sendMessage(jsonObject.toString())
    }

}