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

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.database.Cursor
import android.media.AudioManager
import android.os.IBinder
import android.os.RemoteException
import android.provider.MediaStore
import android.provider.MediaStore.Video.VideoColumns
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope

import com.android.music.MediaPlaybackService.REPEAT_NONE
import com.android.music.MediaPlaybackService.SHUFFLE_NONE
import com.google.android.material.bottomsheet.BottomSheetBehavior

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RoundMusicPlayerViewController(private val context: Context) {

    // Service
    private var mService: IMediaPlaybackService? = null
    private var mServiceToken: MusicUtils.ServiceToken? = null

    // Coroutines
    private val lifeCycleScope = (context as? AppCompatActivity)?.lifecycleScope
    private var refreshJob: Job? = null
    private var skipJob: Job? = null

    // Resources
    val blankString = context.resources.getString(R.string.blank)

    // Data observers
    private val _songs = MutableLiveData<List<Song>>()
    private val _bottomSheetState = MutableLiveData(BottomSheetBehavior.STATE_COLLAPSED)
    private val _playerBoard = MutableLiveData<PlayerBoard>(
        PlayerBoard(
            blankString,
            false,
            REPEAT_NONE,
            0L,
            0L,
            true,
            SHUFFLE_NONE,
            false
        )
    )

    val songs: LiveData<List<Song>> = _songs
    val bottomSheetState : LiveData<Int> = _bottomSheetState
    val playerBoard: LiveData<PlayerBoard> = _playerBoard

    // Data Holders
    private var mDuration = 0L
    private var songsIds = LongArray(0)

    // Listeners
    private val mStatusListener: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == MediaPlaybackService.META_CHANGED) {
                updateTrackInfo()
                setPauseButtonImage()
            } else if (action == MediaPlaybackService.PLAYSTATE_CHANGED) {
                setPauseButtonImage()
            } else if (action == MediaPlaybackService.SHUFFLE_CHANGED) {
                setShuffleButtonImage()
            } else if (action == MediaPlaybackService.REPEAT_CHANGED) {
                setRepeatButtonImage()
            }
        }
    }
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mService = IMediaPlaybackService.Stub.asInterface(service)
            initState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // TODO: Handle service disconnected state, retry binding service.s
            mService = null
        }
    }
    private val mediaButtonIntentReceiver = MediaButtonIntentReceiver()

    init {
        val activity = context as? Activity
        activity?.let {
           mServiceToken = MusicUtils.bindToService(activity, serviceConnection)
        }
    }

    private fun startRefresh() {
        refreshJob?.cancel()
        refreshJob = lifeCycleScope?.launch {
            while (isActive) {
                val delayTime = refreshNow()
                delay(delayTime)
            }
        }
    }

    private fun stopRefresh() {
        refreshJob?.cancel()
    }

    private fun refreshNow(): Long {
        if (mService == null) return MAX_PERIODIC_REFRESH_INTERVAL
        try {
            val position: Long = mService!!.position()
            if ((position >= 0) && (mDuration > 0)) {
                val visibility = playerBoard.value?.currentTimeVisibility ?: false
                if (mService!!.isComplete && position >= mDuration) {
                    _playerBoard.value = playerBoard.value?.copy(
                        elapsedTime = mDuration,
                        currentTimeVisibility = if (mService!!.isPlaying) true else !visibility,
                        hasTrackInfoUpdated = false
                    )
                } else {
                    _playerBoard.value = playerBoard.value?.copy(
                        elapsedTime = position,
                        currentTimeVisibility = if (mService!!.isPlaying) true else !visibility,
                        hasTrackInfoUpdated = false
                    )
                }

                if (!mService!!.isPlaying) {
                    return MAX_PERIODIC_REFRESH_INTERVAL
                }
            } else {
                _playerBoard.value = playerBoard.value?.copy(
                    elapsedTime = 0L,
                    hasTrackInfoUpdated = false
                )
            }
            // calculate the number of milliseconds until the next full second, so
            // the counter can be updated at just the right time
            val remaining = 1000 - (position % 1000)

            // approximate how often we would need to refresh the slider to
            // move it smoothly
            val width = 2000
            val smoothRefreshTime: Long = mDuration / width

            if (smoothRefreshTime > remaining) return remaining
            if (smoothRefreshTime < MIN_PERIODIC_REFRESH_INTERVAL)
                return MIN_PERIODIC_REFRESH_INTERVAL
            return smoothRefreshTime
        } catch (_: RemoteException) {
        }
        return MAX_PERIODIC_REFRESH_INTERVAL
    }

    fun initState() {
        mService?.let { service ->
            if (service.queue.isEmpty()) {
                _bottomSheetState.value = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        updateTrackInfo()
        setShuffleButtonImage()
        setRepeatButtonImage()
        setPauseButtonImage()
    }

    fun onActivityCreated() {
        val mediaButtonIntentFilter = IntentFilter()
        mediaButtonIntentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        mediaButtonIntentFilter.addAction(Intent.ACTION_MEDIA_BUTTON)
        context.registerReceiver(
            mediaButtonIntentReceiver,
            mediaButtonIntentFilter,
            Context.RECEIVER_EXPORTED
        )
    }

    fun onStart() {
        val statusFilter = IntentFilter()
        statusFilter.addAction(MediaPlaybackService.PLAYSTATE_CHANGED)
        statusFilter.addAction(MediaPlaybackService.META_CHANGED)
        statusFilter.addAction(MediaPlaybackService.SHUFFLE_CHANGED)
        statusFilter.addAction(MediaPlaybackService.REPEAT_CHANGED)
        context.registerReceiver(mStatusListener, statusFilter, Context.RECEIVER_EXPORTED)

        startRefresh()
    }

    fun onStop() {
        stopRefresh()
        context.unregisterReceiver(mStatusListener)
    }

    fun onActivityDestroyed() {
        MusicUtils.unbindFromService(mServiceToken)
        context.unregisterReceiver(mediaButtonIntentReceiver)
    }

    fun loadSongs() {
        songsIds = MusicUtils.getAllSongs(context) ?: LongArray(0)
        val songsNames = songsIds.mapIndexed { index, value ->
            Song(
                title = MusicUtils.getSongNameById(context, value), id = value, position = index
            )
        }
        _songs.value = songsNames
    }

    fun playSelected(position: Int) {
        playAll(songsIds, position)
        _bottomSheetState.value = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun doPauseResume() {
        try {
            mService?.apply {
                if (isPlaying) {
                    pause()
                } else {
                    play()
                }
                refreshNow()
                setPauseButtonImage()
            }
        } catch (_: RemoteException) {
        }
    }

    fun goNext() {
        mService?.let { service ->
            if (MusicUtils.isForbidPlaybackInCall(context, true)) {
                return
            }
            try {
                service.next()
            } catch (ex: RemoteException) {
                ex.printStackTrace()
            }
        }
    }

    fun goPrevious() {
        mService?.let { service ->
            if (MusicUtils.isForbidPlaybackInCall(context, true)) {
                return
            }
            try {
                val shuffle = service.shuffleMode
                val histSize = service.histSize
                if (isGoStart(shuffle, histSize)) {
                    service.seek(0)
                    service.play()
                } else {
                    service.prev()
                }
            } catch (ex: RemoteException) {
                ex.printStackTrace()
            }
        }

    }

    fun seekForward(duration: Int = SEEK_FORWARD_DURATION) {
        // TODO: check for playing state, if there is no song in queue, do not perform this
        mService?.let { service ->
            val currPos = service.position()
            Log.d(TAG, "seekForward: curr = $currPos")
            val newPos = (currPos + duration).coerceAtMost(mDuration)
            Log.d(TAG, "new: $newPos")
            service.seek(newPos)
            startRefresh()
        }
    }

    fun seekBackward(duration: Int = SEEK_BACKWARD_DURATION) {
        // TODO: check for playing state, if there is no song in queue, do not perform this
        mService?.let { service ->
            val currPos = service.position()
            val newPos = (currPos - duration).coerceAtLeast(0)
            service.seek(newPos)
            startRefresh()
        }
    }

    fun skipForwardPressed() {
        Log.d(TAG, "onSeekForwardPressed: ")
        skipJob?.cancel()
        skipJob = lifeCycleScope?.launch {
            while (isActive) {
                seekForward(SEEK_LONG_ACTION_SPEED)
                delay(SEEK_ACTION_DELAY)
            }
        }
    }

    fun skipForwardReleased() {
        Log.d(TAG, "onSeekForwardReleased: ")
        skipJob?.cancel()
    }

    fun skipBackwardPressed() {
        skipJob?.cancel()
        skipJob = lifeCycleScope?.launch {
            while (isActive) {
                seekBackward(SEEK_LONG_ACTION_SPEED)
                delay(SEEK_ACTION_DELAY)
            }
        }
    }

    fun skipBackwardReleased() {
        skipJob?.cancel()
    }

    fun cycleRepeat() {
        mService?.let { service ->
            try {
                val mode = service.repeatMode
                if (mode == MediaPlaybackService.REPEAT_NONE) {
                    service.repeatMode = MediaPlaybackService.REPEAT_ALL
                } else if (mode == MediaPlaybackService.REPEAT_ALL) {
                    service.repeatMode = MediaPlaybackService.REPEAT_CURRENT
                    if (service.shuffleMode != MediaPlaybackService.SHUFFLE_NONE) {
                        service.shuffleMode = MediaPlaybackService.SHUFFLE_NONE
                        setShuffleButtonImage()
                    }
                } else {
                    service.repeatMode = MediaPlaybackService.REPEAT_NONE
                }
                setRepeatButtonImage()
            } catch (_: RemoteException) {
            }
        }
    }

    fun toggleShuffle() {
        if (mService == null) {
            return
        }
        try {
            val shuffle = mService!!.shuffleMode
            if (shuffle == MediaPlaybackService.SHUFFLE_NONE) {
                mService!!.shuffleMode = MediaPlaybackService.SHUFFLE_NORMAL
                if (mService!!.repeatMode == MediaPlaybackService.REPEAT_CURRENT) {
                    mService!!.repeatMode = MediaPlaybackService.REPEAT_ALL
                    setRepeatButtonImage()
                }
            } else if (shuffle == MediaPlaybackService.SHUFFLE_NORMAL ||
                shuffle == MediaPlaybackService.SHUFFLE_AUTO) {
                mService!!.shuffleMode = MediaPlaybackService.SHUFFLE_NONE
            } else {
                Log.e(
                    "MediaPlaybackActivity", "Invalid shuffle mode: " + shuffle
                )
            }
            setShuffleButtonImage()
        } catch (_: RemoteException) {
        }
    }

    fun performActionOnSong(song: Song, action: RoundSongsAdapter.Action) {
        when (action) {
            RoundSongsAdapter.Action.PLAY -> {
                playSelected(song.position)
            }

            RoundSongsAdapter.Action.DELETE -> {
                // Already Handled
            }

            RoundSongsAdapter.Action.SET_AS_RINGTONE -> {
                MusicUtils.setRingtone(context, song.id)
                val ringtoneMessage = context.getString(R.string.set_as_ringtone, song.title)
                toast(ringtoneMessage)
            }

            RoundSongsAdapter.Action.SHARE -> {
                shareAudio(song.id)
            }

            RoundSongsAdapter.Action.DETAILS -> {

            }
        }
    }

    private fun playAll(
        list: LongArray, pos: Int
    ) {
        var position = pos
        if (MusicUtils.isForbidPlaybackInCall(context, true)) {
            return
        }
        if (list.isEmpty() || mService == null) {
            val emptyListMessage = context.getString(R.string.empty_list)
            toast(emptyListMessage)
            return
        }
        try {
            val repeatMode: Int = mService?.getRepeatMode() ?: return

            if (MusicUtils.mPlayAllFromMenu) {
                mService?.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE)

                //If the repeat mode is REPEAT_CURRENT, we should change mode to REPEAT_ALL
                if (repeatMode == MediaPlaybackService.REPEAT_CURRENT) {
                    mService?.setRepeatMode(MediaPlaybackService.REPEAT_ALL)
                }

                MusicUtils.mPlayAllFromMenu = false
            }

            val currId: Long = mService?.getAudioId() ?: return
            val currPos: Int = mService?.getQueuePosition() ?: return
            if (position != -1 &&
                currPos == position &&
                currId == list[position] &&
                !MusicUtils.mRepeatPlay) {
                // The selected file is the file that's currently playing;
                // figure out if we need to restart with a new playlist,
                // or just launch the playback activity.
                val playlist: LongArray = mService?.getQueue() ?: return
                if (list.contentEquals(playlist)) {
                    // we don't need to set a new list, but we should resume playback if needed
                    mService?.play()
                    return  // the 'finally' block will still run
                }
            }
            if (position < 0) {
                position = 0
            }
            mService?.open(list, position)
            mService?.play()
        } catch (_: RemoteException) {
        } finally {
        }
    }

    fun isPlayingQueueEmpty() : Boolean {
        return mService?.queue?.size == 0
    }

    private fun setPauseButtonImage() {
        try {
            val playing = mService != null && mService!!.isPlaying
            _playerBoard.value = playerBoard.value?.copy(
                isPlaying = playing,
                hasTrackInfoUpdated = false
            )
        } catch (_: RemoteException) {
        }
    }

    private fun updateTrackInfo() {
        try {
            val path: String = mService?.getTrackName() ?: return
            val songId = mService?.getAudioId() ?: return

            // fetch clip duration from media database if available
            val cursor = MusicUtils.query(
                context,
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId),
                arrayOf<String>(MediaStore.Audio.Media.DURATION),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                mDuration = cursor.getInt(0).toLong()
                // if duration in media database is not valid,
                // fall back to media service duration
                if (mDuration <= 0) {
                    mDuration = mService?.duration() ?: return
                }
                cursor.close()
            } else {
                mDuration = mService?.duration() ?: return
            }

            _playerBoard.value = playerBoard.value?.copy(
                songTitle = path,
                duration = mDuration,
                hasTrackInfoUpdated = true
            )
        } catch (exc: RemoteException) {
            Log.e(TAG, "exception: ${exc.message}")
        } catch (exc: NullPointerException) {
            Log.e(TAG, "exception: ${exc.message}")
        } catch (exc: IllegalStateException) {
            Log.e(TAG, "exception: ${exc.message}")
        }
    }

    private fun setRepeatButtonImage() {
        mService?.let {
            try {
                _playerBoard.value = playerBoard.value?.copy(
                    repeatMode = mService!!.repeatMode,
                    hasTrackInfoUpdated = false
                )
            } catch (_: RemoteException) {
            }
        }

    }

    private fun setShuffleButtonImage() {
        mService?.let { service ->
            try {
                _playerBoard.value = playerBoard.value?.copy(
                    shuffleMode = service.shuffleMode,
                    hasTrackInfoUpdated = false
                )
            } catch (_: RemoteException) {
            }
        }
    }

    private fun shareAudio(id: Long): Boolean {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.setType("audio/*")

        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

        val canBeShared = true
        var filepath: String? = null
        val scheme = uri.scheme
        if ("file" == scheme) {
            filepath = uri.path
        } else {
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    uri, arrayOf<String>(VideoColumns.DATA), null, null, null
                )
                if (cursor != null && cursor.moveToNext()) {
                    filepath = cursor.getString(0)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "cannot get path from: $uri")
            } finally {
                cursor?.close()
            }
        }
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        if (canBeShared) context.startActivity(shareIntent)
        return true
    }

    private fun isGoStart(shuffle: Int, histSize: Int): Boolean {
        try {
            mService?.let { service ->
                val isOverTime = service.position() < SWITCH_MUSIC_MAX_TIME
                val isShuffleMode = shuffle == MediaPlaybackService.SHUFFLE_NORMAL
                val isHistLess = histSize == 0 || histSize == 1
                return (isOverTime && isShuffleMode && isHistLess) || !isOverTime
            }
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        return false
    }

    private fun toast(message: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, length).show()
    }

    companion object {
        const val TAG = "MUSIC_APP"
        const val SWITCH_MUSIC_MAX_TIME: Int = 2000

        const val SEEK_FORWARD_DURATION = 10000
        const val SEEK_BACKWARD_DURATION = 10000

        const val SEEK_LONG_ACTION_SPEED = 3000

        const val SEEK_ACTION_DELAY = 300L

        private const val MAX_PERIODIC_REFRESH_INTERVAL= 500L
        private const val MIN_PERIODIC_REFRESH_INTERVAL= 20L
    }

    // Data model for view
    data class PlayerBoard(
        val songTitle: String,
        val isPlaying: Boolean,
        val repeatMode: Int,
        val duration: Long,
        val elapsedTime: Long,
        val currentTimeVisibility: Boolean = true,
        val shuffleMode: Int,
        val hasTrackInfoUpdated: Boolean
    )
}