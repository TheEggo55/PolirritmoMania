package polyrhythmmania.screen.play

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import paintbox.transition.*
import paintbox.util.gdxutils.disposeQuietly
import paintbox.util.sumOfFloat
import polyrhythmmania.Localization
import polyrhythmmania.PRManiaGame
import polyrhythmmania.achievements.Achievements
import polyrhythmmania.container.Container
import polyrhythmmania.engine.InputCalibration
import polyrhythmmania.engine.input.*
import polyrhythmmania.gamemodes.AbstractEndlessMode
import polyrhythmmania.gamemodes.DunkMode
import polyrhythmmania.gamemodes.GameMode
import polyrhythmmania.gamemodes.endlessmode.DailyChallengeScore
import polyrhythmmania.gamemodes.endlessmode.EndlessPolyrhythm
import polyrhythmmania.library.score.LevelScoreAttempt
import polyrhythmmania.screen.mainmenu.menu.SubmitDailyChallengeScoreMenu
import polyrhythmmania.screen.play.pause.PauseMenuHandler
import polyrhythmmania.screen.play.pause.PauseOption
import polyrhythmmania.screen.play.pause.TengokuBgPauseMenuHandler
import polyrhythmmania.screen.results.ResultsScreen
import polyrhythmmania.statistics.PlayTimeType
import polyrhythmmania.world.WorldType
import java.time.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt


/**
 * Base implementation of [AbstractEnginePlayScreen] for standard game modes (Polyrhythm, practice, 
 * Dunk, Assemble).
 * 
 * Handles score + dunk achievements, results (standard PR), Daily Challenge score submission.
 */
