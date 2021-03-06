package com.andreacioccarelli.musicdownloader.ui.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.content.*
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.andreacioccarelli.logkit.logd
import com.andreacioccarelli.logkit.loge
import com.andreacioccarelli.musicdownloader.R
import com.andreacioccarelli.musicdownloader.constants.*
import com.andreacioccarelli.musicdownloader.data.formats.Format
import com.andreacioccarelli.musicdownloader.data.requests.DownloadLinkRequestsBuilder
import com.andreacioccarelli.musicdownloader.data.serializers.DirectLinkResponse
import com.andreacioccarelli.musicdownloader.data.serializers.Result
import com.andreacioccarelli.musicdownloader.extensions.*
import com.andreacioccarelli.musicdownloader.ui.drawables.GradientGenerator
import com.andreacioccarelli.musicdownloader.util.ChecklistStore
import com.andreacioccarelli.musicdownloader.util.QueueStore
import com.andreacioccarelli.musicdownloader.util.VibrationUtil
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.pierfrancescosoffritti.androidyoutubeplayer.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.player.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.player.listeners.AbstractYouTubePlayerListener
import com.tapadoo.alerter.Alerter
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.find
import org.jetbrains.anko.uiThread

/**
 * Created by La mejor on 2018/Aug.
 * Part of the package andreacioccarelli.musicdownloader.ui.fragment
 */

@SuppressLint("ValidFragment")
class DownloadBottomDialog(val remoteResult: Result) : BottomSheetDialogFragment() {

    private var isInChecklist = false
    private var isInQueue = false
    private lateinit var titleTextView: TextView
    var title = ""

