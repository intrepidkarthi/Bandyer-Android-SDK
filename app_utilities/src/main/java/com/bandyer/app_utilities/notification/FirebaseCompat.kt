/*
 * Copyright (C) 2019 Bandyer S.r.l. All Rights Reserved.
 * See LICENSE.txt for licensing information
 */
package com.bandyer.app_utilities.notification

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.bandyer.app_utilities.storage.ConfigurationPrefsManager
import com.bandyer.app_utilities.networking.ConnectionStatusChangeReceiver.Companion.isConnected
import com.bandyer.app_utilities.networking.ConnectionStatusChangeReceiver.Companion.register
import com.bandyer.app_utilities.networking.ConnectionStatusChangeReceiver.Companion.unRegister
import com.bandyer.app_utilities.networking.MockedNetwork.registerDeviceForPushNotification
import com.bandyer.app_utilities.networking.MockedNetwork.unregisterDeviceForPushNotification
import com.bandyer.app_utilities.networking.OnNetworkConnectionChanged
import com.bandyer.app_utilities.storage.LoginManager.getLoggedUser
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import com.google.firebase.messaging.FirebaseMessaging
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * This class is for Bandyer usage only
 * You should implement your own logic for notification handling
 *
 * @author kristiyan
 */
object FirebaseCompat {
    private var deviceRegistered = false
    private val onNetworkConnectionChanged: OnNetworkConnectionChanged = object : OnNetworkConnectionChanged {
        override fun onNetworkConnectionChanged(context: Context, connected: Boolean) {
            if (deviceRegistered || !connected) return
            registerDevice(context)
        }
    }

    fun unregisterDevice(context: Context?, loggedUser: String?) {
        if (!deviceRegistered) return
        unRegister(context!!)
        deviceRegistered = false
        try {
            FirebaseInstanceId.getInstance().instanceId.addOnSuccessListener { instanceIdResult: InstanceIdResult ->
                val post = Thread(Runnable {
                    val devicePushToken = instanceIdResult.token
                    unregisterDeviceForPushNotification(context, loggedUser, devicePushToken)
                    try {
                        FirebaseInstanceId.getInstance().deleteInstanceId()
                        FirebaseApp.getInstance().delete()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                })
                post.start()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun registerDevice(context: Context) {
        if (deviceRegistered) return
        register(context, onNetworkConnectionChanged)
        if (!isConnected(context)) return
        refreshConfiguration(context, Runnable {
            try {
                FirebaseInstanceId.getInstance().instanceId.addOnSuccessListener { instanceIdResult: InstanceIdResult ->
                    val devicePushToken = instanceIdResult.token
                    registerDeviceForPushNotification(context, getLoggedUser(context), devicePushToken, object : Callback<Void?> {
                        override fun onResponse(call: Call<Void?>, response: Response<Void?>) {
                            if (!response.isSuccessful) {
                                Log.e("PushNotification", "Failed to register device for push notifications!")
                                return@onResponse
                            }
                            deviceRegistered = true
                        }

                        override fun onFailure(call: Call<Void?>, t: Throwable) {
                            Log.e("PushNotification", "Failed to register device for push notifications!")
                        }
                    })
                }.addOnFailureListener { error: Exception? -> Toast.makeText(context, "Wrong configuration for FCM.\nYou will not receive any notifications!", Toast.LENGTH_LONG).show() }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        })
    }

    /**
     * This is necessary to change the projectId at runtime
     * In normal use-cases you shall not need to refresh the configuration as it will use the google-services.json file.
     */
    fun refreshConfiguration(context: Context, onComplete: Runnable, notifications: Boolean = true) {
        val post = Thread(Runnable {
            kotlin.runCatching {
                val configuration = ConfigurationPrefsManager.getConfiguration(context)
                val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                        .setGcmSenderId(configuration.projectNumber)
                        .setProjectId(configuration.firebaseProjectId)
                        .setApplicationId(configuration.firebaseMobileAppId!!)
                        .setApiKey(configuration.firebaseApiKey!!)
                        .build())
                FirebaseInstanceId.getInstance(app).instanceId
                if(notifications) FirebaseMessaging.getInstance().isAutoInitEnabled = true
                onComplete.run()
            }
        })
        post.start()
    }
}