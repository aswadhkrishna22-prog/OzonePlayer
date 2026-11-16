/*
 * Ozone Player (2026)
 * Return YouTube Dislike Client
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import moe.rukamori.archivetune.innertube.models.ReturnYouTubeDislikeResponse

object DislikeClient {
    private const val VOTES_URL = "https://returnyoutubedislikeapi.com/Votes"

    suspend fun getDislikes(
        videoId: String,
    ): Result<ReturnYouTubeDislikeResponse> = runCatching {
        YouTube.innerTube.client.get("$VOTES_URL?videoId=$videoId") {
            contentType(ContentType.Application.Json)
        }.body<ReturnYouTubeDislikeResponse>()
    }
}
