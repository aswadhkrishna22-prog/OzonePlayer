/*
 * Ozone Player (2026)
 * Audio Signal Path Telemetry Dialog
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.dsp.PipelineTelemetry
import moe.rukamori.archivetune.ui.component.DefaultDialog

@Composable
fun SignalPathDialog(
    onDismiss: () -> Unit,
) {
    val snapshot by PipelineTelemetry.snapshot.collectAsState()

    DefaultDialog(
        onDismiss = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.waves),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Audio Signal Path",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Stage 1: Source
            SignalNode(
                stageName = "Source Stream",
                title = snapshot.source.codec,
                subtitle = "${snapshot.source.sampleRate} Hz • ${snapshot.source.bitDepth}" +
                    if (snapshot.source.bitrateKbps > 0) " • ${snapshot.source.bitrateKbps} kbps" else "",
                iconRes = R.drawable.music_note,
                accentColor = Color(0xFF00E676),
            )

            RailConnector()

            // Stage 2: Decoder
            SignalNode(
                stageName = "Decoder",
                title = "Media3 ExoPlayer Core",
                subtitle = "Hardware/Software MediaCodec",
                iconRes = R.drawable.settings,
                accentColor = Color(0xFF29B6F6),
            )

            RailConnector()

            // Stage 3: DSP
            val dspDetails = buildList {
                if (snapshot.dsp.autoEqEnabled) {
                    add("AutoEq: ${snapshot.dsp.autoEqModel ?: "Active"} (${snapshot.dsp.autoEqFilterCount} bands)")
                } else {
                    add("AutoEq: Bypassed")
                }
                if (snapshot.dsp.spatialEnabled) {
                    add("Spatial Audio: Active")
                }
                if (snapshot.dsp.enhanceActive) {
                    add("Enhance: Active")
                } else if (snapshot.dsp.enhanceEnabled) {
                    add("Enhance: Standby (Auto)")
                }
                if (snapshot.dsp.dualPlayEnabled) {
                    add("Dual Play: ${snapshot.dsp.dualPlayEar ?: "Deck A"}")
                }
            }.joinToString(" • ")

            SignalNode(
                stageName = "Audio DSP & Equalizer",
                title = if (snapshot.dsp.autoEqEnabled) "Acoustic Correction Active" else "Direct Passthrough",
                subtitle = dspDetails,
                iconRes = R.drawable.equalizer,
                accentColor = if (snapshot.dsp.autoEqEnabled) Color(0xFFFF9100) else Color(0xFFB0BEC5),
            )

            RailConnector()

            // Stage 4: Mixer / Sink
            SignalNode(
                stageName = "AudioTrack Mixer",
                title = "${snapshot.sink.sampleRate} Hz • ${snapshot.sink.bitDepth}",
                subtitle = "${snapshot.sink.channelCount} Channels Stereo",
                iconRes = R.drawable.volume_up,
                accentColor = Color(0xFFAB47BC),
            )

            RailConnector()

            // Stage 5: Output Device
            SignalNode(
                stageName = "Output Device",
                title = snapshot.sink.deviceName,
                subtitle = "${snapshot.sink.deviceType}" +
                    if (snapshot.sink.isBitPerfect) " • Bit-Perfect Direct" else "",
                iconRes = if (snapshot.sink.isBluetooth) R.drawable.bluetooth else R.drawable.headphones,
                accentColor = if (snapshot.sink.isBitPerfect) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SignalNode(
    stageName: String,
    title: String,
    subtitle: String,
    iconRes: Int,
    accentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stageName.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RailConnector() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 29.dp)
                .width(2.dp)
                .height(14.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}
