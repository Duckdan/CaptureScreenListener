package com.study.capturescreenlistener.contentobserver

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.util.Log

class UriObserver(handler: Handler) : ContentObserver(handler) {
    private lateinit var uriChangeListener: UriChangeListener



    override fun onChange(selfChange: Boolean, uri: Uri) {
        super.onChange(selfChange, uri)
        Log.e("CaptureScreenUtils","UriObserver===$selfChange,  ${uri.toString()}")
        if (this::uriChangeListener.isInitialized && uri != null) {
            uriChangeListener.change(selfChange, uri)
        }
    }

    fun setOnUriChangeListener(uriChangeListener: UriChangeListener) {
        this.uriChangeListener = uriChangeListener
    }

    interface UriChangeListener {
        fun change(selfChange: Boolean, uri: Uri)
    }
}
