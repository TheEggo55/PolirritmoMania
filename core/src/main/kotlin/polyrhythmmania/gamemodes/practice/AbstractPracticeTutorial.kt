package polyrhythmmania.gamemodes.practice

import com.badlogic.gdx.Gdx
import polyrhythmmania.PRManiaGame
import polyrhythmmania.achievements.Achievements
import polyrhythmmania.gamemodes.GameMode
import polyrhythmmania.statistics.PlayTimeType
import polyrhythmmania.world.WorldMode
import polyrhythmmania.world.WorldSettings
import polyrhythmmania.world.WorldType


abstract class AbstractPracticeTutorial(main: PRManiaGame, playTimeType: PlayTimeType, val flagBit: Int)
    : GameMode(main, playTimeType) {
    
    init {
        container.world.worldMode = WorldMode(WorldType.Polyrhythm())
        container.world.showInputFeedback = true // Overrides user settings
        container.world.worldSettings = WorldSettings(showInputIndicators = true, useEnglishSigns = true)
    }

    init {
        // Trigger play all tutorials achievement
        engine.endSignalReceived.addListener {
            if (it.getOrCompute()) {
                Gdx.app.postRunnable {
                    Achievements.tutorialFlag = Achievements.tutorialFlag or flagBit
                    if (Achievements.tutorialFlag == 0b0011) {
                        Achievements.awardAchievement(Achievements.playAllTutorials)
                    }
                    Achievements.persist()
                }
            }
        }
    }

}