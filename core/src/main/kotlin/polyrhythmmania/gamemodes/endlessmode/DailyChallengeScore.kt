package polyrhythmmania.gamemodes.endlessmode

import com.badlogic.gdx.Gdx
import com.eclipsesource.json.Json
import com.eclipsesource.json.JsonObject
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URIBuilder
import org.apache.http.util.EntityUtils
import paintbox.Paintbox
import paintbox.binding.Var
import polyrhythmmania.PRMania
import polyrhythmmania.PRManiaGame
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.thread


data class DailyChallengeScore(val date: LocalDate, val score: Int) {
    companion object {
        val ZERO: DailyChallengeScore = DailyChallengeScore(LocalDate.MIN, 0)
    }
}

data class EndlessHighScore(val seed: UInt, val score: Int) {
    companion object {
        val ZERO: EndlessHighScore = EndlessHighScore(0u, -1)
    }
}

data class DailyLeaderboardScore(val countryCode: String, val score: Int, val name: String, val gameVersion: String?, val patternsVersion: Int)

typealias DailyLeaderboard = Map<LocalDate, List<DailyLeaderboardScore>>

object DailyChallengeUtils {
    
    private const val ROOT_URL: String = "https://api.rhre.dev/prmania"
    const val MIN_SCORE_TO_UNLOCK: Int = 10 // Daily Challenge is initially locked until a score of 10 in Endless Mode
    
    val allowedNameChars: Set<Char> = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_".toSet()

    fun sendNonceRequestSync(date: LocalDate): UUID? {
        val post = HttpPost(
                URIBuilder("${ROOT_URL}/dailychallenge/start/${date.format(DateTimeFormatter.ISO_DATE)}")
                        .setParameter("v", PRMania.VERSION.toString())
                        .setParameter("pv", EndlessPatterns.ENDLESS_PATTERNS_VERSION.toString())
                        .build()
        )
        
        try {
            val httpClient = PRManiaGame.instance.httpClient
            httpClient.execute(post).use { response ->
                val status = response.statusLine.statusCode
                if (status == 200) {
                    val content = EntityUtils.toString(response.entity)
                    try {
                        val uuid: UUID = UUID.fromString(content.trim())
                        return uuid
                    } catch (e: Exception) {
                        Paintbox.LOGGER.warn("Failed to get daily challenge high score nonce from server: bad uuid $content")
                        e.printStackTrace()
                    }
                } else {
                    Paintbox.LOGGER.warn("Failed to get daily challenge high score nonce from server: status was $status for url ${post.uri} ${EntityUtils.toString(response.entity)}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return null
    }
    
    fun submitHighScore(date: LocalDate, score: Int, name: String, nonce: UUID,
                        noCountry: Boolean) {
        thread(isDaemon = true, name = "Daily Challenge high score submission", start = true) {
            val uriBuilder = URIBuilder("${ROOT_URL}/dailychallenge/submit/${date.format(DateTimeFormatter.ISO_DATE)}")
                    .setParameter("v", PRMania.VERSION.toString())
                    .setParameter("pv", EndlessPatterns.ENDLESS_PATTERNS_VERSION.toString())
                    .setParameter("uuid", nonce.toString())
                    .setParameter("score", score.toString())
                    .setParameter("name", name)
            if (noCountry) {
                uriBuilder.setParameter("nocountry", "1")
            }
            
            val post = HttpPost(uriBuilder.build())
            try {
                val httpClient = PRManiaGame.instance.httpClient
                httpClient.execute(post).use { response ->
                    val status = response.statusLine.statusCode
                    if (status != 204) {
                        Paintbox.LOGGER.warn("Failed to post daily challenge high score: status was $status for url ${post.uri} ${EntityUtils.toString(response.entity)}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getLeaderboardPastWeek(listVar: Var<DailyLeaderboard?>, fetching: Var<Boolean>) {
        thread(isDaemon = true, name = "Daily Challenge leaderboard GET", start = true) {
            Gdx.app.postRunnable { 
                fetching.set(true)
            }
            
            val uriBuilder = URIBuilder("${ROOT_URL}/dailychallenge/top_past_week")
                    .setParameter("v", PRMania.VERSION.toString())
                    .setParameter("pv", EndlessPatterns.ENDLESS_PATTERNS_VERSION.toString())

            val get = HttpGet(uriBuilder.build())
            var returnValue: DailyLeaderboard? = null
            try {
                val httpClient = PRManiaGame.instance.httpClient
                httpClient.execute(get).use { response ->
                    val status = response.statusLine.statusCode
                    if (status != 200) {
                        Paintbox.LOGGER.warn("Failed to get daily challenge leaderboard: status was $status for url ${get.uri} ${EntityUtils.toString(response.entity)}")
                    } else {
                        val content = EntityUtils.toString(response.entity)
                        val jsonObject = Json.parse(content).asObject()
                        // key/value pairs of ISO date, score objects
                        val map = mutableMapOf<LocalDate, List<DailyLeaderboardScore>>()
                        jsonObject.forEach { member ->
                            val date = LocalDate.parse(member.name, DateTimeFormatter.ISO_LOCAL_DATE)
                            val list = mutableListOf<DailyLeaderboardScore>()
                            member.value.asArray().forEach { v ->
                                v as JsonObject
                                val countryCode = v["countryCode"]
                                list += DailyLeaderboardScore(countryCode?.takeIf { it.isString }?.asString() ?: "unknown", 
                                        v.getInt("score", 0), v.getString("name", ""), v.get("gameVersion")?.asString(),
                                        v.getInt("patternsVersion", 1))
                            }
                            
                            // Merge
                            map[date] = map.getOrElse(date) { emptyList() } + list
                        }
                        
                        returnValue = map
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                returnValue = null
            }
            
            Gdx.app.postRunnable { 
                fetching.set(false)
                listVar.set(returnValue)
            }
        }
    }
    
    fun mapPlaceNumbers(sortedScoresDescending: List<DailyLeaderboardScore>): Map<DailyLeaderboardScore, Int> {
        val placeNumbersInverse: MutableMap<DailyLeaderboardScore, Int> = sortedScoresDescending.associateWith { 0 }.toMutableMap()

        var placeNumber = 0
        var placeValue = -1
        sortedScoresDescending.asReversed().forEachIndexed { i, score ->
            if (score.score != placeValue) {
                placeValue = score.score
                placeNumber = i + 1
            }
            placeNumbersInverse[score] = placeNumber
        }
        
        return placeNumbersInverse.mapValues { (_, pl) ->
            sortedScoresDescending.size - pl + 1
        }
    }
}
