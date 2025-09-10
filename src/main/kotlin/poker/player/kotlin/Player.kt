package poker.player.kotlin

import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

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

data class GameLog(
    val gameId: String,
    val tournamentId: String,
    val timestamp: Long,
    val rounds: List<JSONObject>,
    val finalResult: JSONObject?
)

data class GameAnalysis(
    val averageAggressiveness: Double,
    val winRate: Double,
    val foldRate: Double,
    val bluffFrequency: Double,
    val averageBetSize: Double
)

enum class PokerPhase {
    PRE_FLOP,
    FLOP,
    TURN,
    RIVER
}

class Player {
    val numberOfPlayers = 4

    // Game log storage - keep the last 10 games in memory
    private val gameLogStorage = mutableListOf<GameLog>()
    private val maxStoredGames = 10

    // Current game analysis based on stored logs
    private var currentAnalysis: GameAnalysis? = null

    // Game ID history - keep track of last 10 gameIds for fetching logs
    private val gameIdHistory = mutableListOf<Pair<String, String>>() // Pairs of (tournamentId, gameId)
    private val maxGameHistory = 10

    // Current game and tournament IDs for dynamic log fetching
    private var currentTournamentId: String? = null
    private var currentGameId: String? = null

    fun betRequest(gameState: GameState): Int {
        val ourPlayer = getOurPlayer(gameState)

        // Store current tournament and game IDs for dynamic log fetching
        currentTournamentId = gameState.tournament_id
        currentGameId = gameState.game_id

        // Add current game to history if it's new
        val currentGamePair = Pair(gameState.tournament_id, gameState.game_id)
        if (!gameIdHistory.contains(currentGamePair)) {
            gameIdHistory.add(currentGamePair)
            // Keep only the last 10 games
            while (gameIdHistory.size > maxGameHistory) {
                gameIdHistory.removeAt(0)
            }
        }

        // Update game logs periodically (every few rounds to avoid excessive API calls)
        if (gameState.round % 5 == 0) {
            updateGameLogs()
        }

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
        val ourHoleCards = ourPlayer.hole_cards ?: return fold(gameState)
        
        // Premium hands (AA, KK, QQ, AK) - raise aggressively
        if (isPremiumHand(ourHoleCards)) {
            return raise(gameState, gameState.small_blind * 3)
        }
        
        // Strong hands (JJ, 10-10, AQ, AJ, KQ) - moderate raise
        if (isStrongHand(ourHoleCards)) {
            return raise(gameState, gameState.small_blind * 2)
        }
        
        // Playable hands (suited connectors, medium pairs, suited aces)
        if (isPlayableHand(ourHoleCards)) {
            // If no raise before us, limp in
            if (gameState.current_buy_in <= gameState.small_blind * 2) {
                return stayInTheGame(gameState)
            }
            // If there's a reasonable raise, call
            if (gameState.current_buy_in <= gameState.small_blind * 4) {
                return stayInTheGame(gameState)
            }
        }
        
        // Everything else - fold to save chips for better spots
        return fold(gameState)
    }


    private fun evaluateFlop(gameState: GameState, ourPlayer: PlayerInfo): Int {
        val ourHoleCards = ourPlayer.hole_cards ?: return fold(gameState)
        
        // Use ranking API to evaluate our hand strength
        val ranking = getRanking(ourHoleCards, gameState.community_cards)
        if (ranking != null) {
            // Very strong hands (top pair or better) - bet for value
            if (ranking.rank >= 6) {
                return raise(gameState, gameState.small_blind * 2)
            }
            
            // Good hands (pair, good draws) - bet or call
            if (ranking.rank >= 4) {
                if (gameState.current_buy_in <= gameState.small_blind * 3) {
                    return stayInTheGame(gameState)
                }
            }
            
            // Marginal hands - only continue if cheap
            if (ranking.rank >= 2) {
                if (gameState.current_buy_in <= gameState.small_blind * 2) {
                    return stayInTheGame(gameState)
                }
            }
        }
        
        // Check for drawing hands
        if (hasOpenEndedStraightDraw(ourHoleCards, gameState.community_cards)) {
            // Stay in with draws if price is reasonable
            if (gameState.current_buy_in <= gameState.small_blind * 3) {
                return stayInTheGame(gameState)
            }
        }
        
        // Fold weak hands or if betting is too expensive
        return fold(gameState)
    }

