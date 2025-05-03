package com.samsunggalaxy

import android.app.Application
import com.google.android.material.color.DynamicColors

//TODO firebase
//TODO keep value cuoi cung de hien thi len wheel view

//done mckimquyen
//applovin ad
//splash screen
//review in app bingo
//keystore
//leak canary
//policy
//share app
//rate app
//more app
//ic launcher
//change color
//ad id manifest
//scale 1.0
//120hz

class GalaxyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
//        this.setupApplovinAd()
        //TODO roy93~ admob init
    }
}
