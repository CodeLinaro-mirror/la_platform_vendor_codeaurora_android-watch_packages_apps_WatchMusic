/**
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.music

import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.provider.MediaStore
import android.util.Log

import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/*  TESTING TODOs:
 *  1. Monkey testing for frequent clicks/long press of +10s, -10s buttons.
 *  2. Service kill test.
 *  3. Long press +10s, -10s simultaneously.
 */
class RoundMusicPickerViewController(private val context: Context) : MediaPlayer.OnCompletionListener {

    private val _songs = MutableLiveData<List<SongPick>>()
    val songs: LiveData<List<SongPick>> = _songs

    var mSelectedSong  = MutableLiveData<SongPick>()
    private var _songsIds = LongArray(0)

    private var mMediaPlayer: MediaPlayer? = null
    private val mAudioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var audioFocusRequest : AudioFocusRequest? = null

    fun loadSongs() {
        MusicUtils.getAllSongs(context)?.let {
            _songsIds = it
        }
        val songsNames = _songsIds.mapIndexed { index, value ->
            SongPick(
                title = MusicUtils.getSongNameById(context, value), id = value, position = index
            )
        }
        _songs.value = songsNames
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun selectSong(song: SongPick) {
        Log.d(TAG, "selectSong: selected ${song.position}")
        val oldPos = mSelectedSong.value?.position ?: -1
        val newPos = song.position
        if (oldPos != newPos || mMediaPlayer == null) {
            Log.d(TAG, "selectSong: new one")
            stopMediaPlayer()
            mSelectedSong.value = mSelectedSong.value?.apply { isPlaying = false }
            mSelectedSong.value = song
            mMediaPlayer = MediaPlayer()
            val baseUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val uri = ContentUris.withAppendedId(baseUri, mSelectedSong.value!!.id)
            audioFocusRequest =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()
            Log.d(TAG, "selectSong: requesting")
            val result = mAudioManager.requestAudioFocus(audioFocusRequest!!)
            if(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                try {
                    mMediaPlayer!!.setDataSource(context, uri)
                    mMediaPlayer!!.setOnCompletionListener(this)
                    mMediaPlayer!!.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    mMediaPlayer!!.prepare()
                    mMediaPlayer!!.start()
                    mSelectedSong.value = mSelectedSong.value?.apply { isPlaying = true }
                } catch (exception: Exception) {
                    Log.e(TAG, "unable to play track", exception)
                }
            }

        } else {
            stopMediaPlayer()
            mSelectedSong.value = mSelectedSong.value?.apply { isPlaying = false }
        }
    }

    fun stopMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer!!.stop()
            mMediaPlayer!!.release()
            mMediaPlayer = null
            audioFocusRequest?.let {
                mAudioManager.abandonAudioFocusRequest(it)
            }
        }
    }

    override fun onCompletion(mp: MediaPlayer?) {
        if(mMediaPlayer == mp) {
            stopMediaPlayer()
            mSelectedSong.value = mSelectedSong.value?.apply { isPlaying = false }
        }
    }

    fun getSelectedUri() = mSelectedSong.value?.let {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.id)
    }

    fun onActivityPaused() {
        stopMediaPlayer()
        mSelectedSong.value = mSelectedSong.value?.apply { isPlaying = false }
    }

    companion object {
        const val TAG = "ROUND_MUSIC_PICKER"
    }
}
