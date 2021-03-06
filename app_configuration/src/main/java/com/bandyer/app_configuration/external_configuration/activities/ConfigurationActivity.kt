/*
 *  Copyright (C) 2020 Bandyer S.r.l. All Rights Reserved.
 *  See LICENSE.txt for licensing information
 */
package com.bandyer.app_configuration.external_configuration.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.bandyer.app_configuration.R
import com.bandyer.app_configuration.external_configuration.model.Configuration
import com.bandyer.app_configuration.external_configuration.model.ConfigurationFieldChangeListener
import com.bandyer.app_configuration.external_configuration.model.CustomUserDetailsProvider
import com.bandyer.app_configuration.external_configuration.model.PushProvider
import com.bandyer.app_configuration.external_configuration.model.bindToConfigurationProperty
import com.bandyer.app_configuration.external_configuration.utils.MediaStorageUtils
import kotlinx.android.synthetic.main.activity_configuration.api_key
import kotlinx.android.synthetic.main.activity_configuration.app_id
import kotlinx.android.synthetic.main.activity_configuration.environment
import kotlinx.android.synthetic.main.activity_configuration.fcm_configuration_fields
import kotlinx.android.synthetic.main.activity_configuration.firebase_api_key
import kotlinx.android.synthetic.main.activity_configuration.firebase_mobile_app_id
import kotlinx.android.synthetic.main.activity_configuration.firebase_project_id
import kotlinx.android.synthetic.main.activity_configuration.firebase_project_number
import kotlinx.android.synthetic.main.activity_configuration.leak_canary
import kotlinx.android.synthetic.main.activity_configuration.mock_user_details
import kotlinx.android.synthetic.main.activity_configuration.push_provider
import kotlinx.android.synthetic.main.activity_configuration.watermark

open class ConfigurationActivity : BaseConfigurationActivity() {

