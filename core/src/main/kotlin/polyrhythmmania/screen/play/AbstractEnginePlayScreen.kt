package polyrhythmmania.screen.play

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import paintbox.binding.VarChangedListener
import paintbox.transition.FadeIn
import paintbox.transition.FadeOut
import paintbox.transition.TransitionScreen
import paintbox.util.gdxutils.disposeQuietly
import paintbox.util.sumOfFloat
import polyrhythmmania.Localization
import polyrhythmmania.PRManiaGame
import polyrhythmmania.achievements.Achievements
import polyrhythmmania.container.Container
import polyrhythmmania.engine.Engine
import polyrhythmmania.engine.InputCalibration
import polyrhythmmania.engine.input.*
import polyrhythmmania.library.score.LevelScoreAttempt
import polyrhythmmania.screen.mainmenu.menu.SubmitDailyChallengeScoreMenu
import polyrhythmmania.screen.results.ResultsScreen
import polyrhythmmania.gamemodes.AbstractEndlessMode
import polyrhythmmania.gamemodes.DunkMode
import polyrhythmmania.gamemodes.GameMode
import polyrhythmmania.gamemodes.endlessmode.DailyChallengeScore
import polyrhythmmania.gamemodes.endlessmode.EndlessPolyrhythm
import polyrhythmmania.soundsystem.SimpleTimingProvider
import polyrhythmmania.soundsystem.SoundSystem
import polyrhythmmania.soundsystem.TimingProvider
import polyrhythmmania.statistics.PlayTimeType
import polyrhythmmania.world.EntityRodPR
import polyrhythmmania.world.render.ForceTilesetPalette
import polyrhythmmania.world.render.WorldRenderer
import polyrhythmmania.world.tileset.TilesetPalette
import java.time.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt


/**
 * A generic play screen for [Engine]-based gameplay.
 */
