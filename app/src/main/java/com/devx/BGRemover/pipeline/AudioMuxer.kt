package com.devx.BGRemover.pipeline

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object AudioMuxer {

    private const val TAG = "AudioMuxer"

    fun mux(
        context: Context,
        videoFile: File,
        originalVideoUri: Uri,
        outputFile: File,
        trimStartUs: Long = 0L,
        trimEndUs: Long = Long.MAX_VALUE
    ): Boolean {

        // 1. Find audio track in original video
        val audioExt   = MediaExtractor()
        audioExt.setDataSource(context, originalVideoUri, null)
        val audioTrack = findTrack(audioExt, "audio/")

        if (audioTrack < 0) {
            Log.w(TAG, "No audio — copying video only")
            audioExt.release()
            videoFile.copyTo(outputFile, overwrite = true)
            return false
        }

        val audioFmt = audioExt.getTrackFormat(audioTrack)
        audioExt.selectTrack(audioTrack)

        // 2. Find video track in processed temp file
        val videoExt   = MediaExtractor()
        videoExt.setDataSource(videoFile.absolutePath)
        val videoTrack = findTrack(videoExt, "video/")

        if (videoTrack < 0) {
            Log.e(TAG, "No video track in processed file")
            audioExt.release()
            videoExt.release()
            return false
        }

        val videoFmt = videoExt.getTrackFormat(videoTrack)
        videoExt.selectTrack(videoTrack)

        // 3. Create muxer with both tracks
        val muxer    = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        val muxVideo = muxer.addTrack(videoFmt)
        val muxAudio = muxer.addTrack(audioFmt)
        muxer.start()

        val buf  = ByteBuffer.allocate(2 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()

        // 4. Write video track (already trimmed + PTS normalized to 0)
        videoExt.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        while (true) {
            info.size = videoExt.readSampleData(buf, 0)
            if (info.size < 0) break
            info.presentationTimeUs = videoExt.sampleTime
            info.flags              = MediaCodec.BUFFER_FLAG_SYNC_FRAME
            info.offset             = 0
            muxer.writeSampleData(muxVideo, buf, info)
            videoExt.advance()
        }

        // 5. Write audio track — seek to trim start, normalize PTS
        audioExt.seekTo(trimStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        while (true) {
            info.size = audioExt.readSampleData(buf, 0)
            if (info.size < 0) break

            // Stop audio at trim end
            if (trimEndUs != Long.MAX_VALUE && audioExt.sampleTime > trimEndUs) break

            // Normalize audio PTS to match video (both start from 0)
            info.presentationTimeUs = (audioExt.sampleTime - trimStartUs)
                .coerceAtLeast(0L)
            info.flags  = MediaCodec.BUFFER_FLAG_SYNC_FRAME
            info.offset = 0
            muxer.writeSampleData(muxAudio, buf, info)
            audioExt.advance()
        }

        // 6. Finalise
        muxer.stop()
        muxer.release()
        videoExt.release()
        audioExt.release()

        Log.i(TAG, "Mux done → ${outputFile.name}")
        return true
    }

    private fun findTrack(ext: MediaExtractor, prefix: String): Int {
        for (i in 0 until ext.trackCount) {
            val mime = ext.getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }
}