    companion object {

        const val CURRENT_CONFIGURATION = "CURRENT_CONFIGURATION"
        const val CONFIGURATION_RESULT = "CONFIGURATION_RESULT"
        const val BRAND_IMAGE_TEXT_REQUEST = 456
        const val MOCK_USER_DETAILS_REQUEST = 457

        @JvmOverloads
        fun show(context: Context, currentConfiguration: Configuration?, showAsQRReader: Boolean = false, qrConfigurationActivity: Class<*>? = null, withOptions: ((intent: Intent) -> Unit)? = null) {
            if (showAsQRReader || qrConfigurationActivity != null) show(context, currentConfiguration, qrConfigurationActivity
                    ?: QRConfigurationActivity::class.java, withOptions)
            else show(context, currentConfiguration, ConfigurationActivity::class.java, withOptions)
        }

        @JvmOverloads
        fun showNew(context: Context, currentConfiguration: Configuration?, showAsQRReader: Boolean = false, qrConfigurationActivity: Class<*>? = null) {
            val withOptions: (intent: Intent) -> Unit = { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            show(context, currentConfiguration, showAsQRReader || qrConfigurationActivity != null, qrConfigurationActivity, withOptions)
        }

        private fun show(context: Context, currentConfiguration: Configuration?, configurationActivity: Class<*>, withOptions: ((intent: Intent) -> Unit)? = null) {
            val intent = Intent(context, configurationActivity)
            intent.putExtra(CURRENT_CONFIGURATION, currentConfiguration)
            withOptions?.invoke(intent)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuration)
        setDefaultValues(getInitialConfiguration())
        addPreferencesListeners()
    }

    override fun onResume() {
        super.onResume()
        val uri = intent.data ?: return
        currentConfiguration = configureFromUri(uri)
        currentConfiguration ?: return
        setDefaultValues(currentConfiguration!!)
    }

    private fun setDefaultValues(configuration: Configuration) {
        this.currentConfiguration = configuration
        environment!!.setValue(configuration.environment)
        app_id!!.setValue(configuration.appId)
        api_key!!.setValue(configuration.apiKey)
        push_provider!!.setValue(configuration.pushProvider.name)
        onPushProviderChanged(configuration.pushProvider)
        firebase_project_number!!.setValue(configuration.projectNumber)
        firebase_project_id!!.setValue(configuration.firebaseProjectId)
        firebase_api_key!!.setValue(configuration.firebaseApiKey)
        firebase_mobile_app_id!!.setValue(configuration.firebaseMobileAppId)
        watermark!!.setImageName(configuration.logoName)
        configuration.logoUrl?.let { textUri ->
            watermark!!.setImageUri(MediaStorageUtils.getUriFromString(textUri))
        }
        mock_user_details!!.setSubtitle(configuration.customUserDetailsProvider.name)
        leak_canary.setValue(configuration.useLeakCanary)
    }

    private fun addPreferencesListeners() {
        environment.bindToConfigurationProperty(currentConfiguration!!, currentConfiguration!!::environment)
        app_id!!.bindToConfigurationProperty(currentConfiguration!!, currentConfiguration!!::appId)
        api_key!!.bindToConfigurationProperty(currentConfiguration!!, currentConfiguration!!::apiKey)
        push_provider.bindToConfigurationProperty(currentConfiguration!!, currentConfiguration!!::pushProvider, object :
                ConfigurationFieldChangeListener<String> {
            override fun onConfigurationFieldChanged(value: String) =
                    onPushProviderChanged(PushProvider.valueOf(value))
        })
        firebase_project_number!!.bindToConfigurationProperty(currentConfiguration!!, currentConfiguration!!::projectNumber)
        firebase_project_id!!.bindToConfigurationProperty(currentConfiguration!!, currentConfiguration!!::firebaseProjectId)
        firebase_api_key!!.bindToConfigurationProperty(currentConfiguration!!, currentConfiguration!!::firebaseApiKey)
        firebase_mobile_app_id!!.bindToConfigurationProperty(currentConfiguration!!, currentConfiguration!!::firebaseMobileAppId)
        leak_canary!!.bindToConfigurationProperty(currentConfiguration!!, currentConfiguration!!::useLeakCanary)

        watermark!!.setOnClickListener {
            ImageTextEditActivity.showForResult(
                    this,
                    Uri.parse(currentConfiguration!!.logoUrl),
                    currentConfiguration!!.logoName ?: "",
                    BRAND_IMAGE_TEXT_REQUEST)
        }
        mock_user_details!!.setOnClickListener {
            MockUserDetailsSettingsActivity.showForResult(
                    this,
                    if (currentConfiguration!!.customUserDetailsImageUrl != null)
                        Uri.parse(currentConfiguration!!.customUserDetailsImageUrl)
                    else null,
                    currentConfiguration!!.customUserDetailsName ?: "",
                    currentConfiguration!!.customUserDetailsProvider,
                    MOCK_USER_DETAILS_REQUEST)
        }
    }

    private fun onPushProviderChanged(pushProvider: PushProvider) {
        fcm_configuration_fields.visibility = when (pushProvider) {
            PushProvider.NONE, PushProvider.Pushy -> View.GONE
            PushProvider.FCM -> View.VISIBLE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BRAND_IMAGE_TEXT_REQUEST && resultCode == 2 && data!!.extras != null) {
            val url = data.getStringExtra(ImageTextEditActivity.PRESET_URI_PARAM)
            val text = data.getStringExtra(ImageTextEditActivity.PRESET_TEXT_PARAM)
            val uri = MediaStorageUtils.getUriFromString(url)
            watermark!!.setImageName(text)
            watermark!!.setImageUri(uri)
            currentConfiguration!!.logoUrl = url
            currentConfiguration!!.logoName = text
        } else if (requestCode == MOCK_USER_DETAILS_REQUEST && resultCode == 2) {
            val customUserImageUrl = data!!.getStringExtra(ImageTextEditActivity.PRESET_URI_PARAM)
            val customDisplayName = data.getStringExtra(ImageTextEditActivity.PRESET_TEXT_PARAM)
            val mockProviderMode = data.getSerializableExtra(MockUserDetailsSettingsActivity.MOCK_MODE_PARAM) as CustomUserDetailsProvider
            currentConfiguration!!.customUserDetailsImageUrl = customUserImageUrl
            currentConfiguration!!.customUserDetailsName = customDisplayName
            currentConfiguration!!.customUserDetailsProvider = mockProviderMode
            mock_user_details!!.setSubtitle(mockProviderMode.toString())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.configuration_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
            R.id.qr_code -> show(this, currentConfiguration, true)
            R.id.reset_all -> {
                setDefaultValues(getInitialConfiguration())
            }
            R.id.save -> {
                if (currentConfiguration!!.isMockConfiguration()) {
                    showErrorDialog(resources.getString(R.string.settings_are_not_correctly_set))
                    return true
                }
                sendBroadcastConfigurationResult(CONFIGURATION_ACTION_UPDATE, currentConfiguration)
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }
}