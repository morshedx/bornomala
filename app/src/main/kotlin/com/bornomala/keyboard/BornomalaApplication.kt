package com.bornomala.keyboard

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point and root of the Hilt dependency graph.
 *
 * Kept deliberately minimal: no work is performed in [onCreate] beyond what Hilt
 * requires, so the IME process cold-starts well within the < 300ms budget. Feature
 * initialization is lazy and performed off the main thread by the components that need
 * it (dictionaries, databases), not eagerly here.
 */
@HiltAndroidApp
class BornomalaApplication : Application()
