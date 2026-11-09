package com.walhalla.shiftalarm

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import java.io.File
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/** Fails the build when a constructor needs a type the graph does not provide. */
@OptIn(KoinExperimentalAPI::class)
class KoinGraphTest {
    @Test
    fun graphIsComplete() {
        module { includes(shiftAlarmModules) }.verify(
            extraTypes = listOf(Context::class, File::class, SavedStateHandle::class),
        )
    }
}
