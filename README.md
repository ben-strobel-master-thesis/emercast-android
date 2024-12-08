## Emercast Android Application

The is the application installed on the devices of end users. 
It implements the main purpose of the Emercast system by spreading emergency notifications from device to device via peer to peer connections. 
It uses BLE advertisements to advertise itself and discover other instances of the application.
It uses the data included in the advertisement to decide whether another instance has need messages and should be connected to.
It then uses a BLE GATT server/client infrastructure to exchange messages with the other instance.

## Setup

- Install the latest version of [Android Studio](https://developer.android.com/studio)
- Open this repository in android studio and wait for the gradle project to sync automatically

## Build

- To build an installable apk run ```gradle :app:assembleDebug```
