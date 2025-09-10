package poker.player.kotlin

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class PlayerTest {
    
    private val player = Player()
    
    @Test
    fun `test betRequest returns small blind when all players bet is zero`() {
        val gameStateJson = createGameStateJson(
            smallBlind = 10,
            players = listOf(
                createPlayerJson(id = 0, name = "Player 1", bet = 0, status = "active"),
                createPlayerJson(id = 1, name = "the donkey killers", bet = 0, status = "active"),
                createPlayerJson(id = 2, name = "Player 3", bet = 0, status = "active")
            )
        )
        
        val result = player.betRequest(gameStateJson)
        assertEquals(10, result)
    }
    
    @Test
    fun `test betRequest returns 0 when some players have bets`() {
        val gameStateJson = createGameStateJson(
            smallBlind = 10,
            players = listOf(
                createPlayerJson(id = 0, name = "Player 1", bet = 20, status = "active"),
                createPlayerJson(id = 1, name = "the donkey killers", bet = 0, status = "active"),
                createPlayerJson(id = 2, name = "Player 3", bet = 0, status = "active")
            )
        )
        
        val result = player.betRequest(gameStateJson)
        assertEquals(0, result)
    }
    
    @Test
    fun `test betRequest throws exception when our player not found`() {
        val gameStateJson = createGameStateJson(
            smallBlind = 10,
            players = listOf(
                createPlayerJson(id = 0, name = "Player 1", bet = 0, status = "active"),
                createPlayerJson(id = 2, name = "Player 3", bet = 0, status = "active")
            )
        )
        
        assertThrows<IllegalStateException> {
            player.betRequest(gameStateJson)
        }
    }
    
    @Test
    fun `test version returns correct string`() {
        val result = player.version()
        assertEquals("Kotlin Player 0.0.1", result)
    }
    
    @Test
    fun `test JSONObject toCard extension function`() {
        val cardJson = JSONObject()
        cardJson.put("rank", "A")
        cardJson.put("suit", "spades")
        
        val card = cardJson.toCard()
        assertEquals("A", card.rank)
        assertEquals("spades", card.suit)
    }
    
    @Test
    fun `test JSONObject toPlayerInfo extension function without hole cards`() {
        val playerJson = createPlayerJson(id = 1, name = "Test Player", bet = 100, status = "active")
        
        val playerInfo = playerJson.toPlayerInfo()
        assertEquals(1, playerInfo.id)
        assertEquals("Test Player", playerInfo.name)
        assertEquals("active", playerInfo.status)
        assertEquals("1.0", playerInfo.version)
        assertEquals(1000, playerInfo.stack)
        assertEquals(100, playerInfo.bet)
        assertNull(playerInfo.hole_cards)
    }
    
    @Test
    fun `test JSONObject toPlayerInfo extension function with hole cards`() {
        val playerJson = createPlayerJson(id = 1, name = "Test Player", bet = 100, status = "active")
        
        val holeCards = JSONArray()
        val card1 = JSONObject()
        card1.put("rank", "A")
        card1.put("suit", "hearts")
        val card2 = JSONObject() 
        card2.put("rank", "K")
        card2.put("suit", "spades")
        holeCards.put(card1)
        holeCards.put(card2)
        playerJson.put("hole_cards", holeCards)
        
        val playerInfo = playerJson.toPlayerInfo()
        assertEquals(1, playerInfo.id)
        assertEquals("Test Player", playerInfo.name)
        assertNotNull(playerInfo.hole_cards)
        assertEquals(2, playerInfo.hole_cards?.size)
        assertEquals("A", playerInfo.hole_cards?.get(0)?.rank)
        assertEquals("hearts", playerInfo.hole_cards?.get(0)?.suit)
        assertEquals("K", playerInfo.hole_cards?.get(1)?.rank)
        assertEquals("spades", playerInfo.hole_cards?.get(1)?.suit)
    }
    
    @Test
    fun `test JSONObject toGameState extension function`() {
        val gameStateJson = createGameStateJson(
            smallBlind = 25,
            players = listOf(
                createPlayerJson(id = 0, name = "Player 1", bet = 0, status = "active"),
                createPlayerJson(id = 1, name = "the donkey killers", bet = 0, status = "active")
            )
        )
        
        val gameState = gameStateJson.toGameState()
        assertEquals("tournament_123", gameState.tournament_id)
        assertEquals("game_456", gameState.game_id)
        assertEquals(1, gameState.round)
        assertEquals(25, gameState.small_blind)
        assertEquals(2, gameState.players.size)
        assertEquals("Player 1", gameState.players[0].name)
        assertEquals("the donkey killers", gameState.players[1].name)
    }
    
    // Helper methods to create test JSON objects
    private fun createGameStateJson(
        smallBlind: Int = 10,
        players: List<JSONObject> = emptyList()
    ): JSONObject {
        val gameState = JSONObject()
        gameState.put("tournament_id", "tournament_123")
        gameState.put("game_id", "game_456")
        gameState.put("round", 1)
        gameState.put("bet_index", 0)
        gameState.put("small_blind", smallBlind)
        gameState.put("current_buy_in", 0)
        gameState.put("pot", 0)
        gameState.put("minimum_raise", 10)
        gameState.put("dealer", 0)
        gameState.put("orbits", 0)
        gameState.put("in_action", 1)
        
        val playersArray = JSONArray()
        players.forEach { playersArray.put(it) }
        gameState.put("players", playersArray)
        
        gameState.put("community_cards", JSONArray())
        
        return gameState
    }
    
    private fun createPlayerJson(
        id: Int,
        name: String,
        bet: Int,
        status: String
    ): JSONObject {
        val player = JSONObject()
        player.put("id", id)
        player.put("name", name)
        player.put("status", status)
        player.put("version", "1.0")
        player.put("stack", 1000)
        player.put("bet", bet)
        return player
    }
}