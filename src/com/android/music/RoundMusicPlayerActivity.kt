/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Changes from Qualcomm Technologies, Inc. are provided under the following license:
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.music

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast

import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView

import com.codeaurora.music.custom.PermissionActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior

class RoundMusicPlayerActivity : AppCompatActivity(), RoundSongsAdapter.ItemActionListener {

    private val requiredPermissions = arrayOf(
        permission.READ_PHONE_STATE, permission.READ_MEDIA_AUDIO, permission.POST_NOTIFICATIONS
    )

    private lateinit var viewController: RoundMusicPlayerViewController
    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewController.loadSongs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (PermissionActivity.checkAndRequestPermission(this, requiredPermissions)) {
            SysApplication.getInstance().exit()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_round_music_player)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        viewController = RoundMusicPlayerViewController(this)
        viewController.onActivityCreated()
        initViews()
    }

    override fun onStart() {
        super.onStart()
        viewController.onStart()
        viewController.loadSongs()
    }

    override fun onStop() {
        super.onStop()
        viewController.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewController.onActivityDestroyed()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initViews() {

        val title = findViewById<TextView>(R.id.title)

        val repeatButton = findViewById<ImageView>(R.id.repeat)
        val mainMenu = findViewById<ImageView>(R.id.main_menu)

        val playPause = findViewById<ImageView>(R.id.btn_play_pause)
        val next = findViewById<ImageView>(R.id.btn_next)
        val previous = findViewById<ImageView>(R.id.btn_previous)

        val seekForward = findViewById<TextView>(R.id.seek_forward)
        val seekBackward = findViewById<TextView>(R.id.seek_backward)

        val progressBar = findViewById<CircularTimeBarView>(R.id.progress_bar)

        val bottomSheet = findViewById<View>(R.id.bottom_sheet)
        val listIcon = findViewById<View>(R.id.list_icon)
        val recyclerView = findViewById<WearableRecyclerView>(R.id.song_list)

        val mySongsAdapter = RoundSongsAdapter(this)
        recyclerView.adapter = mySongsAdapter
        val layoutManager = WearableLinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.isEdgeItemsCenteringEnabled = true

        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)

        // TODO: Manage button's click ability when audio focus changes or playing state changes.
        repeatButton.setOnClickListener {
            viewController.cycleRepeat()
        }
        mainMenu.setOnClickListener { view ->
            showMainMenuDialog(view)
        }
        playPause.setOnClickListener {
            viewController.doPauseResume()
        }
        next.setOnClickListener {
            viewController.goNext()
        }
        previous.setOnClickListener {
            viewController.goPrevious()
        }
        seekForward.setOnLongClickListener {
            viewController.skipForwardPressed()
            true
        }
        // Setting touch listener to get release of long press event.
        seekForward.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                viewController.skipForwardReleased()
            }
            false
        }
        seekBackward.setOnLongClickListener {
            viewController.skipBackwardPressed()
            true
        }
        seekBackward.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                viewController.skipBackwardReleased()
            }
            false
        }
        seekForward.setOnClickListener {
            viewController.seekForward()
        }
        seekBackward.setOnClickListener {
            viewController.seekBackward()
        }

        listIcon.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        recyclerView.requestLayout()
                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {}
                    else -> {}
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val bg = bottomSheet.background as? GradientDrawable ?: return
                val maxRadius = resources.getDimension(R.dimen.bottom_sheet_corner_radius)
                val currentRadius = maxRadius * (1 - slideOffset.coerceIn(0f, 1f))
                bg.cornerRadius = currentRadius
            }
        })

        viewController.bottomSheetState.observe(this) { state ->
            bottomSheetBehavior.state = state
        }

        viewController.songs.observe(this) { songs ->
            mySongsAdapter.updateSongs(songs)
        }

        viewController.playerBoard.observe(this) { board ->
            if(board.hasTrackInfoUpdated) {
                progressBar.setDuration(board.duration)
                title.text = board.songTitle
            }
            progressBar.setElapsedTime(board.elapsedTime)
            if (board.isPlaying) {
                playPause.setImageResource(R.drawable.play_pause)
            } else {
                playPause.setImageResource(R.drawable.play_arrow)
            }
            if(viewController.isPlayingQueueEmpty()) {
                repeatButton.isEnabled = false
                repeatButton.alpha = DISABLED_VIEW_ALPHA
                repeatButton.setImageResource(R.drawable.normal)
            } else {
                repeatButton.isEnabled = true
                repeatButton.alpha = 1f
                when (board.repeatMode) {
                    MediaPlaybackService.REPEAT_ALL ->
                        repeatButton.setImageResource(R.drawable.loopall)
                    MediaPlaybackService.REPEAT_CURRENT ->
                        repeatButton.setImageResource(R.drawable.loopone)
                    else -> repeatButton.setImageResource(R.drawable.normal)
                }
            }
        }
    }

    override fun onItemClicked(song: Song) {
        viewController.playSelected(song.position)
    }

    override fun onOptionsItemSelected(song: Song, action: RoundSongsAdapter.Action) {
        if (action == RoundSongsAdapter.Action.DELETE) {
            val list = LongArray(1)
            list[0] = song.id
            val bundle = Bundle()
            val filter: String = getString(
                R.string.delete_song_desc, song.title
            )
            bundle.putString("description", filter)
            bundle.putLongArray("items", list)
            val intent = Intent()
            intent.setClass(this, DeleteItems::class.java)
            intent.putExtras(bundle)
            deleteLauncher.launch(intent)
        } else if (action == RoundSongsAdapter.Action.DETAILS) {
            showDetails(song.id)
        }
        viewController.performActionOnSong(song, action)
    }

    private fun showMainMenuDialog(view: View) {

        val popupView = LayoutInflater.from(this).inflate(R.layout.main_menu_dialog, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        val params = window.attributes
        params.alpha = DISABLED_VIEW_ALPHA
        window.attributes = params

        popupWindow.setOnDismissListener {
            params.alpha = 1f
            window.attributes = params
        }

        val equalizer : View = popupView.findViewById(R.id.action_equalizer)
        val shuffleMode : View = popupView.findViewById(R.id.action_shuffle)
        val shuffleIcon: ImageView = popupView.findViewById(R.id.shuffle_icon)
        val shuffleText: TextView = popupView.findViewById(R.id.shuffle_text)

        equalizer.setOnClickListener {
            MusicUtils.startSoundEffectActivity(this)
            popupWindow.dismiss()
        }

        if(viewController.isPlayingQueueEmpty()) {
            shuffleMode.isEnabled = false
            shuffleMode.alpha = DISABLED_VIEW_ALPHA
        } else {
            shuffleMode.isEnabled = true
            shuffleMode.alpha = 1f
            shuffleMode.setOnClickListener {
                viewController.toggleShuffle()
                popupWindow.dismiss()
            }
            val board = viewController.playerBoard.value
            board?.let {
                when (it.shuffleMode) {
                    MediaPlaybackService.SHUFFLE_NONE -> {
                        shuffleIcon.setImageResource(R.drawable.random)
                        shuffleText.setTextColor(ContextCompat.getColor(this, R.color.album_text))
                    }
                    MediaPlaybackService.SHUFFLE_AUTO -> {
                        shuffleIcon.setImageResource(R.drawable.random_active)
                        shuffleText.setTextColor(ContextCompat.getColor(this, R.color.wave_color))
                    }
                    else -> {
                        shuffleIcon.setImageResource(R.drawable.random_active)
                        shuffleText.setTextColor(ContextCompat.getColor(this, R.color.wave_color))
                    }
                }
            }

        }

        popupWindow.showAtLocation(view.rootView, Gravity.CENTER, 0, 0)
    }

    private fun showDetails(id: Long) {
        val detailDialog = AlertDialog.Builder(this)
        val detailLayout = LayoutInflater.from(this).inflate(
            R.layout.file_info, null
        )
        detailDialog.setTitle(R.string.details)
        detailDialog.setView(detailLayout)
        detailDialog.setPositiveButton(
            R.string.button_ok
        ) { dialoginterface, i -> dialoginterface.cancel() }
        fillDetails(id, detailLayout)
        detailDialog.show()
    }

    private fun fillDetails(id: Long, contentView: View) {
        val where = StringBuilder()
        where.append(MediaStore.Audio.Media._ID + " = ")
        where.append(id)

        val mCursorCols = arrayOf(
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE
        )
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                mCursorCols,
                where.toString(),
                null,
                null
            )
            Log.d(RoundMusicPlayerViewController.TAG, "queried: ")
            cursor?.use { it ->
                if (it.moveToFirst()) {
                    val name =
                        it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME))
                    val duration =
                        it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                    val size = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
                    val path =
                        it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))

                    val songName = contentView.findViewById<View>(R.id.song_name) as TextView
                    val durationName =
                        contentView.findViewById<View>(R.id.duration_name) as TextView
                    val pathName = contentView.findViewById<View>(R.id.path_name) as TextView
                    val sizeName = contentView.findViewById<View>(R.id.size_name) as TextView

                    songName.text = name
                    val durationSecs = duration/1000
                    durationName.text = buildString {
                        append(MusicUtils.makeTimeString(this@RoundMusicPlayerActivity, durationSecs))
                        append(getString(R.string.duration_unit))
                    }
                    pathName.text = path
                    sizeName.text = formatFileSize(size)

                }
            }
        } catch (e: Exception) {
            Log.d(RoundMusicPlayerViewController.TAG, "fillDetails: ", e)
        } finally {
            cursor?.close()
        }
    }

    private fun formatFileSize(size: Long): String {
        val res = resources
        val magnitude = intArrayOf(
            R.string.size_bytes,
            R.string.size_kilobytes,
            R.string.size_megabytes,
            R.string.size_gigabytes
        )

        var aux = size.toDouble()
        val cc = magnitude.size
        for (i in 0..<cc) {
            if (aux < 1024) {
                val cleanSize = Math.round(aux * 100).toDouble()
                return (cleanSize / 100).toString() +
                        " " + res.getString(magnitude[i]) //$NON-NLS-1$
            } else {
                aux = aux / 1024
            }
        }
        val cleanSize = Math.round(aux * 100).toDouble()
        return (cleanSize / 100).toString() +
                " " + res.getString(magnitude[cc - 1]) //$NON-NLS-1$
    }

    companion object {
        const val DISABLED_VIEW_ALPHA = 0.4f
    }

}