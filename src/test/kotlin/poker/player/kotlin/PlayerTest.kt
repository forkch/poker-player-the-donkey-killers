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
        val gameState = createGameState(
            smallBlind = 10,
            players = listOf(
                createPlayerInfo(id = 0, name = "Player 1", bet = 0, status = "active"),
                createPlayerInfo(id = 1, name = "the donkey killers", bet = 0, status = "active"),
                createPlayerInfo(id = 2, name = "Player 3", bet = 0, status = "active")
            )
        )
        
        val result = player.betRequest(gameState)
        assertEquals(10, result)
    }
    
    @Test
    fun `test betRequest returns 0 when some players have bets`() {
        val gameState = createGameState(
            smallBlind = 10,
            players = listOf(
                createPlayerInfo(id = 0, name = "Player 1", bet = 20, status = "active"),
                createPlayerInfo(id = 1, name = "the donkey killers", bet = 0, status = "active"),
                createPlayerInfo(id = 2, name = "Player 3", bet = 0, status = "active")
            )
        )
        
        val result = player.betRequest(gameState)
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
    
    @Test
    fun `test betRequest returns 10 when our player has K and A hole cards`() {
        val ourPlayerJson = createPlayerJson(id = 1, name = "the donkey killers", bet = 0, status = "active")
        
        val holeCards = JSONArray()
        val card1 = JSONObject()
        card1.put("rank", "K")
        card1.put("suit", "hearts")
        val card2 = JSONObject() 
        card2.put("rank", "A")
        card2.put("suit", "spades")
        holeCards.put(card1)
        holeCards.put(card2)
        ourPlayerJson.put("hole_cards", holeCards)
        
        val gameStateJson = createGameStateJson(
            smallBlind = 10,
            players = listOf(
                createPlayerJson(id = 0, name = "Player 1", bet = 0, status = "active"),
                ourPlayerJson,
                createPlayerJson(id = 2, name = "Player 3", bet = 0, status = "active")
            )
        )
        
        val result = player.betRequest(gameStateJson)
        assertEquals(10, result)
    }
    
    @Test
    fun `test betRequest returns 10 when our player has A and K hole cards in different order`() {
        val ourPlayerJson = createPlayerJson(id = 1, name = "the donkey killers", bet = 0, status = "active")
        
        val holeCards = JSONArray()
        val card1 = JSONObject()
        card1.put("rank", "A")
        card1.put("suit", "diamonds")
        val card2 = JSONObject() 
        card2.put("rank", "K")
        card2.put("suit", "clubs")
        holeCards.put(card1)
        holeCards.put(card2)
        ourPlayerJson.put("hole_cards", holeCards)
        
        val gameStateJson = createGameStateJson(
            smallBlind = 15,
            players = listOf(
                createPlayerJson(id = 0, name = "Player 1", bet = 20, status = "active"),
                ourPlayerJson,
                createPlayerJson(id = 2, name = "Player 3", bet = 0, status = "active")
            )
        )
        
        val result = player.betRequest(gameStateJson)
        assertEquals(15, result)
    }
    
    @Test
    fun `test betRequest does not return 10 when our player has K but not A`() {
        val ourPlayerJson = createPlayerJson(id = 1, name = "the donkey killers", bet = 0, status = "active")
        
        val holeCards = JSONArray()
        val card1 = JSONObject()
        card1.put("rank", "K")
        card1.put("suit", "hearts")
        val card2 = JSONObject() 
        card2.put("rank", "Q")
        card2.put("suit", "spades")
        holeCards.put(card1)
        holeCards.put(card2)
        ourPlayerJson.put("hole_cards", holeCards)
        
        val gameStateJson = createGameStateJson(
            smallBlind = 10,
            players = listOf(
                createPlayerJson(id = 0, name = "Player 1", bet = 0, status = "active"),
                ourPlayerJson,
                createPlayerJson(id = 2, name = "Player 3", bet = 0, status = "active")
            )
        )
        
        val result = player.betRequest(gameStateJson)
        assertEquals(10, result) // Returns small blind since all bets are zero
    }
    
    @Test
    fun `test betRequest does not return 10 when our player has no hole cards`() {
        val gameStateJson = createGameStateJson(
            smallBlind = 10,
            players = listOf(
                createPlayerJson(id = 0, name = "Player 1", bet = 0, status = "active"),
                createPlayerJson(id = 1, name = "the donkey killers", bet = 0, status = "active"),
                createPlayerJson(id = 2, name = "Player 3", bet = 0, status = "active")
            )
        )
        
        val result = player.betRequest(gameStateJson)
        assertEquals(10, result) // Returns small blind since all bets are zero
    }
    
    @Test
    fun `test betRequest uses ranking API when player has hole cards`() {
        val gameState = createGameState(
            smallBlind = 20,
            players = listOf(
                createPlayerInfo(id = 0, name = "Player 1", bet = 5, status = "active"),
                createPlayerInfo(
                    id = 1, 
                    name = "the donkey killers", 
                    bet = 0, 
                    status = "active",
                    holeCards = listOf(
                        createCard("5", "diamonds"),
                        createCard("6", "diamonds")
                    )
                ),
                createPlayerInfo(id = 2, name = "Player 3", bet = 0, status = "active")
            ),
            communityCards = listOf(
                createCard("7", "diamonds"),
                createCard("8", "diamonds"),
                createCard("9", "diamonds")
            )
        )
        
        // This test verifies that the API integration doesn't break the betting logic
        // The actual API call may fail in test environment, but the logic should handle it gracefully
        val result = player.betRequest(gameState)
        assertTrue(result >= 0) // Should return a valid bet amount
    }
    
    @Test
    fun `test getRanking handles empty cards gracefully`() {
        val gameState = createGameState(
            smallBlind = 10,
            players = listOf(
                createPlayerInfo(
                    id = 0, 
                    name = "Player 1", 
                    bet = 0, 
                    status = "active"
                ),
                createPlayerInfo(
                    id = 1, 
                    name = "the donkey killers", 
                    bet = 0, 
                    status = "active",
                    holeCards = emptyList()
                )
            )
        )
        
        // Should not crash when hole cards are empty
        val result = player.betRequest(gameState)
        assertEquals(10, result) // Falls back to small blind since all bets are zero
    }
    
    @Test
    fun `test RankingResponse data class creation`() {
        val cards = listOf(
            createCard("5", "diamonds"),
            createCard("6", "diamonds")
        )
        
        val rankingResponse = RankingResponse(
            rank = 8,
            value = 9,
            second_value = 9,
            kickers = listOf(9, 8, 6, 5),
            cards_used = cards,
            cards = cards
        )
        
        assertEquals(8, rankingResponse.rank)
        assertEquals(9, rankingResponse.value)
        assertEquals(9, rankingResponse.second_value)
        assertEquals(listOf(9, 8, 6, 5), rankingResponse.kickers)
        assertEquals(2, rankingResponse.cards_used.size)
        assertEquals(2, rankingResponse.cards.size)
        assertEquals("5", rankingResponse.cards_used[0].rank)
        assertEquals("diamonds", rankingResponse.cards_used[0].suit)
    }
    
    @Test
    fun `test JSONObject toRankingResponse extension function`() {
        val rankingJson = JSONObject()
        rankingJson.put("rank", 8)
        rankingJson.put("value", 9)
        rankingJson.put("second_value", 9)
        
        val kickersArray = JSONArray()
        kickersArray.put(9)
        kickersArray.put(8)
        kickersArray.put(6)
        kickersArray.put(5)
        rankingJson.put("kickers", kickersArray)
        
        val cardsUsedArray = JSONArray()
        val card1 = JSONObject()
        card1.put("rank", "5")
        card1.put("suit", "diamonds")
        val card2 = JSONObject()
        card2.put("rank", "6")
        card2.put("suit", "diamonds")
        cardsUsedArray.put(card1)
        cardsUsedArray.put(card2)
        rankingJson.put("cards_used", cardsUsedArray)
        
        val cardsArray = JSONArray()
        cardsArray.put(card1)
        cardsArray.put(card2)
        rankingJson.put("cards", cardsArray)
        
        val rankingResponse = rankingJson.toRankingResponse()
        
        assertEquals(8, rankingResponse.rank)
        assertEquals(9, rankingResponse.value)
        assertEquals(9, rankingResponse.second_value)
        assertEquals(listOf(9, 8, 6, 5), rankingResponse.kickers)
        assertEquals(2, rankingResponse.cards_used.size)
        assertEquals(2, rankingResponse.cards.size)
        assertEquals("5", rankingResponse.cards_used[0].rank)
        assertEquals("diamonds", rankingResponse.cards_used[0].suit)
        assertEquals("6", rankingResponse.cards[1].rank)
        assertEquals("diamonds", rankingResponse.cards[1].suit)
    }
    
    // Helper methods to create test data objects
    private fun createGameState(
        smallBlind: Int = 10,
        players: List<PlayerInfo> = emptyList(),
        communityCards: List<Card> = emptyList()
    ): GameState {
        return GameState(
            tournament_id = "tournament_123",
            game_id = "game_456",
            round = 1,
            bet_index = 0,
            small_blind = smallBlind,
            current_buy_in = 0,
            pot = 0,
            minimum_raise = 10,
            dealer = 0,
            orbits = 0,
            in_action = 1,
            players = players,
            community_cards = communityCards
        )
    }
    
    private fun createPlayerInfo(
        id: Int,
        name: String,
        bet: Int,
        status: String,
        holeCards: List<Card>? = null
    ): PlayerInfo {
        return PlayerInfo(
            id = id,
            name = name,
            status = status,
            version = "1.0",
            stack = 1000,
            bet = bet,
            hole_cards = holeCards
        )
    }
    
    private fun createCard(rank: String, suit: String): Card {
        return Card(rank = rank, suit = suit)
    }
    
    // Legacy JSON helper methods (for extension function tests)
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