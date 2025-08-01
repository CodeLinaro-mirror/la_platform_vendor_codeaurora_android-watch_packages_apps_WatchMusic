/**
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.music

import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder

class RoundSongsAdapter(
    private val mListener: ItemActionListener
) : RecyclerView.Adapter<RoundSongsAdapter.SongViewHolder>() {

    private val songs = mutableListOf<Song>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.song_item, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount() = songs.size

    fun updateSongs(items: List<Song>) {
        songs.clear()
        songs.addAll(items)
        notifyDataSetChanged()
    }

    private fun showPopupWindow(view: View, song: Song) {
        val context = view.context
        val popupView = LayoutInflater.from(context).inflate(R.layout.song_menu_popup, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        val window = (context as? Activity)?.window ?: return
        val params = window.attributes
        params.alpha = 0.4f
        window.attributes = params

        popupWindow.setOnDismissListener {
            params.alpha = 1f
            window.attributes = params
        }

        popupView.findViewById<View>(R.id.action_play).setOnClickListener {
            mListener.onOptionsItemSelected(song, Action.PLAY)
            popupWindow.dismiss()
        }
        popupView.findViewById<View>(R.id.action_delete).setOnClickListener {
            mListener.onOptionsItemSelected(song, Action.DELETE)
            popupWindow.dismiss()
        }
        popupView.findViewById<View>(R.id.action_ringtone).setOnClickListener {
            mListener.onOptionsItemSelected(song, Action.SET_AS_RINGTONE)
            popupWindow.dismiss()
        }
        popupView.findViewById<View>(R.id.action_share).setOnClickListener {
            mListener.onOptionsItemSelected(song, Action.SHARE)
            popupWindow.dismiss()
        }
        popupView.findViewById<View>(R.id.action_details).setOnClickListener {
            mListener.onOptionsItemSelected(song, Action.DETAILS)
            popupWindow.dismiss()
        }
        popupWindow.showAtLocation(view.rootView, Gravity.CENTER, 0, 0)
    }

    enum class Action {
        PLAY, DELETE, SET_AS_RINGTONE, SHARE, DETAILS
    }

    interface ItemActionListener {
        fun onItemClicked(song: Song)
        fun onOptionsItemSelected(song: Song, action: Action)
    }

    inner class SongViewHolder(private val view: View) : ViewHolder(view) {

        private val more = view.findViewById<View>(R.id.more)
        private val title = view.findViewById<TextView>(R.id.title)

        fun bind(song: Song) {
            title.text = song.title
            more.setOnClickListener {
                showPopupWindow(more, song)
            }
            view.setOnClickListener {
                mListener.onItemClicked(song)
            }
        }
    }

}