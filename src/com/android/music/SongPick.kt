/**
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.music

data class SongPick(
    val title: String,
    val id: Long,
    val position: Int,
    var isPlaying: Boolean = false
)