    private fun allIn(gameState: GameState): Int {
        return getOurPlayer(gameState).stack
    }


    private fun evaluateTurn(gameState: GameState, ourPlayer: PlayerInfo): Int {
        val ourHoleCards = ourPlayer.hole_cards ?: return fold(gameState)
        
        // Use ranking API to evaluate our hand strength
        val ranking = getRanking(ourHoleCards, gameState.community_cards)
        if (ranking != null) {
            // Very strong hands - bet aggressively for value
            if (ranking.rank >= 7) {
                return raise(gameState, gameState.small_blind * 3)
            }
            
            // Strong hands - moderate betting
            if (ranking.rank >= 5) {
                if (gameState.current_buy_in <= gameState.small_blind * 4) {
                    return raise(gameState, gameState.small_blind * 2)
                } else {
                    return stayInTheGame(gameState)
                }
            }
            
            // Good hands - call reasonable bets
            if (ranking.rank >= 3) {
                if (gameState.current_buy_in <= gameState.small_blind * 3) {
                    return stayInTheGame(gameState)
                }
            }
        }
        
        // Check for drawing hands - more selective on turn
        if (hasOpenEndedStraightDraw(ourHoleCards, gameState.community_cards)) {
            // Only continue with draws if very cheap (better pot odds needed)
            if (gameState.current_buy_in <= gameState.small_blind * 2) {
                return stayInTheGame(gameState)
            }
        }
        
        // Fold weak hands or expensive draws
        return fold(gameState)
    }

    private fun evaluateRiver(gameState: GameState, ourPlayer: PlayerInfo): Int {
        val ourHoleCards = ourPlayer.hole_cards ?: return fold(gameState)
        
        // Check for completed straight - this is a GOOD hand, bet for value!
        if (hasStraight(ourHoleCards, gameState.community_cards)) {
            return raise(gameState, gameState.small_blind * 4)
        }
        
        // Use ranking API to evaluate our hand strength
        val ranking = getRanking(ourHoleCards, gameState.community_cards)
        if (ranking != null) {
            // Premium hands - bet big for value
            if (ranking.rank >= 8) {
                return raise(gameState, gameState.small_blind * 4)
            }
            
            // Very strong hands - bet for value
            if (ranking.rank >= 6) {
                return raise(gameState, gameState.small_blind * 2)
            }
            
            // Good hands - call or small bet
            if (ranking.rank >= 4) {
                if (gameState.current_buy_in <= gameState.small_blind * 3) {
                    return stayInTheGame(gameState)
                }
            }
            
            // Marginal hands - only call small bets
            if (ranking.rank >= 2) {
                if (gameState.current_buy_in <= gameState.small_blind) {
                    return stayInTheGame(gameState)
                }
            }
        }
        
        // Weak hands - fold to preserve chips
        return fold(gameState)
    }


    private fun fold(gameState: GameState): Int {
//        // If there's no bet (current_buy_in is 0), check (return 0)
//        if (gameState.current_buy_in == 0) {
//            return 0
//        }
//
//        // If there's an outstanding bet, call (return current_buy_in)
//        return gameState.current_buy_in

        return 0

    }


    private fun stayInTheGame(gameState: GameState): Int {
        // If there's no bet (current_buy_in is 0), check (return 0)
        if (gameState.current_buy_in == 0) {
            return 0
        }

        // If there's an outstanding bet, call (return current_buy_in)
        return gameState.current_buy_in

    }