    override fun getTheme() = R.style.BottomSheetTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = BottomSheetDialog(requireContext(), theme)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_dialog, container, false)
        VibrationUtil.strong()

        title = remoteResult.snippet.title
            .replace("/", "")
            .removeSuffix(".mp4")
            .removeSuffix(".mp3")
            .renameIfEqual(".", "_.")
            .renameIfEqual("..", "__.")
            .removePrefix(".")

        titleTextView = view.find(R.id.thumb_title)
        titleTextView.text = title

        Glide.with(this.requireActivity())
                .load(remoteResult.snippet.thumbnails.medium.url)
                .thumbnail(0.1F)
                .into(view.find(R.id.thumb_icon))

        view.find<CardView>(R.id.thumbCard).setOnClickListener {
            val dialog = MaterialDialog(requireContext())
                    .title(text = "Change file name")
                    .input(prefill = titleTextView.text, waitForPositiveButton = true) { _, text ->
                        titleTextView.text = text
                        title = text.toString()
                    }
                    .positiveButton(text = "SUBMIT")

            with(dialog) {
                show()
                getInputField()?.let { input ->
                    input.selectAll()
                    input.addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(p0: Editable?) {
                            if (p0.isNullOrBlank()) {
                                dialog.setActionButtonEnabled(WhichButton.POSITIVE, false)
                                return
                            }

                            val text = p0.toString()
                            val inputField = dialog.getInputField()!!

                            if (text.contains("/")) {
                                inputField.error = "File name cannot contain /"
                                dialog.setActionButtonEnabled(WhichButton.POSITIVE, false)
                                return
                            }

                            if (text.endsWith(".mp3") || text.endsWith(".mp4")) {
                                inputField.error = "We will think about putting an extension, just enter the file name"
                                dialog.setActionButtonEnabled(WhichButton.POSITIVE, false)
                                return
                            }

                            dialog.setActionButtonEnabled(WhichButton.POSITIVE, true)
                        }

                        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

                        }

                        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

                        }

                    })
                }
            }
        }

        view.find<CardView>(R.id.play).setOnClickListener { openVideoInDialog() }
        view.find<CardView>(R.id.open_video).setOnClickListener { openVideo() }
        view.find<CardView>(R.id.open_channel).setOnClickListener { openChannel() }
        view.find<CardView>(R.id.copy_link).setOnClickListener { copyLink() }
        view.find<CardView>(R.id.share_link).setOnClickListener { shareLink() }
        view.find<CardView>(R.id.mp3).setOnClickListener { handleClick(Format.MP3) }
        view.find<CardView>(R.id.mp4).setOnClickListener { handleClick(Format.MP4) }

        val addTo = view.find<CardView>(R.id.add_to_list)
        val removeFrom = view.find<CardView>(R.id.remove_from_list)

        isInChecklist = ChecklistStore.contains(requireContext(), remoteResult.snippet.title)
        isInQueue = QueueStore.contains(requireContext(), remoteResult.snippet.title)

        if (isInChecklist) {
            removeFrom.setOnClickListener {
                ChecklistStore.remove(requireContext(), remoteResult.snippet.title)
                dismiss()
                VibrationUtil.medium()
                success("Added to Checklist")
            }

            addTo.visibility = View.GONE
        } else {
            addTo.setOnClickListener {
                ChecklistStore.add(requireContext(), remoteResult.snippet.title, remoteResult.snippet.thumbnails.medium.url)
                dismiss()
                VibrationUtil.medium()
                success("Removed from Checklist")
            }

            removeFrom.visibility = View.GONE
        }

        if (isInQueue) {
            view.find<CardView>(R.id.add_to_queue).also {
                it.setOnClickListener {
                    QueueStore.remove(requireContext(), remoteResult.snippet.title)
                    dismiss()
                    VibrationUtil.medium()
                    success("Removed from download queue")
                }
                view.find<TextView>(R.id.queueTitle).text = "Remove from Queue"
            }
        } else {
            view.find<CardView>(R.id.add_to_queue).also {
                it.setOnClickListener {
                    QueueStore.add(requireContext(), remoteResult.snippet.title, watchLink, false)
                    dismiss()
                    VibrationUtil.medium()
                    success("Added to download queue")
                }
                view.find<TextView>(R.id.queueTitle).text = "Add to Queue"
            }
        }

        return view
    }

    private fun openVideoInDialog() {
        val dialog = MaterialDialog(requireContext())
                .customView(R.layout.video_player_dialog, scrollable = false)

        dialog.window!!.setBackgroundDrawable(GradientGenerator.make(26F, R.color.Grey_1000, R.color.Grey_1000))
        val youtubePlayer = dialog.getCustomView()!!.find<YouTubePlayerView>(R.id.player)

        youtubePlayer.initialize({ initializedYouTubePlayer: YouTubePlayer ->
            initializedYouTubePlayer.addListener(object : AbstractYouTubePlayerListener() {
                override fun onReady() {
                    initializedYouTubePlayer.loadVideo(remoteResult.id.videoId, 0f)
                }
            })
        }, true)

        with(dialog) {
            show()
            setOnDismissListener {
                youtubePlayer.release()
            }
        }
    }

    private val watchLink = "$YOUTUBE_WATCH_URL${remoteResult.id.videoId}"

    private fun openVideo() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.run {
                intent.data = watchLink.toUri()
                setPackage(PACKAGE_YOUTUBE)
            }

            startActivity(intent)
        } catch (err: ActivityNotFoundException) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = watchLink.toUri()
            startActivity(intent)
        }
    }

    private fun openChannel() {
        val channelUrl = "$YOUTUBE_CHANNEL_URL${remoteResult.snippet.channelId}".toUri()

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.run {
                intent.data = channelUrl
                setPackage("com.google.android.youtube")
            }

            startActivity(intent)
        } catch (err: ActivityNotFoundException) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = channelUrl
            startActivity(intent)
        }
    }


    private fun copyLink() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", watchLink)
        clipboard.primaryClip = clip

        success("Link copied", R.drawable.copy)
        VibrationUtil.medium()
        dismiss()
    }

    private fun shareLink() {
        val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
        sharingIntent.run {
            type = "text/plain"
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, remoteResult.snippet.title)
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, watchLink)
        }
        startActivity(Intent.createChooser(sharingIntent, "Share link to"))
        VibrationUtil.medium()
        dismiss()
    }

    private lateinit var conversionAlert: Alerter

    private fun handleClick(format: Format) {
        VibrationUtil.medium()
        dismiss()
        val act = requireActivity()
        val downloadManager = act.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        doAsync {
            val requestBuilder = DownloadLinkRequestsBuilder.get(remoteResult.id.videoId, format)
            val request = OkHttpClient().newCall(requestBuilder).execute()
            val gson = Gson()

            val response = gson.fromJson(request.body()!!.string(), DirectLinkResponse::class.java)

            when {
                response.state == RESPONSE_OK -> processDownloadForReadyFile(act, response, downloadManager)
                response.state == RESPONSE_WAIT -> {
                    conversionAlert = Alerter.create(act)

                    with(conversionAlert) {
                        setTitle("Preparing download")
                        setText("Waiting server to process file...")
                        enableInfiniteDuration(true)
                        setBackgroundDrawable(GradientGenerator.appThemeGradient)
                        setIcon(R.drawable.download)
                        enableProgress(true)
                        show()
                    }

                    processDownloadForWaitingFile(act, requestBuilder, downloadManager)
                }
                response.state == RESPONSE_ERROR -> {
                    Alerter.create(act)
                            .setTitle("Error while downloading")
                            .setText(response.reason)
                            .setDuration(7_000)
                            .setBackgroundDrawable(GradientGenerator.appThemeGradient)
                            .setIcon(R.drawable.download_error)
                            .show()
                }
                else -> {
                    loge("Unknown state: $response")
                    throw IllegalStateException()
                }
            }
        }
    }

    private fun processDownloadForReadyFile(act: Activity, response: DirectLinkResponse, downloadManager: DownloadManager) {
        act.runOnUiThread {
            if (isInChecklist) ChecklistStore.remove(act, remoteResult.snippet.title)

            val fileName = if (titleTextView.text.isBlank() || titleTextView.text.isEmpty()) response.title else title
            val completeFileName = "$fileName.${response.format}"
            val fileDownloadLink = response.download.sanitize()

            Alerter.hide()
            Alerter.create(act)
                    .setTitle("Downloading file")
                    .setText(completeFileName)
                    .setDuration(7_000)
                    .setBackgroundDrawable(GradientGenerator.appThemeGradient)
                    .setIcon(R.drawable.download)
                    .show()

            val uri = fileDownloadLink.toUri()
            logd(fileDownloadLink, completeFileName)

            val downloadRequest = DownloadManager.Request(uri)

            with(downloadRequest) {
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverRoaming(true)
                setVisibleInDownloadsUi(true)
                setTitle("Downloading $title")
                setDescription(fileName)
                allowScanningByMediaScanner()
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                setDestinationInExternalPublicDir("", "MusicDownloader/$completeFileName")
            }

            downloadManager.enqueue(downloadRequest)
        }
    }

    private fun processDownloadForWaitingFile(act: Activity, requestBuilder: Request, downloadManager: DownloadManager) {
        doAsync {
            val nextRequest = OkHttpClient().newCall(requestBuilder).execute()
            val json = nextRequest.body()!!.string()
            val nextResponse = Gson().fromJson(json, DirectLinkResponse::class.java)

            uiThread {
                if (nextResponse.state != RESPONSE_ERROR) {
                    conversionAlert.updateState(nextResponse)

                    if (nextResponse.state == RESPONSE_OK) {
                        Alerter.clearCurrent(act)
                        processDownloadForReadyFile(act, nextResponse, downloadManager)
                    } else {
                        processDownloadForWaitingFile(act, requestBuilder, downloadManager)
                    }
                } else {
                    Alerter.create(act)
                            .setTitle("Can't download this file")
                            .setText("Video length exceeds 3 hours")
                            .setDuration(7_000)
                            .setBackgroundDrawable(GradientGenerator.errorGradient)
                            .setIcon(R.drawable.ic_error_outline_white_48dp)
                            .show()
                    return@uiThread
                }
            }
        }
    }
}