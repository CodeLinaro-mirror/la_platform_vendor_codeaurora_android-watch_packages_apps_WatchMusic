/**
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.music

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder

class RoundSongsPickerAdapter(private val mListener : SongSelection) :
        RecyclerView.Adapter<RoundSongsPickerAdapter.SongViewHolder>() {
    private val songs = mutableListOf<SongPick>()
    private var selectedPosition = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.music_picker_list_item, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(songs[position], position == selectedPosition)
    }

    fun onSelectionChanged(newPosition: Int) {
        val oldSelected = selectedPosition
        selectedPosition = newPosition
        notifyItemChanged(oldSelected)
        notifyItemChanged(newPosition)
    }

    override fun getItemCount() = songs.size

    fun updateSongs(items: List<SongPick>) {
        songs.clear()
        songs.addAll(items)
        notifyDataSetChanged()
    }

    interface SongSelection {
        fun onSongSelectionChanged(song: SongPick)
    }

    inner class SongViewHolder(private val view: View) : ViewHolder(view) {

        private val title = view.findViewById<TextView>(R.id.title)
        private val playIndicator = view.findViewById<View>(R.id.play_indicator)

        fun bind(song: SongPick, isSelected: Boolean) {
            title.text = song.title
            playIndicator.visibility = if (song.isPlaying) View.VISIBLE
            else View.INVISIBLE
            view.setBackgroundResource(
                if (isSelected) R.drawable.song_item_selected_bg
                else R.drawable.song_item_bg
            )
            view.setOnClickListener {
                mListener.onSongSelectionChanged(song)
            }
        }
    }
}