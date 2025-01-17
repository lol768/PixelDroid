package org.pixeldroid.app.posts.feeds.cachedFeeds

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState.NotLoading
import androidx.paging.PagingDataAdapter
import androidx.paging.RemoteMediator
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import org.pixeldroid.app.databinding.FragmentFeedBinding
import org.pixeldroid.app.posts.feeds.initAdapter
import org.pixeldroid.app.utils.BaseFragment
import org.pixeldroid.app.utils.api.objects.FeedContentDatabase
import org.pixeldroid.app.utils.db.AppDatabase
import org.pixeldroid.app.utils.db.dao.feedContent.FeedContentDao
import org.pixeldroid.app.utils.limitedLengthSmoothScrollToPosition

/**
 * A fragment representing a list of [FeedContentDatabase] items that are cached by the database.
 */
open class CachedFeedFragment<T: FeedContentDatabase> : BaseFragment() {

    internal lateinit var viewModel: FeedViewModel<T>
    internal lateinit var adapter: PagingDataAdapter<T, RecyclerView.ViewHolder>

    private lateinit var binding: FragmentFeedBinding


    private var job: Job? = null


    @ExperimentalPagingApi
    internal fun launch() {
        // Make sure we cancel the previous job before creating a new one
        job?.cancel()
        job = lifecycleScope.launchWhenStarted {
            viewModel.flow.collectLatest {
                adapter.submitData(it)
            }
        }
    }

    internal fun initSearch() {
        // Scroll to top when the list is refreshed from network.
        lifecycleScope.launchWhenStarted {
            adapter.loadStateFlow
                // Only emit when REFRESH LoadState for RemoteMediator changes.
                .distinctUntilChangedBy {
                    it.refresh
                }
                // Only react to cases where Remote REFRESH completes i.e., NotLoading.
                .filter { it.refresh is NotLoading}
                .collect { binding.list.scrollToPosition(0) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        super.onCreateView(inflater, container, savedInstanceState)

        binding = FragmentFeedBinding.inflate(layoutInflater)

        initAdapter(binding.progressBar, binding.swipeRefreshLayout,
            binding.list, binding.motionLayout, binding.errorLayout, adapter)

        return binding.root
    }

    fun onTabReClicked() {
        binding.list.limitedLengthSmoothScrollToPosition(0)
    }
}


/**
 * Factory that creates ViewModel from a [FeedContentRepository], to be used in cached feeds to
 * fetch the ViewModel that is responsible for preparing and managing the data
 * for a CachedFeedFragment
 */
class ViewModelFactory<U: FeedContentDatabase> @ExperimentalPagingApi constructor(private val db: AppDatabase?,
                                                                                  private val dao: FeedContentDao<U>?,
                                                                                  private val remoteMediator: RemoteMediator<Int, U>?,
                                                                                  private val feedContentRepository: FeedContentRepository<U> = FeedContentRepository(db!!, dao!!, remoteMediator!!)
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FeedViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FeedViewModel(feedContentRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}