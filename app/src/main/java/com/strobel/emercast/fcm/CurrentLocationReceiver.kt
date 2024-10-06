package com.strobel.emercast.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

        val previousLocation = repo.getCurrent()
        val newLocation = locationResult.lastLocation!!
        repo.update(newLocation.latitude.toFloat(), newLocation.longitude.toFloat())

        val previousLocationRounded = if (previousLocation == null) Pair(0.0,0.0) else  Pair(LocationUtils.roundToNearestPointFive(previousLocation.first.toDouble()), LocationUtils.roundToNearestPointFive(
            previousLocation.second.toDouble()))
        val newLocationRounded = Pair(LocationUtils.roundToNearestPointFive(newLocation.latitude), LocationUtils.roundToNearestPointFive(newLocation.longitude))

        if(previousLocationRounded.first == newLocationRounded.first && previousLocationRounded.second == newLocationRounded.second) return

        FirebaseMessaging.getInstance().unsubscribeFromTopic()
    }
}