package poker.player.kotlin

import org.json.JSONObject

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

class Player {
    val numberOfPlayers = 4
    
    fun betRequest(game_state: JSONObject): Int {
        // Parse JSONObject to GameState data class
        val gameState = game_state.toGameState()
        
        val currentSmallBlind = gameState.small_blind
        val indexOfDealer = gameState.dealer
        val round = gameState.round

        val ourPlayer = getOurPlayer(gameState)
        println(ourPlayer)

        val ourIndex = ourPlayer.id
        println(ourIndex)

        if (everyPlayersBetIsZero(gameState)) {
            return gameState.small_blind
        }

        return 0
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

    fun showdown() {
    }

    fun version(): String {
        return "Kotlin Player 0.0.1"
    }
}
