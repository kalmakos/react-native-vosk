package com.vosk

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enhanced Speech Service with configurable audio source and effects.
 * Based on org.vosk.android.SpeechService but with added echo cancellation support.
 */
class EnhancedSpeechService(
    private val recognizer: Recognizer,
    private val sampleRate: Float,
    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION
) {
    private val TAG = "EnhancedSpeechService"
    
    private var recorder: AudioRecord? = null
    private var recognizerThread: RecognizerThread? = null
    
    // Audio effects
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null

    private val isRecording = AtomicBoolean(false)

    /**
     * Start listening with optional timeout and audio effects
     */
    fun startListening(
        listener: RecognitionListener,
        timeoutMs: Int = NO_TIMEOUT,
        enableAEC: Boolean = false,
        enableNS: Boolean = false,
        enableAGC: Boolean = false
    ): Boolean {
        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return false
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate.toInt(),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: $bufferSize")
                return false
            }

            recorder = AudioRecord(
                audioSource,
                sampleRate.toInt(),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized properly")
                recorder?.release()
                recorder = null
                return false
            }

            // Initialize audio effects
            val audioSessionId = recorder!!.audioSessionId
            
            if (enableAEC && AcousticEchoCanceler.isAvailable()) {
                try {
                    acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId)
                    acousticEchoCanceler?.enabled = true
                    Log.d(TAG, "AcousticEchoCanceler enabled: ${acousticEchoCanceler?.enabled}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to initialize AcousticEchoCanceler", e)
                }
            } else if (enableAEC) {
                Log.w(TAG, "AcousticEchoCanceler not available on this device")
            }

            if (enableNS && NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(audioSessionId)
                    noiseSuppressor?.enabled = true
                    Log.d(TAG, "NoiseSuppressor enabled: ${noiseSuppressor?.enabled}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to initialize NoiseSuppressor", e)
                }
            } else if (enableNS) {
                Log.w(TAG, "NoiseSuppressor not available on this device")
            }

            if (enableAGC && AutomaticGainControl.isAvailable()) {
                try {
                    automaticGainControl = AutomaticGainControl.create(audioSessionId)
                    automaticGainControl?.enabled = true
                    Log.d(TAG, "AutomaticGainControl enabled: ${automaticGainControl?.enabled}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to initialize AutomaticGainControl", e)
                }
            } else if (enableAGC) {
                Log.w(TAG, "AutomaticGainControl not available on this device")
            }

            recorder?.startRecording()
            isRecording.set(true)

            recognizerThread = RecognizerThread(listener, timeoutMs)
            recognizerThread?.start()

            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            return false
        }
    }

    /**
     * Stop listening
     */
    fun stop(): Boolean {
        if (!isRecording.get()) {
            return false
        }

        isRecording.set(false)
        
        recognizerThread?.interrupt()
        recognizerThread = null

        return true
    }

    /**
     * Shutdown and release resources
     */
    fun shutdown() {
        stop()
        cleanup()
    }

    private fun cleanup() {
        try {
            // Release audio effects
            acousticEchoCanceler?.let {
                it.enabled = false
                it.release()
                acousticEchoCanceler = null
            }
            
            noiseSuppressor?.let {
                it.enabled = false
                it.release()
                noiseSuppressor = null
            }
            
            automaticGainControl?.let {
                it.enabled = false
                it.release()
                automaticGainControl = null
            }

            // Release audio recorder
            recorder?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
                recorder = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        }
    }

    private inner class RecognizerThread(
        private val listener: RecognitionListener,
        private val timeoutMs: Int
    ) : Thread() {

        override fun run() {
            val buffer = ShortArray(1024)
            var lastSpeechTime = System.currentTimeMillis()
            var speechDetected = false

            try {
                while (isRecording.get() && !isInterrupted) {
                    val nread = recorder?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (nread < 0) {
                        Log.e(TAG, "Error reading audio buffer: $nread")
                        break
                    }

                    if (nread > 0) {
                        val isFinal = recognizer.acceptWaveForm(buffer, nread)
                        
                        if (isFinal) {
                            val result = recognizer.result
                            listener.onResult(result)
                            speechDetected = false
                        } else {
                            val partialResult = recognizer.partialResult
                            listener.onPartialResult(partialResult)
                            
                            // Check if there's actual speech in partial result
                            if (partialResult.contains("\"partial\"") && 
                                partialResult.length > 20) {
                                speechDetected = true
                                lastSpeechTime = System.currentTimeMillis()
                            }
                        }

                        // Check for timeout
                        if (timeoutMs != NO_TIMEOUT && speechDetected) {
                            val silenceDuration = System.currentTimeMillis() - lastSpeechTime
                            if (silenceDuration > timeoutMs) {
                                Log.d(TAG, "Timeout reached after $silenceDuration ms of silence")
                                val finalResult = recognizer.finalResult
                                listener.onFinalResult(finalResult)
                                listener.onTimeout()
                                break
                            }
                        }
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "RecognizerThread interrupted")
            } catch (e: Exception) {
                Log.e(TAG, "Error in recognizer thread", e)
                listener.onError(e)
            } finally {
                cleanup()
            }
        }
    }

    companion object {
        const val NO_TIMEOUT = -1
    }
}
