package com.test.voicerecorder

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.linc.audiowaveform.AudioWaveform
import com.test.voicerecorder.voice.VoicePlayer
import com.test.voicerecorder.voice.VoiceRecorder
import com.test.voicerecorder.audiofx.AudioEnhancer
import com.test.voicerecorder.voice.TGAudio
import com.test.voicerecorder.ui.theme.VoiceRecorderTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "not granted", Toast.LENGTH_SHORT).show()
        }
    }
    private val listener = object : VoiceRecorder.Listener {
        override fun onAmplitude(amplitude: Float) {
            Log.d("MainActivity", "amplitude = $amplitude")
        }

        override fun onProgress(duration: Long) {
            Log.d("MainActivity", "onProgress = $duration")
        }

        override fun onFail() {
            Toast.makeText(this@MainActivity, "onFail", Toast.LENGTH_SHORT).show()
            voiceState.value = VoiceData(0, "failure", ShortArray(100))
        }

        override fun onSave(file: String, duration: Int, waveform: ShortArray) {
            Toast.makeText(this@MainActivity, "onSave, duration = $duration", Toast.LENGTH_SHORT)
                .show()
            voiceState.value = VoiceData(duration, file, waveform)
        }
    }

    private val voiceState = mutableStateOf(VoiceData(0, "null", ShortArray(100)))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VoiceRecorderTheme {
                AppScreen(voiceState.value, permissionLauncher, listener)
            }
        }
    }
}

@Composable
fun AppScreen(
    voiceData: VoiceData,
    permissionLauncher: ActivityResultLauncher<String>?,
    recordListener: VoiceRecorder.Listener?,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = {
                            if (recordListener != null) {
                                VoiceRecorder.record(context, recordListener)
                            }
                        }
                    ) {
                        Text(text = "Start")
                    }
                    Button(
                        onClick = { VoiceRecorder.save() } // or cancel?
                    ) {
                        Text(text = "Stop")
                    }
                    Button(
                        onClick = {
                            val audio = TGAudio(voiceData.file)
                            VoicePlayer.playAudio(audio)
                        }
                    ) {
                        Text(text = "Play")
                    }
                    Button(
                        onClick = { permissionLauncher?.launch(Manifest.permission.RECORD_AUDIO) }
                    ) {
                        Text(text = "Mic")
                    }
                }
                Spacer(modifier = Modifier.size(16.dp))
                Text(text = "File: ${voiceData.file}")
                Text(text = "Duration: ${voiceData.duration}ms")
                Spacer(modifier = Modifier.size(16.dp))
                AudioWaveform(
                    amplitudes = voiceData.waveform.map { it.toInt() },
                    spikeAnimationSpec = tween(0), // fix crash
                    progress = 0f,
                    onProgressChange = {},
                    modifier = Modifier.fillMaxWidth()
                )

                val gc = remember { mutableStateOf(true) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {
                            gc.value = !gc.value
                            AudioEnhancer.FT_USE_SYSTEM_AGC = gc.value
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = gc.value,
                        onCheckedChange = {
                            gc.value = it
                            AudioEnhancer.FT_USE_SYSTEM_AGC = it
                        }
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(text = "AutomaticGainControl")
                }

                val ns = remember { mutableStateOf(true) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {
                            ns.value = !ns.value
                            AudioEnhancer.FT_USE_SYSTEM_NS = ns.value
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = ns.value,
                        onCheckedChange = {
                            ns.value = it
                            AudioEnhancer.FT_USE_SYSTEM_NS = it
                        }
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(text = "NoiseSuppressor")
                }

                val aec = remember { mutableStateOf(true) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {
                            aec.value = !aec.value
                            AudioEnhancer.FT_USE_SYSTEM_AEC = aec.value
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = aec.value,
                        onCheckedChange = {
                            aec.value = it
                            AudioEnhancer.FT_USE_SYSTEM_AEC = it
                        }
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(text = "AcousticEchoCanceler")
                }

                Spacer(modifier = Modifier.size(16.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val audio = TGAudio("/data/data/com.test.voicerecorder/cache/record-1.ogg")
                            VoicePlayer.playAudio(audio)
                        }
                    ) {
                        Text(text = "Play 1")
                    }
                    Spacer(modifier = Modifier.size(16.dp))
                    Button(
                        onClick = {
                            val audio = TGAudio("/data/data/com.test.voicerecorder/cache/record-2.ogg")
                            VoicePlayer.playAudio(audio)
                        }
                    ) {
                        Text(text = "Play 2")
                    }
                }
            }
        }
    }
}

data class VoiceData(
    val duration: Int,
    val file: String,
    val waveform: ShortArray
)

@Preview(showBackground = true)
@Composable
fun AppScreenPreview() {
    VoiceRecorderTheme {
        AppScreen(VoiceData(0, "preview", ShortArray(100)), null, null)
    }
}