package me.wickyplays.android.karaokeplayer.player.manager

import android.util.Log
import me.wickyplays.android.karaokeplayer.databinding.ActivityPlayerBinding
import me.wickyplays.android.karaokeplayer.player.obj.JudgementNode
import me.wickyplays.android.karaokeplayer.player.obj.Song
import java.io.File

class PlayerJudgementManager(binding: ActivityPlayerBinding) {

    var score = 0
    private var judgementNodes: List<JudgementNode> = emptyList()

    fun initJudgementFromPath(song: Song): List<JudgementNode> {
        return try {
            val judgementPath = song.judgementPath
            if (judgementPath != null) {
                Log.d("Player", "Reading judgement file: $judgementPath")
                val judgementFile = File(judgementPath)
                if (judgementFile.exists()) {
                    val judgementContent = judgementFile.readText()
                    val parsedNodes = parseJudgementJson(judgementContent)
                    judgementNodes = parsedNodes
                    parsedNodes
                } else {
                    Log.e("Player", "Judgement file not found at path: $judgementPath")
                    emptyList()
                }
            } else {
                Log.d("Player", "No judgement path provided for song")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("Player", "Error reading judgement file: ${e.message}")
            emptyList()
        }
    }

    private fun parseJudgementJson(jsonContent: String): List<JudgementNode> {
        return try {
            val jsonArray = org.json.JSONArray(jsonContent)
            val nodes = mutableListOf<JudgementNode>()

            for (i in 0 until jsonArray.length()) {
                var nodeObj = jsonArray.getJSONObject(i)
                nodes.add(
                    JudgementNode(
                        n = nodeObj.getInt("n"),
                        s = nodeObj.getDouble("s"),
                        e = nodeObj.getDouble("e"),
                        hit = nodeObj.optBoolean("hit", false)
                    )
                )
            }
            nodes
        } catch (e: Exception) {
            Log.e("Player", "Error parsing judgement JSON: ${e.message}")
            emptyList()
        }
    }

    //TODO: Could be more
    fun updateJudgement(currentTime: Double) {
    }

    fun resetScore() {
        score = 0
    }

    fun getJudgementNodes(): List<JudgementNode> {
        return judgementNodes
    }
}