/**
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.music

import android.Manifest.permission
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast

import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView

@RequiresApi(Build.VERSION_CODES.O)
class RoundMusicPickerActivity : AppCompatActivity(), RoundSongsPickerAdapter.SongSelection {

    private val REQUIRED_PERMISSIONS = arrayOf(
        permission.READ_MEDIA_AUDIO
    )

    private lateinit var viewController: RoundMusicPickerViewController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_round_music_picker)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        viewController = RoundMusicPickerViewController(this)
        initViews()
    }

    private fun initViews() {
        val recyclerView = findViewById<WearableRecyclerView>(R.id.music_list)
        val okayButton = findViewById<RelativeLayout>(R.id.okayButton)
        val cancelButton = findViewById<RelativeLayout>(R.id.cancelButton)

        val mySongsAdapter = RoundSongsPickerAdapter(this)
        recyclerView.adapter = mySongsAdapter
        recyclerView.layoutManager = WearableLinearLayoutManager(this)
        recyclerView.isEdgeItemsCenteringEnabled = true

        viewController.songs.observe(this) { songs ->
            mySongsAdapter.updateSongs(songs)
        }

        okayButton.setOnClickListener {
            val uri = viewController.getSelectedUri()
            uri?.let {
                setResult(RESULT_OK, Intent().apply { data = uri })
                finish()
            } ?: Toast.makeText(
                this, getString(R.string.null_uri_selected_message), Toast.LENGTH_LONG).show()
        }
        updateViewEnabled(okayButton, false)

        cancelButton.setOnClickListener {
            finish()
        }

        viewController.mSelectedSong.observe(this) { song ->
            song?.position?.let {
                mySongsAdapter.onSelectionChanged(it)
                updateViewEnabled(okayButton, true)
            }
        }

        cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun loadSongs() {
        if (!hasPermissions()) {
            val permissions = getRequireGrantPermissions()
            requestPermissions(permissions, REQUEST_CODE)
            return
        }
        viewController.loadSongs()
    }

    private fun getRequireGrantPermissions() =
        REQUIRED_PERMISSIONS.filterNot { hasPermission(it) }.toTypedArray()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPermissions()) {
            viewController.loadSongs()
        } else {
            Toast.makeText(this, R.string.dialog_content, Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun hasPermissions() = REQUIRED_PERMISSIONS.all { hasPermission(it) }

    private fun hasPermission(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun onStart() {
        super.onStart()
        loadSongs()
    }

    override fun onPause() {
        super.onPause()
        viewController.onActivityPaused()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onSongSelectionChanged(song: SongPick) {
        viewController.selectSong(song)
    }

    private fun updateViewEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if(enabled) ENABLED_VIEW_ALPHA else DISABLED_VIEW_ALPHA
    }

    companion object {
        private const val REQUEST_CODE = 101
        const val DISABLED_VIEW_ALPHA = 0.4f
        const val ENABLED_VIEW_ALPHA = 1f
    }
}