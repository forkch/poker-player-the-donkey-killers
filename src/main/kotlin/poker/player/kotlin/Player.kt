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

enum class PokerPhase {
    PRE_FLOP,
    FLOP,
    TURN,
    RIVER
}

class Player {
    val numberOfPlayers = 4

    fun betRequest(gameState: GameState): Int {
        val ourPlayer = getOurPlayer(gameState)

        // Determine poker phase based on community cards
        return when (getPokerPhase(gameState.community_cards)) {
            PokerPhase.PRE_FLOP -> evaluatePreFlop(gameState, ourPlayer)
            PokerPhase.FLOP -> evaluateFlop(gameState, ourPlayer)
            PokerPhase.TURN -> evaluateTurn(gameState, ourPlayer)
            PokerPhase.RIVER -> evaluateRiver(gameState, ourPlayer)
        }
    }

    private fun getPokerPhase(communityCards: List<Card>): PokerPhase {
        return when (communityCards.size) {
            0 -> PokerPhase.PRE_FLOP
            3 -> PokerPhase.FLOP
            4 -> PokerPhase.TURN
            5 -> PokerPhase.RIVER
            else -> PokerPhase.PRE_FLOP // Default to pre-flop for unexpected cases
        }
    }

    private fun evaluatePreFlop(gameState: GameState, ourPlayer: PlayerInfo): Int {

        // Use ranking API to evaluate our hand strength
        val ourHoleCards = ourPlayer.hole_cards
        if (ourHoleCards != null && ourHoleCards.isNotEmpty()) {
            val ranking = getRanking(ourHoleCards, gameState.community_cards)
            if (ranking != null) {
                // Pre-flop: if rank >= 5, raise by big blind
                if (ranking.rank >= 5) {
                    return gameState.current_buy_in + (gameState.small_blind * 2)
                }
                // Pre-flop: if rank >= 1, bet current_buy_in
                if (ranking.rank >= 1) {
                    return stayInTheGame(gameState)
                }
            }
        }

        val probability = Random.nextFloat()
        if (probability < 0.1f) {
            return gameState.small_blind * 2 * 2
        }

        return stayInTheGame(gameState)
    }


    private fun evaluateFlop(gameState: GameState, ourPlayer: PlayerInfo): Int {
        // Check for open-ended straight draw - stay in the hand
        val ourHoleCards = ourPlayer.hole_cards
        if (ourHoleCards != null && ourHoleCards.isNotEmpty()) {
            if (hasOpenEndedStraightDraw(ourHoleCards, gameState.community_cards)) {
                return stayInTheGame(gameState)
            }
        }

        // Use ranking API to evaluate our hand strength
        if (ourHoleCards != null && ourHoleCards.isNotEmpty()) {
            val ranking = getRanking(ourHoleCards, gameState.community_cards)
            if (ranking != null) {
                // Flop/Turn: if rank < 2, fold
                if (ranking.rank < 2) {
                    return stayInTheGame(gameState)
                }
                // Flop/Turn: if rank >= 5, raise by big blind
                if (ranking.rank >= 5) {
                    return gameState.current_buy_in + (gameState.small_blind * 2)
                }
                // Flop/Turn: if rank >= 3, bet current_buy_in (more conservative than pre-flop)
                if (ranking.rank >= 3) {
                    return stayInTheGame(gameState)
                }
            }
        }

        // 20% of the time, bet small blind (less aggressive than pre-flop)
        if (Random.nextFloat() < 0.2f) {
            return gameState.small_blind * 2 * 2
        }

        return stayInTheGame(gameState)
    }


    private fun evaluateTurn(gameState: GameState, ourPlayer: PlayerInfo): Int {
        // Check for open-ended straight draw - stay in the hand
        val ourHoleCards = ourPlayer.hole_cards
        if (ourHoleCards != null && ourHoleCards.isNotEmpty()) {
            if (hasOpenEndedStraightDraw(ourHoleCards, gameState.community_cards)) {
                return stayInTheGame(gameState)
            }
        }

        // Use ranking API to evaluate our hand strength
        if (ourHoleCards != null && ourHoleCards.isNotEmpty()) {
            val ranking = getRanking(ourHoleCards, gameState.community_cards)
            if (ranking != null) {
                // Flop/Turn: if rank < 2, fold
                if (ranking.rank < 2) {
                    return stayInTheGame(gameState)
                }
                // Flop/Turn: if rank >= 5, raise by big blind
                if (ranking.rank >= 5) {
                    return gameState.current_buy_in + (gameState.small_blind * 2)
                }
                // Flop/Turn: if rank >= 3, bet current_buy_in (more conservative than pre-flop)
                if (ranking.rank >= 3) {
                    return stayInTheGame(gameState)
                }
            }
        }

        // 20% of the time, bet small blind (less aggressive than pre-flop)
        if (Random.nextFloat() < 0.2f) {
            return gameState.small_blind * 2 * 2
        }

        return stayInTheGame(gameState)
    }

