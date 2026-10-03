package io.github.pathgao.housheng

import org.junit.Assert.*
import org.junit.Test

class OnlineDefaultsTest {
    /** A fresh process must start with every network path off: no real app is read and no source reaches the model. */
    @Test fun everyModelPathStartsOff() {
        assertTrue(ModelValidation.realEnabled.isEmpty())
        val local = Classifier { Decision.KEEP }
        for (source in AppCatalog.sources.keys) assertSame(source, local, ModelValidation.classifier(source, local))
    }
}
