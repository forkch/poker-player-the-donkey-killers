package poker.player.kotlin

import org.json.JSONObject

class Player {
    val numberOfPlayers = 4
    fun betRequest(game_state: JSONObject): Int {
        val currentSmallBlind = game_state.getInt("small_blind")

        val indexOfDealer = game_state.getInt("dealer")
        val round = game_state.getInt("round")

        val ourPlayer = getOurPlayer(game_state)
        println(ourPlayer)

        val ourIndex = ourPlayer.getInt("id")
        println(ourIndex)

        if (everyPlayersBetIsZero(game_state)) {
            return game_state.getInt("small_blind")
        }


        return 0
    }

    private fun everyPlayersBetIsZero(gameState: JSONObject): Boolean {
        val filter = gameState.getJSONArray("players").filter { player ->
            player is JSONObject && player.getInt("bet") != 0 && player.getString("status").equals("active", true)
        }

        return filter.isEmpty()
    }

    private fun getOurPlayer(game_state: JSONObject): JSONObject {
        val find = game_state.getJSONArray("players").find { player ->
            player is JSONObject && player.getString("name").equals("the donkey killers", true)
        }
        return find as JSONObject
    }

    fun showdown() {
    }

    fun version(): String {
        return "Kotlin Player 0.0.1"
    }
}