    private fun evaluateRiver(gameState: GameState, ourPlayer: PlayerInfo): Int {
        // Check for completed straight - fold if we have one
        val ourHoleCards = ourPlayer.hole_cards
        if (ourHoleCards != null && ourHoleCards.isNotEmpty()) {
            if (hasStraight(ourHoleCards, gameState.community_cards)) {
                return raise(gameState, gameState.small_blind * 2)
            }
        }

        // Use ranking API to evaluate our hand strength
        if (ourHoleCards != null && ourHoleCards.isNotEmpty()) {
            val ranking = getRanking(ourHoleCards, gameState.community_cards)
            if (ranking != null) {
                // River: if rank < 2, fold
                if (ranking.rank < 2) {
                    return stayInTheGame(gameState)
                }
                // River: if rank >= 6, raise by big blind (most conservative)
                if (ranking.rank >= 6) {
                    return gameState.current_buy_in + (gameState.small_blind * 2)
                }
                // River: if rank >= 4, bet current_buy_in
                if (ranking.rank >= 4) {
                    return stayInTheGame(gameState)
                }
            }
        }

        // 10% of the time, bet small blind (least aggressive)
        if (Random.nextFloat() < 0.1f) {
            return gameState.small_blind
        }

        return stayInTheGame(gameState)
    }

    private fun stayInTheGame(gameState: GameState): Int {
        // If there's no bet (current_buy_in is 0), check (return 0)
        if (gameState.current_buy_in == 0) {
            return 0
        }

        // If there's an outstanding bet, call (return current_buy_in)
        return gameState.current_buy_in

    }


    private fun raise(gameState: GameState, raiseAmount: Int): Int {
        return gameState.current_buy_in + raiseAmount
    }


    private fun getDealer(gameState: GameState): PlayerInfo {
        return gameState.players.find { it.id == gameState.dealer }!!
    }

    private fun getFirstPlayer(gameState: GameState): PlayerInfo {
        return gameState.players.find { it.id == (gameState.dealer + 1) % (gameState.players.size) }!!
    }

    private fun isSmallBlind(gameState: GameState, player: PlayerInfo): Boolean {
        // Small blind is the first player after the dealer
        val smallBlindPosition = (gameState.dealer + 1) % gameState.players.size
        return player.id == smallBlindPosition
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

    private fun hasSuitedKingOrAce(player: PlayerInfo): Boolean {
        val holeCards = player.hole_cards ?: return false

        if (holeCards.size != 2) return false

        // Check if cards are suited (same suit)
        val suits = holeCards.map { it.suit }.toSet()
        if (suits.size != 1) return false

        // Check if at least one card is King or Ace
        val ranks = holeCards.map { it.rank }.toSet()
        return ranks.contains("K") || ranks.contains("A")
    }

    private fun hasOpenEndedStraightDraw(holeCards: List<Card>, communityCards: List<Card>): Boolean {
        val allCards = holeCards + communityCards
        val rankValues = allCards.map { getRankValue(it.rank) }.sorted().distinct()

        // Check for open-ended straight draws (need 4 consecutive cards with gaps at both ends)
        for (i in 0..rankValues.size - 4) {
            val consecutive = mutableListOf<Int>()
            for (j in i until rankValues.size) {
                if (consecutive.isEmpty() || rankValues[j] == consecutive.last() + 1) {
                    consecutive.add(rankValues[j])
                } else {
                    break
                }
            }

            // Open-ended straight draw: exactly 4 consecutive cards where we can complete on both ends
            if (consecutive.size == 4) {
                val lowEnd = consecutive.first()
                val highEnd = consecutive.last()

                // Check if we can complete on both ends (not at the extremes A-2-3-4 or J-Q-K-A)
                if (lowEnd > 1 && highEnd < 14) {
                    return true
                }
            }
        }

        // Special case for A-2-3-4 (wheel straight draw)
        val wheelCards = rankValues.filter { it == 1 || it in 2..4 }
        if (wheelCards.size == 4 && wheelCards.contains(1) && wheelCards.contains(2) &&
            wheelCards.contains(3) && wheelCards.contains(4)
        ) {
            return true
        }

        return false
    }

    private fun hasStraight(holeCards: List<Card>, communityCards: List<Card>): Boolean {
        val allCards = holeCards + communityCards
        val rankValues = allCards.map { getRankValue(it.rank) }.sorted().distinct()

        // Check for 5 consecutive cards
        for (i in 0..rankValues.size - 5) {
            val consecutive = mutableListOf<Int>()
            for (j in i until rankValues.size) {
                if (consecutive.isEmpty() || rankValues[j] == consecutive.last() + 1) {
                    consecutive.add(rankValues[j])
                } else {
                    break
                }
            }

            if (consecutive.size >= 5) {
                return true
            }
        }

        // Special case for A-2-3-4-5 (wheel straight)
        val wheelCards = rankValues.filter { it == 1 || it in 2..5 }
        if (wheelCards.size >= 5 && wheelCards.contains(1) && wheelCards.contains(2) &&
            wheelCards.contains(3) && wheelCards.contains(4) && wheelCards.contains(5)
        ) {
            return true
        }

        return false
    }

    private fun getRankValue(rank: String): Int {
        return when (rank) {
            "2" -> 2
            "3" -> 3
            "4" -> 4
            "5" -> 5
            "6" -> 6
            "7" -> 7
            "8" -> 8
            "9" -> 9
            "10" -> 10
            "J" -> 11
            "Q" -> 12
            "K" -> 13
            "A" -> 14  // Ace high, but handled specially for wheel straights
            else -> 0
        }
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
        return "higher bets for suited cards depending on blind position (reverted stay in the game)"
    }
}
