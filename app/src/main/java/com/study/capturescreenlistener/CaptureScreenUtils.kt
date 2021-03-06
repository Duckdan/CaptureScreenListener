package com.study.capturescreenlistener

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.study.capturescreenlistener.contentobserver.UriObserver

/**
 * 判断是否被截屏或者录屏。
 * 因为Android并没有提供监听截屏或者录屏的api，但是可以知道的是不管截屏或者录屏生成的文件都会被保存到手机中，
 * 一般保存的文件夹命名如下KEYWORDS所示，所以可以通过监听相关的URI的的内容，从而达到监听截屏或者录屏的问题。
 * 添加一个内容观察者就好了，录制屏或者截图都会调用这个。
 * 如果添加两个内容观察者的话change方法将会被调用两次
 */
@SuppressLint("StaticFieldLeak")
object CaptureScreenUtils : UriObserver.UriChangeListener {

    private var contentResolver: ContentResolver? = null
    private var videoObserver: UriObserver
    private var imageObserver: UriObserver
    private var mainHandler: Handler


    private var context: Context? = null
    private val KEYWORDS = arrayOf(
        "screenshot",
        "screenshots",
        "screen_shot",
        "screen-shot",
        "screen shot",
        "screencapture",
        "screen_capture",
        "screen-capture",
        "screen capture",
        "screencap",
        "screen_cap",
        "screen-cap",
        "screen cap",
        "screenrecorder"
    )
    private val screenPicture = mutableSetOf<String>()
    private var screenTime = 0L
    init {
        val handlerThread = HandlerThread("uriobserver")
        handlerThread.start()
        val handler = Handler(handlerThread.looper)
        mainHandler = Handler(Looper.getMainLooper())

        videoObserver = UriObserver(handler)
        videoObserver.setOnUriChangeListener(this)

        imageObserver = UriObserver(handler)
        imageObserver.setOnUriChangeListener(this)
    }


    override fun change(selfChange: Boolean, uri: Uri) {
        contentResolver?.let { it ->
            val uriPath = uri.path?.toLowerCase() ?: ""
            it.query(
                uri,
                null,
                null,
                null,
                "${if(uriPath.contains("video")) MediaStore.Video.VideoColumns.DATE_ADDED else MediaStore.Images.ImageColumns.DATE_ADDED } desc limit 1"
            )?.takeIf { cursor ->
                cursor.moveToFirst()
            }?.use { cursor ->
                var path = ""
                var dateAdd = 0L
                var relativePath = ""
                var type = ""
                val uriPath = uri.path?.toLowerCase() ?: ""
                Log.e(javaClass.simpleName, "uriPath=====$uriPath,  uri=====$uri")

                // 此时处于子线程中
                when {
                    uriPath.contains("video") -> { //录屏
                        type = "video"
                        path =
                            cursor.getColumnIndex(MediaStore.Video.VideoColumns.DATA).takeIf {
                                it > -1
                            }?.run {
                                cursor.getString(this).toLowerCase()
                            } ?: ""


                        //小米手机没这个字段
                        relativePath =
                            cursor.getColumnIndex(MediaStore.Images.ImageColumns.RELATIVE_PATH)
                                .takeIf {
                                    it > -1
                                }?.run {
                                    cursor.getString(this)
                                } ?: ""


                        dateAdd =
                            cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_ADDED)
                                .takeIf {
                                    it > -1
                                }?.run {
                                    cursor.getLong(this)
                                } ?: 0L
                    }
                    uriPath.contains("images") -> {//截屏
                        type = "images"
                        val dataIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
                        path = cursor.getString(dataIndex).toLowerCase()

                        val relativePathIndex =
                            cursor.getColumnIndex(MediaStore.Images.ImageColumns.RELATIVE_PATH)
                        relativePath = cursor.getString(relativePathIndex)

                        val dateAddIndex =
                            cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_ADDED)
                        dateAdd = cursor.getLong(dateAddIndex)
                    }
                    else -> type = "未知"
                }

                Log.e(javaClass.simpleName, "type=====$type,  data=====$path,  uri=====$uri")
                Log.e(
                    javaClass.simpleName,
                    "type=====$type,  relativePath=====$relativePath,  uri=====$uri"
                )
                Log.e(javaClass.simpleName, "type=====$type,  dateAdd=====$dateAdd,  uri=====$uri,  当前时间：${System.currentTimeMillis()}")

                for (keyword in KEYWORDS) {
                    /**
                     * 利用添加的时间来去除重复提醒的问题，因为多个机型出现录屏的时候也会通知截屏的uri;截屏的时候也会通知录屏的uri
                     */
                    if (path.contains(keyword)&& dateAdd> screenTime) {
                        screenTime = dateAdd
                        //还在子线程中，在这里处理发现被录屏或者截屏的逻辑
                        mainHandler.post {
                            Toast.makeText(context, "当前被截屏了", Toast.LENGTH_SHORT).show()
                        }
                        break
                    }
                }
            } ?: Toast.makeText(this.context, "数据为空", Toast.LENGTH_SHORT).show()
        }
    }

    fun register(context: Context) {
        this.context = context
        val contentResolver = context.contentResolver
        contentResolver?.let { contentResolver ->
            this.contentResolver = contentResolver
            /**
             * 小米手机录屏保存视频时不会触发MediaStore.Images.Media.EXTERNAL_CONTENT_URI，所以要添加 MediaStore.Video.Media.EXTERNAL_CONTENT_URI
             * 荣耀10手机录屏保存视频时会触发MediaStore.Images.Media.EXTERNAL_CONTENT_URI
             *
             *   //notifyForDescendants必须用true,因为有的机型只通知子uri
             */
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                videoObserver
            )
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                imageObserver
            )

        }
    }

    fun unregister() {
        contentResolver?.let {
            it.unregisterContentObserver(videoObserver)
            it.unregisterContentObserver(imageObserver)
        }
        contentResolver = null
        context = null
    }
}