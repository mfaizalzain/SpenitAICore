package com.fmz.spenitaicore.ui.components

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun NativeAdCard(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    if (adUnitId.isBlank()) return

    val context = LocalContext.current
    val headlineColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()
    val buttonTextColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    var nativeAd by remember(adUnitId) { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(context, adUnitId) {
        var loadedAd: NativeAd? = null
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                loadedAd?.destroy()
                loadedAd = ad
                nativeAd = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAd = null
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        adLoader.loadAd(AdRequest.Builder().build())

        onDispose {
            loadedAd?.destroy()
            nativeAd = null
        }
    }

    nativeAd?.let { ad ->
        Card(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .padding(12.dp),
                factory = {
                    createNativeAdView(
                        context = it,
                        headlineColor = headlineColor,
                        bodyColor = bodyColor,
                        accentColor = accentColor,
                        buttonTextColor = buttonTextColor
                    )
                },
                update = { adView ->
                    styleNativeAdView(
                        adView = adView,
                        headlineColor = headlineColor,
                        bodyColor = bodyColor,
                        accentColor = accentColor,
                        buttonTextColor = buttonTextColor
                    )
                    populateNativeAdView(ad, adView)
                }
            )
        }
    }
}

private fun createNativeAdView(
    context: android.content.Context,
    headlineColor: Int,
    bodyColor: Int,
    accentColor: Int,
    buttonTextColor: Int
): NativeAdView {
    val adView = NativeAdView(context)
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    val attribution = TextView(context).apply {
        text = "Ad"
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(accentColor)
    }

    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    val icon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)).also {
            it.marginEnd = dp(context, 12)
        }
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val headline = TextView(context).apply {
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 2
        setTextColor(headlineColor)
    }

    val body = TextView(context).apply {
        textSize = 13f
        maxLines = 2
        setTextColor(bodyColor)
    }

    val callToAction = Button(context).apply {
        textSize = 13f
        setTextColor(buttonTextColor)
        backgroundTintList = ColorStateList.valueOf(accentColor)
        minHeight = dp(context, 36)
        minimumHeight = dp(context, 36)
    }

    textColumn.addView(headline)
    textColumn.addView(body)
    row.addView(icon)
    row.addView(textColumn)
    root.addView(attribution)
    root.addView(row)
    root.addView(callToAction)
    adView.addView(root)

    adView.headlineView = headline
    adView.bodyView = body
    adView.iconView = icon
    adView.callToActionView = callToAction

    return adView
}

private fun styleNativeAdView(
    adView: NativeAdView,
    headlineColor: Int,
    bodyColor: Int,
    accentColor: Int,
    buttonTextColor: Int
) {
    (adView.headlineView as? TextView)?.setTextColor(headlineColor)
    (adView.bodyView as? TextView)?.setTextColor(bodyColor)
    (adView.callToActionView as? Button)?.let { button ->
        button.setTextColor(buttonTextColor)
        button.backgroundTintList = ColorStateList.valueOf(accentColor)
    }
    (((adView.getChildAt(0) as? LinearLayout)?.getChildAt(0)) as? TextView)?.setTextColor(accentColor)
}

private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
    (adView.headlineView as TextView).text = nativeAd.headline

    val bodyView = adView.bodyView as TextView
    bodyView.text = nativeAd.body.orEmpty()
    bodyView.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE

    val iconView = adView.iconView as ImageView
    val icon = nativeAd.icon
    if (icon == null) {
        iconView.visibility = View.GONE
    } else {
        iconView.setImageDrawable(icon.drawable)
        iconView.visibility = View.VISIBLE
    }

    val callToActionView = adView.callToActionView as Button
    callToActionView.text = nativeAd.callToAction.orEmpty()
    callToActionView.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE

    adView.setNativeAd(nativeAd)
}

private fun dp(context: android.content.Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
