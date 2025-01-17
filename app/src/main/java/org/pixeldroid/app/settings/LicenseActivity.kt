package org.pixeldroid.app.settings

import android.os.Bundle
import com.mikepenz.aboutlibraries.Libs
import org.pixeldroid.app.R
import org.pixeldroid.app.databinding.OpenSourceLicenseBinding
import org.pixeldroid.app.utils.BaseThemedWithBarActivity

/**
 * Displays licenses for all app dependencies. JSON is
 * generated by the plugin https://github.com/cookpad/LicenseToolsPlugin.
 */
class LicenseActivity: BaseThemedWithBarActivity() {

    private lateinit var binding: OpenSourceLicenseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = OpenSourceLicenseBinding.inflate(layoutInflater)

        setContentView(binding.root)

        supportActionBar?.setTitle(R.string.dependencies_licenses)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val aboutLibsJson: String = applicationContext.resources.openRawResource(R.raw.aboutlibraries)
            .bufferedReader().use { it.readText() }

        val libs = Libs.Builder()
            .withJson(aboutLibsJson)
            .build()

        val adapter = OpenSourceLicenseAdapter(libs)
        binding.openSourceLicenseRecyclerView.adapter = adapter
    }
}