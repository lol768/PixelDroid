package org.pixeldroid.app.utils

import android.content.*
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.browser.customtabs.CustomTabsIntent
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import okhttp3.HttpUrl
import org.pixeldroid.app.R
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun hasInternet(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return cm.activeNetwork != null
}

/**
 * Check if domain is valid or not
 */
fun validDomain(domain: String?): Boolean {
    domain?.apply {
        try {
            HttpUrl.Builder().host(replace("https://", "")).scheme("https").build()
        } catch (e: IllegalArgumentException) {
            return false
        }
    } ?: return false

    return true
}

fun Uri.fileExtension(contentResolver: ContentResolver): String? {
    return if (scheme == "content") {
        contentResolver.getType(this)?.takeLastWhile { it != '/' }
    } else {
        MimeTypeMap.getFileExtensionFromUrl(toString()).ifEmpty { null }
    }
}

fun Uri.getMimeType(contentResolver: ContentResolver, fallback: String = "image/*"): String {
    return if (scheme == "content") {
        contentResolver.getType(this)
    } else {
        MimeTypeMap.getFileExtensionFromUrl(toString())
            ?.run { MimeTypeMap.getSingleton().getMimeTypeFromExtension(lowercase(Locale.getDefault())) }
    } ?: fallback
}

fun Context.displayDimensionsInPx(): Pair<Int, Int> {
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Pair(windowManager.currentWindowMetrics.bounds.width(), windowManager.currentWindowMetrics.bounds.height())
    } else {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        Pair(metrics.widthPixels, metrics.heightPixels)
    }
}

fun normalizeDomain(domain: String): String {
    return "https://" + domain
            .replace("http://", "")
            .replace("https://", "")
            .trim(Char::isWhitespace)
}

fun Context.openUrl(url: String): Boolean {

    val intent = CustomTabsIntent.Builder().build()

    return try {
        intent.launchUrl(this, Uri.parse(url))
        true
    } catch (e: ActivityNotFoundException) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(browserIntent)
            true
        } catch(e: ActivityNotFoundException) {
            false
        }
    }
}


fun RecyclerView.limitedLengthSmoothScrollToPosition(targetItem: Int) {
    layoutManager?.apply {
        val maxScroll = 3
        when (this) {
            is LinearLayoutManager -> {
                val topItem = findFirstVisibleItemPosition()
                val distance = topItem - targetItem
                val anchorItem = when {
                    distance > maxScroll -> targetItem + maxScroll
                    distance < -maxScroll -> targetItem - maxScroll
                    else -> topItem
                }
                if (anchorItem != topItem) scrollToPosition(anchorItem)
                post {
                    smoothScrollToPosition(targetItem)
                }
            }
            else -> smoothScrollToPosition(targetItem)
        }
    }
}

/**
 * @brief Updates the application's theme depending on the given preferences and resources
 */
fun setThemeFromPreferences(preferences: SharedPreferences, resources: Resources) {
    val themes = resources.getStringArray(R.array.theme_values)
    //Set the theme
    when(preferences.getString("theme", "")) {
        //Light
        themes[1] -> {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        //Dark
        themes[2] -> {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
            }
        }
    }
}

@StyleRes
fun Context.themeNoActionBar(): Int? {
    return when(PreferenceManager.getDefaultSharedPreferences(this).getInt("themeColor", 0)) {
        // No theme was chosen: the user wants to use the system dynamic color (from wallpaper for example)
        -1 -> null
        1 -> R.style.AppTheme2_NoActionBar
        2 -> R.style.AppTheme3_NoActionBar
        3 -> R.style.AppTheme4_NoActionBar
        else -> R.style.AppTheme5_NoActionBar
    }
}

@StyleRes
fun Context.themeActionBar(): Int? {
    return when(PreferenceManager.getDefaultSharedPreferences(this).getInt("themeColor", 0)) {
        // No theme was chosen: the user wants to use the system dynamic color (from wallpaper for example)
        -1 -> null
        1 -> R.style.AppTheme2
        2 -> R.style.AppTheme3
        3 -> R.style.AppTheme4
        else -> R.style.AppTheme5
    }
}

@ColorInt
fun Context.getColorFromAttr(@AttrRes attrColor: Int): Int = MaterialColors.getColor(this, attrColor, Color.BLACK)


val typeAdapterInstantDeserializer: JsonDeserializer<Instant> = JsonDeserializer { json: JsonElement, _, _ ->
    DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(
        json.asString, Instant::from
    )
}

val typeAdapterInstantSerializer: JsonSerializer<Instant> = JsonSerializer { src: Instant, _, _ ->
    JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(src))
}

/**
 * Delegated property to use in fragments to prevent memory leaks of bindings.
 * This makes it unnecessary to set binding to null in onDestroyView.
 * The value should be assigned in the Fragment's onCreateView()
 */
fun <T> Fragment.bindingLifecycleAware(): ReadWriteProperty<Fragment, T> =
    object : ReadWriteProperty<Fragment, T>, DefaultLifecycleObserver {

        private var binding: T? = null

        override fun onDestroy(owner: LifecycleOwner) {
            binding = null
        }

        override fun getValue(thisRef: Fragment, property: KProperty<*>): T = binding!!

        override fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
            binding = value
            this@bindingLifecycleAware.viewLifecycleOwner.lifecycle.addObserver(this)
        }
    }