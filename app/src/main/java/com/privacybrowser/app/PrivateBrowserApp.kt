package com.privacybrowser.app

import android.app.Application

/**
 * Application entry point.
 *
 * Deliberately does NOT initialize any analytics, crash-reporting, or
 * remote-logging SDKs — this app does not collect browsing data remotely.
 */
class PrivateBrowserApp : Application()
