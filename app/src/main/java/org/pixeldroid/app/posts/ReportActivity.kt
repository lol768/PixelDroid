package org.pixeldroid.app.posts

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import org.pixeldroid.app.R
import org.pixeldroid.app.databinding.ActivityReportBinding
import org.pixeldroid.app.utils.BaseThemedWithBarActivity
import org.pixeldroid.app.utils.api.objects.Status

class ReportActivity : BaseThemedWithBarActivity() {

    private lateinit var binding: ActivityReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.report)

        val status = intent.getSerializableExtra(Status.POST_TAG) as Status?

        binding.reportTargetTextview.text = getString(R.string.report_target).format(status?.account?.acct)


        binding.reportButton.setOnClickListener{
            binding.reportButton.visibility = View.INVISIBLE
            binding.reportProgressBar.visibility = View.VISIBLE

            binding.textInputLayout.editText?.isEnabled = false

            val api = apiHolder.api ?: apiHolder.setToCurrentUser()

            lifecycleScope.launchWhenCreated {
                try {
                    api.report(
                        status?.account?.id!!,
                        listOf(status),
                        binding.textInputLayout.editText?.text.toString()
                    )

                    reportStatus(true)
                } catch (exception: Exception) {
                    reportStatus(false)
                }
            }
        }
    }

    private fun reportStatus(success: Boolean){
        if(success){
            binding.reportProgressBar.visibility = View.GONE
            binding.reportButton.visibility = View.INVISIBLE
            binding.reportSuccess.visibility = View.VISIBLE
        } else {
            binding.textInputLayout.error = getString(R.string.report_error)
            binding.reportButton.visibility = View.VISIBLE
            binding.textInputLayout.editText?.isEnabled = true
            binding.reportProgressBar.visibility = View.GONE
            binding.reportSuccess.visibility = View.GONE
        }
    }
}