package poker.player.kotlin

import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random

data class Card(
    val rank: String,
    val suit: String
)

data class PlayerInfo(
    val id: Int,
    val name: String,
    val status: String,
    val version: String,
    val stack: Int,
    val bet: Int,
    val hole_cards: List<Card>? = null
)

data class GameState(
    val tournament_id: String,
    val game_id: String,
    val round: Int,
    val bet_index: Int,
    val small_blind: Int,
    val current_buy_in: Int,
    val pot: Int,
    val minimum_raise: Int,
    val dealer: Int,
    val orbits: Int,
    val in_action: Int,
    val players: List<PlayerInfo>,
    val community_cards: List<Card>
)

data class RankingResponse(
    val rank: Int,
    val value: Int,
    val second_value: Int,
    val kickers: List<Int>,
    val cards_used: List<Card>,
    val cards: List<Card>
)

// Extension functions for parsing JSONObject to data classes
fun JSONObject.toCard(): Card {
    return Card(
        rank = this.getString("rank"),
        suit = this.getString("suit")
    )
}

fun JSONObject.toPlayerInfo(): PlayerInfo {
    val holeCards = if (this.has("hole_cards")) {
        val holeCardsArray = this.getJSONArray("hole_cards")
        (0 until holeCardsArray.length()).map { i ->
            holeCardsArray.getJSONObject(i).toCard()
        }
    } else null

    return PlayerInfo(
        id = this.getInt("id"),
        name = this.getString("name"),
        status = this.getString("status"),
        version = this.getString("version"),
        stack = this.getInt("stack"),
        bet = this.getInt("bet"),
        hole_cards = holeCards
    )
}

fun JSONObject.toGameState(): GameState {
    val playersArray = this.getJSONArray("players")
    val players = (0 until playersArray.length()).map { i ->
        playersArray.getJSONObject(i).toPlayerInfo()
    }

    val communityCardsArray = this.getJSONArray("community_cards")
    val communityCards = (0 until communityCardsArray.length()).map { i ->
        communityCardsArray.getJSONObject(i).toCard()
    }

    return GameState(
        tournament_id = this.getString("tournament_id"),
        game_id = this.getString("game_id"),
        round = this.getInt("round"),
        bet_index = this.getInt("bet_index"),
        small_blind = this.getInt("small_blind"),
        current_buy_in = this.getInt("current_buy_in"),
        pot = this.getInt("pot"),
        minimum_raise = this.getInt("minimum_raise"),
        dealer = this.getInt("dealer"),
        orbits = this.getInt("orbits"),
        in_action = this.getInt("in_action"),
        players = players,
        community_cards = communityCards
    )
}

fun JSONObject.toRankingResponse(): RankingResponse {
    val kickersArray = this.getJSONArray("kickers")
    val kickers = (0 until kickersArray.length()).map { i ->
        kickersArray.getInt(i)
    }

    val cardsUsedArray = this.getJSONArray("cards_used")
    val cardsUsed = (0 until cardsUsedArray.length()).map { i ->
        cardsUsedArray.getJSONObject(i).toCard()
    }

    val cardsArray = this.getJSONArray("cards")
    val cards = (0 until cardsArray.length()).map { i ->
        cardsArray.getJSONObject(i).toCard()
    }

    return RankingResponse(
        rank = this.getInt("rank"),
        value = this.getInt("value"),
        second_value = this.getInt("second_value"),
        kickers = kickers,
        cards_used = cardsUsed,
        cards = cards
    )
}

class Player {
    val numberOfPlayers = 4

    fun betRequest(gameState: GameState): Int {


        val currentSmallBlind = gameState.small_blind
        val round = gameState.round

        val dealer = getDealer(gameState)

        val ourPlayer = getOurPlayer(gameState)

        val ourIndex = ourPlayer.id

        // Use ranking API to evaluate our hand strength
        val ourHoleCards = ourPlayer.hole_cards
        if (ourHoleCards != null && ourHoleCards.isNotEmpty()) {
            val ranking = getRanking(ourHoleCards, gameState.community_cards)
            // If we get a ranking response, we can use it for betting decisions
            if (ranking != null) {
                // If we get a rank >= 5, raise by big blind (add big blind to current_buy_in)
                if (ranking.rank >= 5) {
                    return gameState.current_buy_in + (gameState.small_blind * 2)
                }
                // Pre-flop: if no community cards and rank >= 1, bet current_buy_in
                if (gameState.community_cards.isEmpty() && ranking.rank >= 1) {
                    return gameState.current_buy_in
                }
                // If we get a rank >= 2, bet what the previous player bet
                if (ranking.rank >= 2) {
                    return gameState.current_buy_in
                }
            }
        }

        // 30% of the time, just place the small blind (return 0)
        if (Random.nextFloat() < 0.3f) {
            return gameState.small_blind
        }

        return 0
    }


    private fun getDealer(gameState: GameState): PlayerInfo {
        return gameState.players.find { it.id == gameState.dealer }!!
    }

    private fun getFirstPlayer(gameState: GameState): PlayerInfo {
        return gameState.players.find { it.id == (gameState.dealer + 1) % (gameState.players.size) }!!
    }

    // Legacy method for JSON compatibility
    fun betRequest(game_state: JSONObject): Int {
        return betRequest(game_state.toGameState())
    }

    private fun everyPlayersBetIsZero(gameState: GameState): Boolean {
        val activePlayers = gameState.players.filter { player ->
            player.bet != 0 && player.status.equals("active", true)
        }

        return activePlayers.isEmpty()
    }

    private fun getOurPlayer(gameState: GameState): PlayerInfo {
        val ourPlayer = gameState.players.find { player ->
            player.name.equals("the donkey killers", true)
        }
        return ourPlayer ?: throw IllegalStateException("Our player not found in game state")
    }

    private fun hasKingAce(player: PlayerInfo): Boolean {
        val holeCards = player.hole_cards ?: return false

        if (holeCards.size != 2) return false

        val ranks = holeCards.map { it.rank }.toSet()
        return ranks.contains("K") && ranks.contains("A")
    }

    private fun getRanking(holeCards: List<Card>, communityCards: List<Card>): RankingResponse? {
        return try {
            val allCards = holeCards + communityCards
            val cardsJson = JSONArray()

            allCards.forEach { card ->
                val cardJson = JSONObject()
                cardJson.put("rank", card.rank)
                cardJson.put("suit", card.suit)
                cardsJson.put(cardJson)
            }

            val url = URL("https://rainman.leanpoker.org/rank")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true

            val postData = "cards=$cardsJson"
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(postData)
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val responseJson = JSONObject(response)
                responseJson.toRankingResponse()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun showdown() {
    }

    fun version(): String {
        return "Kotlin Player 0.0.1"
    }
}
