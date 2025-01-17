package org.pixeldroid.app.utils.api.objects

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.view.View
import androidx.core.net.toUri
import com.google.android.material.snackbar.Snackbar
import org.pixeldroid.app.R
import org.pixeldroid.app.posts.getDomain
import java.io.File
import java.io.Serializable
import java.time.Instant

/**
    Represents a status posted by an account.
    https://docs.joinmastodon.org/entities/status/
 */

open class Status(
    //Base attributes
    override val id: String,
    val uri: String? = "",
    val created_at: Instant? = null, //ISO 8601 Datetime
    val account: Account?,
    val content: String? = "", //HTML
    val visibility: Visibility? = Visibility.public,
    val sensitive: Boolean? = false,
    val spoiler_text: String? = "",
    val media_attachments: List<Attachment>? = null,
    val application: Application? = null,
    //Rendering attributes
    val mentions: List<Mention>? = null,
    val tags: List<Tag>? = null,
    val emojis: List<Emoji>? = null,
    //Informational attributes
    val reblogs_count: Int? = 0,
    val favourites_count: Int? = 0,
    val replies_count: Int? = 0,
    //Nullable attributes
    val url: String? = null, //URL
    val in_reply_to_id: String? = null,
    val in_reply_to_account: String? = null,
    val reblog: Status? = null,
    val poll: Poll? = null,
    val card: Card? = null,
    val language: String? = null, //ISO 639 Part 1 two-letter language code
    val text: String? = null,
    //Authorized user attributes
    val favourited: Boolean? = false,
    val reblogged: Boolean? = false,
    val muted: Boolean? = false,
    val bookmarked: Boolean? = false,
    val pinned: Boolean? = false,
) : Serializable, FeedContent
{
    companion object {
        const val POST_TAG = "postTag"
        const val VIEW_COMMENTS_TAG = "view_comments_tag"
        const val POST_COMMENT_TAG = "post_comment_tag"
    }

    fun getPostUrl() : String? = media_attachments?.firstOrNull()?.url
    fun getProfilePicUrl() : String? = account?.anyAvatar()
    fun getPostPreviewURL() : String? = media_attachments?.firstOrNull()?.previewNoPlaceholder


    fun getNLikes(context: Context) : CharSequence {
        return context.resources.getQuantityString(
                R.plurals.likes,
                favourites_count ?: 0,
                favourites_count ?: 0
        )
    }

    fun getNShares(context: Context) : CharSequence {
        return context.resources.getQuantityString(
                R.plurals.shares,
                reblogs_count ?: 0,
                reblogs_count ?: 0
        )
    }

    fun getStatusDomain(domain: String, context: Context) : String {
        val accountDomain = getDomain(account!!.url)
        return if(getDomain(domain) == accountDomain) ""
        else context.getString(R.string.from_other_domain).format(accountDomain)

    }

    fun downloadImage(context: Context, url: String, view: View, share: Boolean = false) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val downloadUri = Uri.parse(url)

        val title = url.substringAfterLast("/")
        val request = DownloadManager.Request(downloadUri).apply {
            setTitle(title)
            if(!share) {
                val directory = File(Environment.DIRECTORY_PICTURES)
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                setDestinationInExternalPublicDir(directory.toString(), title)
            }
        }
        val downloadId = downloadManager.enqueue(request)
        val query = DownloadManager.Query().setFilterById(downloadId)

        Thread {

            var msg = ""
            var lastMsg = ""
            var downloading = true

            while (downloading) {
                val cursor: Cursor = downloadManager.query(query)
                cursor.moveToFirst()
                if (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    == DownloadManager.STATUS_SUCCESSFUL
                ) {
                    downloading = false
                }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (!share) {
                    msg = when (status) {
                        DownloadManager.STATUS_FAILED ->
                            context.getString(R.string.image_download_failed)
                        DownloadManager.STATUS_RUNNING ->
                            context.getString(R.string.image_download_downloading)
                        DownloadManager.STATUS_SUCCESSFUL ->
                            context.getString(R.string.image_download_success)
                        else -> ""
                    }
                    if (msg != lastMsg && msg != "") {
                        Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show()
                        lastMsg = msg
                    }
                } else if (status == DownloadManager.STATUS_SUCCESSFUL) {

                    val ext = url.substringAfterLast(".", "*")

                    val path = cursor.getString(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                    )
                    val file = path.toUri()

                    val shareIntent: Intent = Intent.createChooser(Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, file)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        type = "image/$ext"
                    }, null)

                    context.startActivity(shareIntent)
                }
                cursor.close()
            }
        }.start()
    }

    enum class Visibility: Serializable {
        public, unlisted, private, direct
    }
}