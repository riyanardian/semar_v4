package com.example.semar_v4

data class DeviceModel(
    val name: String,
    val type: String,
    val chipId: String,   //
    var status: Boolean = false,   // status ON/OFF dari ESP32
    var runhourRelay2: String = "0 jam" // default

)

