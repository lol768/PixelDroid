package org.pixeldroid.app.posts.feeds.cachedFeeds.postFeeds

import androidx.paging.*
import androidx.room.withTransaction
import org.pixeldroid.app.utils.db.AppDatabase
import org.pixeldroid.app.utils.di.PixelfedAPIHolder
import org.pixeldroid.app.utils.db.entities.HomeStatusDatabaseEntity
import java.lang.NullPointerException
import javax.inject.Inject


/**
 * RemoteMediator for the home feed.
 *
 * A [RemoteMediator] defines a set of callbacks used to incrementally load data from a remote
 * source into a local source wrapped by a [PagingSource], e.g., loading data from network into
 * a local db cache.
 */
@OptIn(ExperimentalPagingApi::class)
class HomeFeedRemoteMediator @Inject constructor(
    private val apiHolder: PixelfedAPIHolder,
    private val db: AppDatabase,
) : RemoteMediator<Int, HomeStatusDatabaseEntity>() {

    override suspend fun load(loadType: LoadType, state: PagingState<Int, HomeStatusDatabaseEntity>): MediatorResult {

        val maxId = when (loadType) {
            LoadType.REFRESH -> null
            LoadType.PREPEND -> {
                // No prepend for the moment, might be nice to add later
                return MediatorResult.Success(endOfPaginationReached = true)
            }
            LoadType.APPEND -> state.lastItemOrNull()?.id
                ?: return MediatorResult.Success(endOfPaginationReached = true)
        }

        try {
            val user = db.userDao().getActiveUser()
                    ?: return MediatorResult.Error(NullPointerException("No active user exists"))
            val api = apiHolder.api ?: apiHolder.setToCurrentUser()

            val apiResponse = api.timelineHome(
                max_id= maxId, limit = state.config.pageSize.toString()
            )

            val dbObjects = apiResponse.map{
                HomeStatusDatabaseEntity(user.user_id, user.instance_uri, it)
            }

            val endOfPaginationReached = apiResponse.isEmpty()

            db.withTransaction {
                // Clear table in the database
                if (loadType == LoadType.REFRESH) {
                    db.homePostDao().clearFeedContent(user.user_id, user.instance_uri)
                }
                db.homePostDao().insertAll(dbObjects)
            }
            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (exception: Exception) {
            return MediatorResult.Error(exception)
        }
    }
}