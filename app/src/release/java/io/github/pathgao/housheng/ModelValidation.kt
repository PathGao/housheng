package io.github.pathgao.housheng

import android.widget.LinearLayout

object ModelValidation {
    fun maxAgeMs(source: String): Long = 500
    fun classifier(source: String, fallback: Classifier): Classifier = fallback
    fun addControls(body: LinearLayout) = Unit
}