abstract class AbstractEnginePlayScreen(
        main: PRManiaGame, playTimeType: PlayTimeType?,

        val container: Container,
        val challenges: Challenges, val inputCalibration: InputCalibration,
        
        // TODO: BELOW: refactor to GameMode
        val gameMode: GameMode?, val resultsBehaviour: ResultsBehaviour
) : AbstractPlayScreen(main, playTimeType) {

    companion object; // Used for early init
    
    val timing: TimingProvider get() = container.timing
    val soundSystem: SoundSystem
        get() = container.soundSystem ?: error("${this::javaClass.name} requires a non-null SoundSystem in the Container")
    val engine: Engine get() = container.engine
    val renderer: WorldRenderer get() = container.renderer
    
    protected var goingToResults: Boolean = false

    private val endSignalListener: VarChangedListener<Boolean> = VarChangedListener {
        if (it.getOrCompute()) {
            Gdx.app.postRunnable {
                onEndSignalFired()
            }
        }
    }
    
    protected val dunkAchievementStartTimestamp: Instant = Instant.now() // TODO remove me, used only for dunk achievement

    init {
        engine.endSignalReceived.addListener(endSignalListener)
    }
    

    override fun renderGameplay(delta: Float) {
        renderer.render(batch)
    }

    override fun initializeGameplay() {
        // Reset/clearing pass
        engine.removeEvents(engine.events.toList())
        engine.inputter.areInputsLocked = engine.autoInputs
        engine.inputter.reset()
        engine.soundInterface.clearAllNonMusicAudio()
        engine.inputCalibration = this.inputCalibration
        engine.removeActiveTextbox(unpauseSoundInterface = false, runTextboxOnComplete = false)
        engine.resetEndSignal()
        container.world.resetWorld()
        renderer.onWorldReset()
        challenges.applyToEngine(engine)

        // Set everything else
        applyForcedTilesetPaletteSettings()
        container.setTexturePackFromSource()

        timing.seconds = -(1f + max(0f, this.inputCalibration.audioOffsetMs / 1000f))
        engine.seconds = timing.seconds
        val player = engine.soundInterface.getCurrentMusicPlayer(engine.musicData.beadsMusic)
        if (player != null) { // Set music player position
            val musicSample = player.musicSample
            musicSample.moveStartBuffer(0)
            engine.musicData.setMusicPlayerPositionToCurrentSec()
            player.pause(false)
        }
        soundSystem.startRealtime() // Does nothing if already started

        val blocks = container.blocks.toList()
        engine.addEvents(blocks.flatMap { it.compileIntoEvents() })
    }

    override fun onStartOver() {
        super.onStartOver()
    }

    override fun renderUpdate() {
        super.renderUpdate()

        if (!isPaused.get() && timing is SimpleTimingProvider) {
            timing.seconds += Gdx.graphics.deltaTime
            gameMode?.renderUpdate()
        }
    }

    /**
     * Will be triggered in the gdx main thread.
     */
    protected open fun onEndSignalFired() {
        soundSystem.setPaused(true)
        container.world.entities.filterIsInstance<EntityRodPR>().forEach { rod ->
            engine.inputter.submitInputsFromRod(rod)
        }
        
        // TODO this should be handled by each GameMode
        if (resultsBehaviour is ResultsBehaviour.ShowResults) {
            transitionToResults(resultsBehaviour)
        } else {
            val sideMode = this.gameMode
            if (sideMode is EndlessPolyrhythm && sideMode.dailyChallenge != null) {
                val menuCol = main.mainMenuScreen.menuCollection
                val score: DailyChallengeScore = main.settings.endlessDailyChallenge.getOrCompute()
                val nonce = sideMode.dailyChallengeUUIDNonce.getOrCompute()
                if (score.score > 0 && !engine.autoInputs) {
                    val submitMenu = SubmitDailyChallengeScoreMenu(menuCol, sideMode.dailyChallenge, nonce, score)
                    menuCol.addMenu(submitMenu)
                    menuCol.pushNextMenu(submitMenu, instant = true, playSound = false)
                }

                quitToMainMenu()
            } else {
                if (sideMode is DunkMode) {
                    val localDateTime = LocalDateTime.ofInstant(dunkAchievementStartTimestamp, ZoneId.systemDefault())
                    if (localDateTime.dayOfWeek == DayOfWeek.FRIDAY && localDateTime.toLocalTime() >= LocalTime.of(17, 0)) {
                        Achievements.awardAchievement(Achievements.dunkFridayNight)
                    }
                }
                quitToMainMenu()
            }
        }
    }
    
    
    protected fun quitToMainMenu() {
        val main = this.main
        val currentScreen = main.screen
        Gdx.app.postRunnable {
            val mainMenu = main.mainMenuScreen.prepareShow(doFlipAnimation = true)
            main.screen = TransitionScreen(main, currentScreen, mainMenu,
                    FadeOut(0.25f, Color(0f, 0f, 0f, 1f)), FadeIn(0.125f, Color(0f, 0f, 0f, 1f))).apply {
                this.onEntryEnd = {
                    currentScreen.dispose()
                    container.disposeQuietly()
                }
            }
        }
    }
    
    
    protected fun transitionToResults(resultsBehaviour: ResultsBehaviour.ShowResults) {
        val inputter = engine.inputter
        val inputsHit = inputter.inputResults.count { it.inputScore != InputScore.MISS }
        val nInputs = max(inputter.totalExpectedInputs, inputter.minimumInputCount)
        val rawScore: Float = (if (nInputs <= 0) 0f else ((inputter.inputResults.map { it.inputScore }.sumOfFloat { inputScore ->
            inputScore.weight
        } / nInputs) * 100))
        val score: Int = rawScore.roundToInt().coerceIn(0, 100)

        val resultsText = container.resultsText
        val ranking = Ranking.getRanking(score)
        val leftResults = inputter.inputResults.filter { it.inputType == InputType.DPAD }
        val rightResults = inputter.inputResults.filter { it.inputType == InputType.A }
        val badLeftGoodRight = leftResults.isNotEmpty() && rightResults.isNotEmpty()
                && (leftResults.sumOfFloat { abs(it.accuracyPercent) } / leftResults.size) - 0.15f > (rightResults.sumOfFloat { abs(it.accuracyPercent) } / rightResults.size)
        val lines: Pair<String, String> = resultsText.generateLinesOfText(score, badLeftGoodRight)
        var isNewHighScore = false
        if (gameMode != null && gameMode is AbstractEndlessMode) {
            val endlessModeScore = gameMode.prevHighScore
            val prevScore = endlessModeScore.highScore.getOrCompute()
            if (score > prevScore) {
                endlessModeScore.highScore.set(score)
                PRManiaGame.instance.settings.persist()
                isNewHighScore = true
            }
        } else if (resultsBehaviour.previousHighScore != null) {
            if (score > resultsBehaviour.previousHighScore && resultsBehaviour.previousHighScore >= 0) {
                isNewHighScore = true
            }
        }

        val scoreObj = Score(score, rawScore, inputsHit, nInputs,
                inputter.skillStarGotten.get() && inputter.skillStarBeat.isFinite(), inputter.noMiss,
                challenges,
                resultsText.title ?: Localization.getValue("play.results.defaultTitle"),
                lines.first, lines.second,
                ranking, isNewHighScore
        )


        fun transitionAway(nextScreen: Screen, disposeContainer: Boolean, action: () -> Unit) {
            action.invoke()

            main.screen = TransitionScreen(main, this, nextScreen,
                    FadeOut(0.5f, Color(0f, 0f, 0f, 1f)), FadeIn(0.125f, Color(0f, 0f, 0f, 1f))).apply {
                this.onEntryEnd = {
                    this@AbstractEnginePlayScreen.dispose()
                    if (disposeContainer) {
                        container.disposeQuietly()
                    }
                }
            }
        }

        goingToResults = true
        transitionAway(ResultsScreen(main, scoreObj, container, gameMode, {
            copyThisScreenForResultsStartOver(scoreObj, resultsBehaviour)
        }, keyboardKeybinds,
                LevelScoreAttempt(System.currentTimeMillis(), scoreObj.scoreInt, scoreObj.noMiss, scoreObj.skillStar, scoreObj.challenges),
                resultsBehaviour.onRankingRevealed), disposeContainer = false) {}
    }
    
    protected abstract fun copyThisScreenForResultsStartOver(scoreObj: Score, resultsBehaviour: ResultsBehaviour): AbstractEnginePlayScreen

    
    override fun pauseGame(playSound: Boolean) {
        super.pauseGame(playSound)
        
        soundSystem.setPaused(true)
    }

    override fun unpauseGame(playSound: Boolean) {
        super.unpauseGame(playSound)
        
        val player = engine.soundInterface.getCurrentMusicPlayer(engine.musicData.beadsMusic)
        if (player != null) {
            engine.musicData.setMusicPlayerPositionToCurrentSec()
            player.pause(false)
        }
        soundSystem.setPaused(false)
    }


    override fun shouldCatchCursor(): Boolean = true
    override fun uncatchCursorOnHide(): Boolean {
        return !goingToResults
    }
    
    override fun keyDown(keycode: Int): Boolean {
        var consumed = false
        if (main.screen === this) {
            if (!isPaused.get()) {
                when (keycode) {
                    keyboardKeybinds.buttonDpadUp, keyboardKeybinds.buttonDpadDown,
                    keyboardKeybinds.buttonDpadLeft, keyboardKeybinds.buttonDpadRight -> {
                        engine.postRunnable {
                            engine.inputter.onDpadButtonPressed(false)
                        }
                        consumed = true
                    }
                    keyboardKeybinds.buttonA -> {
                        engine.postRunnable {
                            engine.inputter.onAButtonPressed(false)
                        }
                        consumed = true
                    }
                }
            }
        }

        return consumed || super.keyDown(keycode)
    }
    
    override fun keyUp(keycode: Int): Boolean {
        var consumed = false
        if (main.screen === this) {
            if (!isPaused.get())  {
                when (keycode) {
                    keyboardKeybinds.buttonDpadUp, keyboardKeybinds.buttonDpadDown,
                    keyboardKeybinds.buttonDpadLeft, keyboardKeybinds.buttonDpadRight -> {
                        engine.postRunnable {
                            engine.inputter.onDpadButtonPressed(true)
                        }
                        consumed = true
                    }
                    keyboardKeybinds.buttonA -> {
                        engine.postRunnable {
                            engine.inputter.onAButtonPressed(true)
                        }
                        consumed = true
                    }
                }
            }
        }

        return consumed || super.keyUp(keycode)
    }

    override fun _dispose() {
        // NOTE: container instance is disposed separately.
        // Additionally, the sound system is disposed in the container, so it doesn't have to be stopped.
        engine.endSignalReceived.removeListener(endSignalListener)
    }

    override fun getDebugString(): String {
        return super.getDebugString() + """---
SoundSystem: paused=${soundSystem.isPaused}
---
${engine.getDebugString()}
---
${renderer.getDebugString()}
---
SideMode: ${gameMode?.javaClass?.name}${if (gameMode != null) ("\n" + gameMode.getDebugString()) else ""}
"""
    }
    

    protected fun applyForcedTilesetPaletteSettings() {
        when (container.globalSettings.forceTilesetPalette) {
            ForceTilesetPalette.NO_FORCE ->
                container.world.tilesetPalette
            ForceTilesetPalette.FORCE_PR1 ->
                TilesetPalette.createGBA1TilesetPalette()
            ForceTilesetPalette.FORCE_PR2 ->
                TilesetPalette.createGBA2TilesetPalette()
            ForceTilesetPalette.ORANGE_BLUE ->
                TilesetPalette.createOrangeBlueTilesetPalette()
        }.applyTo(container.renderer.tileset)
    }
}