    private fun raiseByBigBlind(gameState: GameState): Int {
        return gameState.current_buy_in + gameState.small_blind
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

    private fun isPremiumHand(holeCards: List<Card>): Boolean {
        if (holeCards.size != 2) return false
        
        val ranks = holeCards.map { it.rank }.sorted()
        val rankValues = ranks.map { getRankValue(it) }.sorted()
        
        // Pocket pairs: AA, KK, QQ
        if (ranks[0] == ranks[1]) {
            return ranks[0] in listOf("A", "K", "Q")
        }
        
        // AK (suited or unsuited)
        return rankValues == listOf(13, 14)
    }

    private fun isStrongHand(holeCards: List<Card>): Boolean {
        if (holeCards.size != 2) return false
        
        val ranks = holeCards.map { it.rank }.sorted()
        val rankValues = ranks.map { getRankValue(it) }.sorted()
        val suits = holeCards.map { it.suit }
        val suited = suits[0] == suits[1]
        
        // Pocket pairs: JJ, 10-10
        if (ranks[0] == ranks[1]) {
            return ranks[0] in listOf("J", "10")
        }
        
        // High card combinations: AQ, AJ, KQ
        if (rankValues == listOf(12, 14)) return true // AQ
        if (rankValues == listOf(11, 14)) return true // AJ
        if (rankValues == listOf(12, 13)) return true // KQ
        
        return false
    }

    private fun isPlayableHand(holeCards: List<Card>): Boolean {
        if (holeCards.size != 2) return false
        
        val ranks = holeCards.map { it.rank }.sorted()
        val rankValues = ranks.map { getRankValue(it) }.sorted()
        val suits = holeCards.map { it.suit }
        val suited = suits[0] == suits[1]
        
        // Medium pocket pairs (99 down to 66)
        if (ranks[0] == ranks[1]) {
            val pairValue = getRankValue(ranks[0])
            return pairValue in 6..9
        }
        
        // Suited aces (A2s - A9s)
        if (suited && rankValues[1] == 14 && rankValues[0] in 2..9) {
            return true
        }
        
        // Suited connectors (78s and higher)
        if (suited && rankValues[1] - rankValues[0] == 1 && rankValues[0] >= 7) {
            return true
        }
        
        // Suited one-gappers (79s, 8Ts, 9Js)
        if (suited && rankValues[1] - rankValues[0] == 2 && rankValues[0] >= 7) {
            return true
        }
        
        return false
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

    private fun updateGameLogs() {
        try {
            val newLogs = fetchGameLogs()
            gameLogStorage.addAll(newLogs)
            
            // Keep only the most recent logs
            while (gameLogStorage.size > maxStoredGames) {
                gameLogStorage.removeAt(0)
            }
        } catch (e: Exception) {
            println("Error updating game logs: ${e.message}")
        }
    }

    private fun fetchGameLogs(): List<GameLog> {
        val allGameLogs = mutableListOf<GameLog>()

        // Iterate through all games in history and fetch logs for each
        gameIdHistory.forEach { (tournamentId, gameId) ->
            try {
                val url = URL("https://live.leanpoker.org/api/tournament/$tournamentId/game/$gameId/log")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Handle gzip compression
                    val inputStream = if (connection.contentEncoding == "gzip") {
                        GZIPInputStream(connection.inputStream)
                    } else {
                        connection.inputStream
                    }
                    
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val response = reader.readText()
                    reader.close()

                    val responseJson = JSONObject(response)
                    val gameLogs = parseGameLogsFromResponse(responseJson, tournamentId, gameId)
                    allGameLogs.addAll(gameLogs)
                }
            } catch (e: Exception) {
                println("Error fetching game logs for game $gameId: ${e.message}")
            }
        }

        return allGameLogs
    }

    private fun parseGameLogsFromResponse(response: JSONObject, tournamentId: String, gameId: String): List<GameLog> {
        val gameLogs = mutableListOf<GameLog>()
        try {
            // Use the provided tournament and game IDs instead of parsing from response
            val timestamp = System.currentTimeMillis()

            // Extract rounds if they exist
            val rounds = mutableListOf<JSONObject>()
            if (response.has("rounds")) {
                val roundsArray = response.getJSONArray("rounds")
                for (i in 0 until roundsArray.length()) {
                    rounds.add(roundsArray.getJSONObject(i))
                }
            }

            // Extract final result if it exists
            val finalResult = if (response.has("result")) response.getJSONObject("result") else null

            val gameLog = GameLog(gameId, tournamentId, timestamp, rounds, finalResult)
            gameLogs.add(gameLog)

        } catch (e: Exception) {
            println("Error parsing game logs from response: ${e.message}")
        }
        return gameLogs
    }

    fun showdown() {
    }

    fun version(): String {
        return "don't know what is happening as of now"
    }
}
