package io.github.pathgao.housheng

import android.content.Context
import android.widget.LinearLayout

internal object ModelTransport {
    fun direct(context: Context) = true
    fun classify(context: Context, text: String): String = ClefApiClient(context).classify(text)
    fun addControls(body: LinearLayout) = Unit
}