class EnginePlayScreenBase(
        main: PRManiaGame, playTimeType: PlayTimeType?,
        container: Container,
        challenges: Challenges, inputCalibration: InputCalibration,
        gameMode: GameMode?, val resultsBehaviour: ResultsBehaviour
) : AbstractEnginePlayScreen(main, playTimeType, container, challenges, inputCalibration, gameMode) {
    
    override val pauseMenuHandler: PauseMenuHandler = TengokuBgPauseMenuHandler(this)
    
    private val dunkAchievementStartTimestamp: Instant = Instant.now() // Used for a dunk achievement
    private var endlessPrPauseTime: Float = 0f // Used for an endless achievement for pausing in between patterns
    
    private var disableCatchingCursorOnHide: Boolean = false
    
    init {
        if (engine.inputter.endlessScore.enabled
                && engine.world.worldMode.worldType is WorldType.Polyrhythm
                && engine.inputter.endlessScore.maxLives.get() == 1) { // Daredevil mode in endless
            (pauseMenuHandler as? TengokuBgPauseMenuHandler)?.also { handler ->
                val hex = "DB2323"
                val bg = handler.pauseBg
                bg.cycleSpeed = 0f
                bg.topColor.set(Color.valueOf(hex))
                bg.bottomColor.set(Color.valueOf(hex))
            }
        }
    }
    
    init {
        // Score achievements for endless-type modes
        engine.inputter.endlessScore.score.addListener { scoreVar ->
            if (engine.inputter.endlessScore.enabled && engine.areStatisticsEnabled) {
                val newScore = scoreVar.getOrCompute()
                when (engine.world.worldMode.worldType) {
                    is WorldType.Polyrhythm -> {
                        if (gameMode is EndlessPolyrhythm) {
                            if (gameMode.dailyChallenge != null) {
                                listOf(Achievements.dailyScore25, Achievements.dailyScore50,
                                        Achievements.dailyScore75, Achievements.dailyScore100,
                                        Achievements.dailyScore125).forEach {
                                    Achievements.attemptAwardScoreAchievement(it, newScore)
                                }
                            } else {
                                listOf(Achievements.endlessScore25, Achievements.endlessScore50,
                                        Achievements.endlessScore75, Achievements.endlessScore100,
                                        Achievements.endlessScore125).forEach {
                                    Achievements.attemptAwardScoreAchievement(it, newScore)
                                }

                                if (gameMode.disableLifeRegen) {
                                    Achievements.attemptAwardScoreAchievement(Achievements.endlessNoLifeRegen100, newScore)
                                }
                                if (engine.inputter.endlessScore.maxLives.get() == 1) { // Daredevil
                                    Achievements.attemptAwardScoreAchievement(Achievements.endlessDaredevil100, newScore)
                                }
                                if (main.settings.masterVolumeSetting.getOrCompute() == 0) {
                                    Achievements.attemptAwardScoreAchievement(Achievements.endlessSilent50, newScore)
                                }
                            }
                        }

                    }
                    WorldType.Dunk -> {
                        listOf(Achievements.dunkScore10, Achievements.dunkScore20, Achievements.dunkScore30,
                                Achievements.dunkScore50).forEach {
                            Achievements.attemptAwardScoreAchievement(it, newScore)
                        }
                    }
                    WorldType.Assemble -> {
                        // NO-OP
                    }
                }
            }
        }
    }
    
    init {
        val optionList = mutableListOf<PauseOption>()
        optionList += PauseOption(if (engine.autoInputs) "play.pause.resume.robotMode" else "play.pause.resume", true) {
            unpauseGame(true)
        }
        optionList += PauseOption("play.pause.startOver", !(gameMode is EndlessPolyrhythm && gameMode.dailyChallenge != null)) {
            playMenuSound("sfx_menu_enter_game")

            val thisScreen: EnginePlayScreenBase = this
            val resetAction: () -> Unit = {
                resetAndUnpause()
                disableCatchingCursorOnHide = false
            }
            if (shouldCatchCursor()) {
                disableCatchingCursorOnHide = true
                Gdx.input.isCursorCatched = true
            }
            main.screen = TransitionScreen(main, thisScreen, thisScreen,
                    WipeTransitionHead(Color.BLACK.cpy(), 0.4f), WipeTransitionTail(Color.BLACK.cpy(), 0.4f)).apply {
                onEntryEnd = resetAction
            }
        }
        optionList += PauseOption("play.pause.quitToMainMenu", true) {
            quitToScreen()
            Gdx.app.postRunnable {
                playMenuSound("sfx_pause_exit")
            }
        }
        this.pauseOptions.set(optionList)
    }


    override fun onEndSignalFired() {
        super.onEndSignalFired()
        
        when (val behaviour = resultsBehaviour) {
            is ResultsBehaviour.ShowResults -> {
                transitionToResults(behaviour)
            }
            else -> {
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

                    quitToScreen()
                } else {
                    if (sideMode is DunkMode) {
                        val localDateTime = LocalDateTime.ofInstant(dunkAchievementStartTimestamp, ZoneId.systemDefault())
                        if (localDateTime.dayOfWeek == DayOfWeek.FRIDAY && localDateTime.toLocalTime() >= LocalTime.of(17, 0)) {
                            Achievements.awardAchievement(Achievements.dunkFridayNight)
                        }
                    }
                    quitToScreen()
                }
            }
        }
    }

    
    private fun transitionToResults(resultsBehaviour: ResultsBehaviour.ShowResults) {
        val inputter = engine.inputter
        val inputsHit = inputter.inputResults.count { it.inputScore != InputScore.MISS }
        val nInputs = max(inputter.totalExpectedInputs, inputter.minimumInputCount)
        val rawScore: Float = (if (nInputs <= 0) 0f else ((inputter.inputResults.map { it.inputScore }.sumOfFloat { inputScore ->
            inputScore.weight
        } / nInputs) * 100))
        val score: Int = rawScore.roundToInt().coerceIn(0, 100)

        val resultsText = container.resultsText
        val ranking = Ranking.getRanking(score)
        val leftResults = inputter.inputResults.filter { it.inputType == InputType.DPAD_ANY }
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
                    FadeToOpaque(0.5f, Color(0f, 0f, 0f, 1f)), FadeToTransparent(0.125f, Color(0f, 0f, 0f, 1f))).apply {
                this.onEntryEnd = {
                    this@EnginePlayScreenBase.disposeQuietly()
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
    
    private fun copyThisScreenForResultsStartOver(scoreObj: Score, resultsBehaviour: ResultsBehaviour): EnginePlayScreenBase {
        return EnginePlayScreenBase(main, playTimeType, container, challenges, inputCalibration, gameMode,
                if (resultsBehaviour is ResultsBehaviour.ShowResults)
                    resultsBehaviour.copy(previousHighScore = if (scoreObj.newHighScore)
                        scoreObj.scoreInt
                    else resultsBehaviour.previousHighScore)
                else resultsBehaviour)
    }

    
    override fun uncatchCursorOnHide(): Boolean {
        return super.uncatchCursorOnHide() && !disableCatchingCursorOnHide
    }

    override fun renderGameplay(delta: Float) {
        super.renderGameplay(delta)

        if (isPaused.get()) {
            endlessPrPauseTime += Gdx.graphics.deltaTime
        }
    }

    override fun pauseGame(playSound: Boolean) {
        super.pauseGame(playSound)

        endlessPrPauseTime = 0f
    }

    override fun unpauseGame(playSound: Boolean) {
        super.unpauseGame(playSound)

        if (gameMode != null && gameMode is EndlessPolyrhythm) {
            gameMode.submitPauseTime(this.endlessPrPauseTime)
        }
    }
}