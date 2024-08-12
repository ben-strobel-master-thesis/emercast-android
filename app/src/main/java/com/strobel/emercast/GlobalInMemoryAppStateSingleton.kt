package com.strobel.emercast

import com.strobel.emercast.ble.enums.GattRoleEnum

// This singleton is only persisted across one lifecyle of the application
// This means that all values are reset when the application is terminated
class GlobalInMemoryAppStateSingleton private constructor(){

    var gattRole: GattRoleEnum = GattRoleEnum.UNDETERMINED

    companion object {
        private val instance = GlobalInMemoryAppStateSingleton()

        fun getInstance(): GlobalInMemoryAppStateSingleton {
            return instance
        }
    }
}