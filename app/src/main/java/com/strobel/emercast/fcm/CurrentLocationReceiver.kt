package com.strobel.emercast.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationResult
import com.google.firebase.messaging.FirebaseMessaging
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.repositories.CurrentLocationRepository
import com.strobel.emercast.lib.LocationUtils

class CurrentLocationReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if(!LocationResult.hasResult(intent)) return
        val locationResult = LocationResult.extractResult(intent)

        val dbHelper = EmercastDbHelper(context)
        val repo = CurrentLocationRepository(dbHelper)

        if(locationResult?.lastLocation == null) {
            Log.d(this.javaClass.name, "Current Location is empty")
            return
        }

        handleNewLocation(locationResult.lastLocation!!, context)
    }

    companion object {
        fun handleNewLocation(newLocation: Location, context: Context) {
            val dbHelper = EmercastDbHelper(context)
            val repo = CurrentLocationRepository(dbHelper)

            val previousLocation = repo.getCurrent()
            repo.update(newLocation.latitude.toFloat(), newLocation.longitude.toFloat())

            val previousLocationRounded = if (previousLocation == null) Pair(0.0,0.0) else  Pair(LocationUtils.roundToNearestPointFive(previousLocation.first.toDouble()), LocationUtils.roundToNearestPointFive(
                previousLocation.second.toDouble()))
            val newLocationRounded = Pair(LocationUtils.roundToNearestPointFive(newLocation.latitude), LocationUtils.roundToNearestPointFive(newLocation.longitude))

            if(previousLocationRounded.first == newLocationRounded.first && previousLocationRounded.second == newLocationRounded.second) return

            Log.d(this.javaClass.name, "Received new location, location topic has changed")

            if(previousLocation != null) {
                LocationUtils.getTopicsForLatLong(previousLocation.first, previousLocation.second).forEach {
                    FirebaseMessaging.getInstance().unsubscribeFromTopic(it)
                }
            }

            LocationUtils.getTopicsForLatLong(newLocationRounded.first.toFloat(), newLocationRounded.second.toFloat()).forEach {
                FirebaseMessaging.getInstance().subscribeToTopic(it)
                Log.d(this.javaClass.name, "Subscribed to topic: $it")
            }
        }
    }
}