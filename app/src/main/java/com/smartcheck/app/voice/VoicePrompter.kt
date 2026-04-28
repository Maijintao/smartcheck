package com.smartcheck.app.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.smartcheck.app.domain.repository.IVoiceService
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoicePrompter @Inject constructor(
    @ApplicationContext context: Context
) : IVoiceService, TextToSpeech.OnInitListener {

    companion object {
        private const val PROMPT_DIR_NAME = "voice_prompts"
    }

    private val isReady = AtomicBoolean(false)
    private val pending = ArrayDeque<String>()
    private val enabledRef = AtomicReference(true)
    private val promptFiles = linkedMapOf(
        "欢迎" to "welcome.wav",
        "今日已晨检" to "already_checked_today.wav",
        "请将人脸对准摄像头" to "face_guide.wav",
        "正在测温" to "temp_measuring.wav",
        "人脸未录入" to "face_not_enrolled.wav",
        "健康证已过期" to "health_cert_expired.wav",
        "请将手心放入检测仪" to "hand_palm.wav",
        "请同时伸出两只手心" to "hand_palm.wav",
        "请将手背放入检测仪" to "hand_back.wav",
        "请同时伸出两只手背" to "hand_back.wav",
        "体温正常，请准备手部检测" to "temp_normal.wav",
        "体温异常" to "temp_abnormal.wav",
        "体温异常，请复测" to "temp_abnormal.wav",
        "手部有异物" to "hand_foreign_object.wav",
        "手部检测不合格" to "hand_fail.wav",
        "请人工复核" to "manual_review.wav",
        "晨检成功，祝您工作愉快" to "all_pass.wav"
    )

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private val context: Context = context.applicationContext
    private val promptDir: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, PROMPT_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    init {
        Timber.d("[Voice] 初始化 TTS...")
        tts = TextToSpeech(context, this)
    }

    private fun ensureInitialized() {
        if (tts != null) return
        Timber.d("[Voice] TTS 已释放，尝试重新初始化")
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            configureTtsEngine()
            isReady.set(true)
            Timber.d("[Voice] TTS 初始化成功，语言: ${Locale.getDefault().displayLanguage}")

            Timber.i("[Voice] 语音文件目录: ${promptDir.absolutePath}")
            synthesizePromptFiles()

            while (pending.isNotEmpty()) {
                speakInternal(pending.removeFirst())
            }
        } else {
            Timber.e("[Voice] TTS 初始化失败: status=$status，尝试使用系统默认引擎")
            try {
                tts?.shutdown()
                tts = TextToSpeech(context) { status2 ->
                    if (status2 == TextToSpeech.SUCCESS) {
                        configureTtsEngine()
                        isReady.set(true)
                        Timber.d("[Voice] TTS 重试初始化成功")
                        Timber.i("[Voice] 语音文件目录: ${promptDir.absolutePath}")
                        synthesizePromptFiles()
                        while (pending.isNotEmpty()) {
                            speakInternal(pending.removeFirst())
                        }
                    } else {
                        Timber.e("[Voice] TTS 重试也失败")
                    }
                }
            } catch (e: Exception) {
                Timber.e("[Voice] TTS 重试异常: ${e.message}")
            }
        }
    }

    private fun configureTtsEngine() {
        var result = tts?.setLanguage(Locale.CHINA)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = tts?.setLanguage(Locale.TRADITIONAL_CHINESE)
        }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
        }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = tts?.setLanguage(Locale.getDefault())
        }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Timber.w("[Voice] 中文不可用，使用系统默认语言")
            tts?.setLanguage(Locale.getDefault())
        }
        tts?.setSpeechRate(1.0f)
        tts?.setPitch(1.0f)
    }

    private fun synthesizePromptFiles() {
        val engine = tts ?: return
        for ((text, fileName) in promptFiles) {
            val target = File(promptDir, fileName)
            if (target.exists() && target.length() > 0L) continue

            val result = try {
                engine.synthesizeToFile(
                    text,
                    Bundle(),
                    target,
                    "synth_${UUID.randomUUID()}"
                )
            } catch (e: Exception) {
                Timber.e("[Voice] 提交语音合成异常: ${e.message}")
                TextToSpeech.ERROR
            }

            if (result == TextToSpeech.SUCCESS) {
                Timber.d("[Voice] 已提交语音合成: $text -> ${target.absolutePath}")
            } else {
                Timber.w("[Voice] 语音合成提交失败: text=$text, result=$result")
            }
        }
    }

    private fun playPromptFile(text: String): Boolean {
        val fileName = promptFiles[text] ?: return false
        val target = File(promptDir, fileName)
        if (!target.exists() || target.length() <= 0L) {
            return false
        }

        return try {
            tts?.stop()
            releaseMediaPlayer()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(target.absolutePath)
                setOnCompletionListener {
                    releaseMediaPlayer()
                }
                setOnErrorListener { _, _, _ ->
                    releaseMediaPlayer()
                    false
                }
                prepare()
                start()
            }
            Timber.d("[Voice] 播放语音文件: ${target.absolutePath}")
            true
        } catch (e: Exception) {
            Timber.e("[Voice] 播放语音文件失败: ${e.message}")
            releaseMediaPlayer()
            false
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    private fun speakInternal(text: String) {
        if (playPromptFile(text)) {
            return
        }

        val engine = tts
        if (engine == null || !isReady.get()) {
            Timber.w("[Voice] TTS 未就绪，加入队列: $text")
            pending.addLast(text)
            return
        }

        Timber.d("[Voice] TTS 播报: $text")
        
        try {
            engine.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "smartcheck_${UUID.randomUUID()}"
            )
        } catch (e: Exception) {
            Timber.e("[Voice] 播报异常: ${e.message}")
        }
    }

    override fun speak(text: String) {
        if (!enabledRef.get()) {
            Timber.d("[Voice] 语音已禁用，跳过: $text")
            return
        }
        ensureInitialized()
        val content = text.trim()
        if (content.isEmpty()) return

        speakInternal(content)
    }

    override fun speakQueue(text: String) {
        if (!enabledRef.get()) return
        ensureInitialized()
        val content = text.trim()
        if (content.isEmpty()) return

        val engine = tts
        if (engine == null || !isReady.get()) {
            pending.addLast(content)
            return
        }

        engine.speak(
            content,
            TextToSpeech.QUEUE_ADD,
            null,
            "smartcheck_${System.currentTimeMillis()}"
        )
    }

    override fun shutdown() {
        releaseMediaPlayer()
        tts?.shutdown()
        tts = null
        isReady.set(false)
        pending.clear()
    }

    override fun setEnabled(enabled: Boolean) {
        enabledRef.set(enabled)
    }

    override fun isEnabled(): Boolean = enabledRef.get()
}
