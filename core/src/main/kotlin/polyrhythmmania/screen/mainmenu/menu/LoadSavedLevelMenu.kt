package polyrhythmmania.screen.mainmenu.menu

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Align
import paintbox.Paintbox
import paintbox.binding.BooleanVar
import paintbox.binding.IntVar
import paintbox.binding.Var
import paintbox.filechooser.FileExtFilter
import paintbox.filechooser.TinyFDWrapper
import paintbox.font.TextAlign
import paintbox.packing.PackedSheet
import paintbox.registry.AssetRegistry
import paintbox.transition.FadeToTransparent
import paintbox.transition.TransitionScreen
import paintbox.ui.Anchor
import paintbox.ui.ImageIcon
import paintbox.ui.Pane
import paintbox.ui.area.Insets
import paintbox.ui.control.CheckBox
import paintbox.ui.control.Slider
import paintbox.ui.control.TextLabel
import paintbox.ui.layout.HBox
import paintbox.ui.layout.VBox
import paintbox.util.gdxutils.disposeQuietly
import polyrhythmmania.Localization
import polyrhythmmania.PreferenceKeys
import polyrhythmmania.container.Container
import polyrhythmmania.container.GlobalContainerSettings
import polyrhythmmania.discord.DefaultPresences
import polyrhythmmania.discord.DiscordRichPresence
import polyrhythmmania.editor.EditorSpecialFlags
import polyrhythmmania.editor.block.BlockEndState
import polyrhythmmania.editor.block.Instantiators
import polyrhythmmania.engine.input.Challenges
import polyrhythmmania.library.score.GlobalScoreCache
import polyrhythmmania.library.score.LevelScore
import polyrhythmmania.screen.mainmenu.bg.BgType
import polyrhythmmania.screen.play.regular.EnginePlayScreenBase
import polyrhythmmania.screen.play.regular.OnRankingRevealed
import polyrhythmmania.screen.play.regular.ResultsBehaviour
import polyrhythmmania.soundsystem.SimpleTimingProvider
import polyrhythmmania.soundsystem.SoundSystem
import polyrhythmmania.statistics.GlobalStats
import polyrhythmmania.statistics.PlayTimeType
import java.io.File
import java.util.*
import kotlin.concurrent.thread

class LoadSavedLevelMenu(
        menuCol: MenuCollection, immediateLoad: File?,
        val previousHighScore: Int? = null
) : StandardMenu(menuCol) {

    private sealed class Substate {
        data object FileDialogOpen : Substate()
        data object Loading : Substate()
        class Loaded(val loadMetadata: Container.LoadMetadata, val needsFlashingLightsWarning: Boolean) : Substate()
        data object LoadError : Substate()
    }

    private val substate: Var<Substate> = Var(if (immediateLoad == null) Substate.FileDialogOpen else Substate.Loading)

    val descLabel: TextLabel
    val challengeSetting: Pane
    
    val robotMode: BooleanVar = BooleanVar(false)
    val goForPerfect: BooleanVar = BooleanVar(false)
    val tempoUp: IntVar = IntVar(100)

    @Volatile
    private var loaded: LoadData? = null
    
    private val flashingLightsAcked: BooleanVar = BooleanVar(false)

    init {
        this.setSize(WIDTH_MID)
        this.titleText.bind { Localization.getVar("mainMenu.play.playSavedLevel.title").use() }
        this.contentPane.bounds.height.set(300f)
        this.deleteWhenPopped.set(true)

        val content = VBox().apply {
            Anchor.TopLeft.configure(this)
            this.spacing.set(0f)
            this.bindHeightToParent(-40f)
        }
        val hbox = HBox().apply {
            Anchor.BottomLeft.configure(this)
            this.spacing.set(8f)
            this.padding.set(Insets(4f, 0f, 2f, 2f))
            this.bounds.height.set(40f)
            this.bindWidthToParent(adjust = -48f)
        }

        descLabel = TextLabel(text = Localization.getValue("common.closeFileChooser")).apply {
            this.markup.set(this@LoadSavedLevelMenu.markup)
            this.padding.set(Insets(4f))
            this.bounds.height.set(96f)
            this.textColor.set(LongButtonSkin.TEXT_COLOR)
            this.renderAlign.set(Align.center)
            this.textAlign.set(TextAlign.CENTRE)
        }
        content.addChild(descLabel)
        challengeSetting = Pane().apply {
            Anchor.BottomLeft.configure(this)
            this.bindHeightToParent(adjust = -96f)
            this.margin.set(Insets(4f, 0f, 0f, 0f))
            this.visible.set(false)
            this += VBox().apply {
                this.spacing.set(1f)
                this += TextLabel(binding = { Localization.getVar("mainMenu.play.challengeSettings").use() },
                        font = this@LoadSavedLevelMenu.font).apply {
                    this.bounds.height.set(32f)
                }
                this += Pane().apply {
                    this.bounds.height.set(32f)
                    val perfectCheckbox = CheckBox(binding = { Localization.getVar("mainMenu.play.challengeSettings.perfect").use() },
                            font = this@LoadSavedLevelMenu.font).apply {
                        this.bindWidthToParent(multiplier = 0.5f)
                        this.checkedState.set(goForPerfect.get())
                        this.color.set(LongButtonSkin.TEXT_COLOR)
                        this.color.bind {
                            if (apparentDisabledState.use()) {
                                LongButtonSkin.DISABLED_TEXT
                            } else LongButtonSkin.TEXT_COLOR
                        }
                        this.textLabel.padding.set(Insets(0f, 0f, 4f, 0f))
                        this.onCheckChanged = { newState ->
                            goForPerfect.set(newState)
                        }
                        this.disabled.bind { robotMode.use() }
                    }
                    this += perfectCheckbox
                    this += CheckBox(binding = { Localization.getVar("mainMenu.play.challengeSettings.robotMode").use() },
                            font = this@LoadSavedLevelMenu.font).apply {
                        this.bindWidthToParent(multiplier = 0.5f)
                        Anchor.TopRight.configure(this)
                        this.checkedState.set(robotMode.get())
                        this.color.set(LongButtonSkin.TEXT_COLOR)
                        this.textLabel.padding.set(Insets(0f, 0f, 4f, 0f))
                        this.onCheckChanged = { newState ->
                            robotMode.set(newState)
                            if (newState) {
                                perfectCheckbox.checkedState.set(false)
                            }
                        }
                        this.setOnAction {
                            val newState = checkedState.invert()
                            if (newState) {
                                menuCol.playMenuSound("sfx_pause_robot_on")
                            } else {
                                menuCol.playMenuSound("sfx_pause_robot_off")
                            }
                        }
                        this.tooltipElement.set(createTooltip(Localization.getVar("mainMenu.play.challengeSettings.robotMode.tooltip")))
                    }
                }
                this += HBox().apply {
                    this.bounds.height.set(32f)
                    this.spacing.set(8f)
                    this += TextLabel(binding = { Localization.getVar("mainMenu.play.challengeSettings.speed").use() },
                            font = this@LoadSavedLevelMenu.font).apply {
                        this.bounds.width.set(100f)
                        this.textColor.set(LongButtonSkin.TEXT_COLOR)
                        this.renderAlign.set(Align.right)
                    }
                    val slider = Slider().apply slider@{
                        this.bounds.width.set(200f)
                        this.setValue(tempoUp.get().toFloat())
                        this.minimum.set(10f)
                        this.maximum.set(250f)
                        this.tickUnit.set(5f)
                        this.value.addListener { 
                            tempoUp.set(it.getOrCompute().toInt())
                        }
                        (this.skin.getOrCompute() as Slider.SliderSkin).also { skin ->
                            val filledColors = listOf(Challenges.TEMPO_DOWN_COLOR, Color(0.24f, 0.74f, 0.94f, 1f), Challenges.TEMPO_UP_COLOR)
                            skin.filledColor.sideEffectingAndRetain { existing -> 
                                val tempo = this@slider.value.use().toInt()
                                existing.set(filledColors[if (tempo < 100) 0 else if (tempo > 100) 2 else 1])
                            }
                        }
                    }
                    this += slider
                    val percent = Localization.getVar("mainMenu.play.challengeSettings.speed.percent", Var {
                        listOf(slider.value.use().toInt())
                    })
                    this += TextLabel(binding = { percent.use() },
                            font = this@LoadSavedLevelMenu.font).apply {
                        this.bounds.width.set(75f)
                        this.textColor.set(LongButtonSkin.TEXT_COLOR)
                        this.renderAlign.set(Align.left)
                        this.setScaleXY(0.9f)
                    }
                }
            }
        }
        content.addChild(challengeSetting)

        contentPane.addChild(content)
        contentPane.addChild(hbox)

        hbox.temporarilyDisableLayouts {
            hbox += createSmallButton(binding = { Localization.getVar("common.cancel").use() }).apply {
                this.bounds.width.set(100f)
                this.visible.bind {
                    when (substate.use()) {
                        is Substate.Loaded, Substate.LoadError -> true
                        else -> false
                    }
                }
                this.setOnAction {
                    loaded?.newContainer?.disposeQuietly()
                    loaded = null
                    menuCol.popLastMenu(playSound = true)
                }
            }
            hbox += createSmallButton(binding = { Localization.getVar("mainMenu.play.playAction").use() }).apply {
                this.bounds.width.set(125f)
                this.visible.bind {
                    when (substate.use()) {
                        is Substate.Loaded -> true
                        else -> false
                    }
                }
                this.disabled.bind {
                    val ss = substate.use()
                    ss is Substate.Loaded && ss.needsFlashingLightsWarning && !flashingLightsAcked.use()
                }
                val lockedTooltip = createTooltip(Localization.getVar("mainMenu.play.flashingLightsWarning.playButtonLocked"))
                val ackedTooltip = createTooltip(Localization.getVar("mainMenu.play.flashingLightsWarning.tooltip"))
                this.tooltipElement.bind {
                    val ss = substate.use()
                    if (disabled.use()) lockedTooltip else if (ss is Substate.Loaded && ss.needsFlashingLightsWarning) ackedTooltip else null
                }
                this.setOnAction {
                    Gdx.input.isCursorCatched = true
                    val loadedData = loaded
                    if (loadedData != null) {
                        val engine = loadedData.newContainer.engine
                        val robotMode = this@LoadSavedLevelMenu.robotMode.get()
                        menuCol.playMenuSound("sfx_menu_enter_game")
                        if (robotMode) {
                            menuCol.playMenuSound("sfx_pause_robot_on")
                            engine.autoInputs = true
                        }
                        
                        // Set challenge settings
                        val challenges: Challenges = Challenges(tempoUp.get(), goForPerfect.get() && !robotMode)
                        challenges.applyToEngine(engine)
                        
                        mainMenu.transitionAway {
                            val main = mainMenu.main
                            val uuid = loadedData.modernLevelUUID
                            val onRankingRevealed = OnRankingRevealed { lsa, score ->
                                GlobalStats.timesPlayedCustomLevel.increment()
                                if (uuid != null) {
                                    val levelScore: LevelScore? = GlobalScoreCache.scoreCache.getOrCompute().map[uuid] 
                                    if (levelScore?.lastPlayed == null) {
                                        GlobalStats.timesPlayedUniqueCustomLevel.increment()
                                    }
                                    
                                    GlobalScoreCache.pushNewLevelScoreAttempt(uuid, lsa)
                                }
                            }
                            val playScreen = EnginePlayScreenBase(main, PlayTimeType.REGULAR, loadedData.newContainer, challenges,
                                    inputCalibration = main.settings.inputCalibration.getOrCompute(),
                                    resultsBehaviour = if (robotMode) ResultsBehaviour.NoResults
                                    else ResultsBehaviour.ShowResults(onRankingRevealed,
                                            previousHighScore?.let { ResultsBehaviour.PreviousHighScore.NumberOnly(it) } ?: ResultsBehaviour.PreviousHighScore.None),
                                    gameMode = null)
                            main.screen = TransitionScreen(main, main.screen, playScreen, null, FadeToTransparent(0.25f, Color(0f, 0f, 0f, 1f))).apply { 
                                this.onEntryEnd = {
                                    playScreen.resetAndUnpause()
                                    menuCol.popLastMenu(playSound = false)
                                    DiscordRichPresence.updateActivity(DefaultPresences.playingLevel())
                                    mainMenu.backgroundType = BgType.NORMAL
                                }
                            }
                        }
                    }
                }
            }

            hbox += CheckBox(binding = { Localization.getVar("mainMenu.play.flashingLightsWarning").use() }, font = font).apply {
                this.bounds.width.set(240f)
                this.textLabel.setScaleXY(0.75f)
                this.imageNode.padding.set(Insets(4f))
                this.checkedState.set(flashingLightsAcked.get())
                this.disabled.bind { flashingLightsAcked.use() }
                this.visible.bind { 
                    val ss = substate.use()
                    ss is Substate.Loaded && ss.needsFlashingLightsWarning
                }
                this.onCheckChanged = {
                    flashingLightsAcked.set(true)
                }
                this.tooltipElement.set(createTooltip(Localization.getVar("mainMenu.play.flashingLightsWarning.tooltip")))
            }
            
            val keyboardKeybindings = main.settings.inputKeymapKeyboard.getOrCompute()
            contentPane += Pane().apply { 
                Anchor.BottomRight.configure(this)
                this.padding.set(Insets(2f))
                this.bounds.width.set(40f)
                this.bounds.height.set(40f)
                this += ImageIcon(TextureRegion(AssetRegistry.get<PackedSheet>("ui_icon_editor")["controls_help"])).apply {
                    this.visible.bind {
                        substate.use() is Substate.Loaded
                    }
                    this.tooltipElement.set(createTooltip {
                        Localization.getValue("mainMenu.play.controlsTooltip", "${Localization.getValue("mainMenu.inputSettings.keyboard.keybindPause")}: ${Input.Keys.toString(Input.Keys.ESCAPE)}/${Input.Keys.toString(keyboardKeybindings.pause)} | ${keyboardKeybindings.toKeyboardString(false, true)}")
                    })
                }
            }
        }
    }

    init { // This init block should be LAST
        if (immediateLoad != null) {
            Gdx.app.postRunnable {
                thread(isDaemon = true) {
                    try {
                        loadFile(immediateLoad)
                    } catch (e: Exception) {
                        Gdx.app.postRunnable { 
                            throw e
                        }
                    }
                }
            }
        } else {
            Gdx.app.postRunnable {
                main.restoreForExternalDialog { completionCallback ->
                    thread(isDaemon = true) {
                        val title = Localization.getValue("fileChooser.load.level.title")
                        val filter = FileExtFilter(Localization.getValue("fileChooser.load.level.filter"),
                                listOf("*.${Container.LEVEL_FILE_EXTENSION}")).copyWithExtensionsInDesc()
                        TinyFDWrapper.openFile(title,
                                main.attemptRememberDirectory(PreferenceKeys.FILE_CHOOSER_PLAY_SAVED_LEVEL)
                                        ?: main.getDefaultDirectory(), filter) { file: File? ->
                            completionCallback()
                            if (file != null) {
                                try {
                                    loadFile(file)
                                } catch (e: Exception) {
                                    Gdx.app.postRunnable {
                                        throw e
                                    }
                                }
                            } else { // Cancelled out
                                Gdx.app.postRunnable {
                                    menuCol.popLastMenu(playSound = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * To be called in the SAME thread that opened the file chooser.
     */
    private fun loadFile(newFile: File) {
        Gdx.app.postRunnable {
            descLabel.doLineWrapping.set(false)
            descLabel.text.set(Localization.getValue("editor.dialog.load.loading"))
            substate.set(Substate.Loading)
        }

        val newSoundSystem: SoundSystem = SoundSystem.createDefaultSoundSystem().apply {
            this.audioContext.out.gain = main.settings.gameplayVolume.getOrCompute() / 100f
        }
        val newContainer: Container = Container(newSoundSystem, SimpleTimingProvider {
            Gdx.app.postRunnable {
                throw it
            }
            true
        }, GlobalContainerSettings(main.settings.forceTexturePack.getOrCompute(), main.settings.forceTilesetPalette.getOrCompute(), main.settings.reducedMotion.getOrCompute()))

        try {
            val loadMetadata = newContainer.readFromFile(newFile, EnumSet.noneOf(EditorSpecialFlags::class.java))

            if (newContainer.blocks.none { it is BlockEndState }) {
                Gdx.app.postRunnable {
                    substate.set(Substate.LoadError)
                    descLabel.doLineWrapping.set(true)
                    descLabel.text.set(Localization.getValue("mainMenu.play.noEndState",
                            Localization.getValue(Instantiators.endStateInstantiator.name.getOrCompute())))
                    newContainer.disposeQuietly()
                }
            } else if (loadMetadata.isFutureVersion) {
                Gdx.app.postRunnable {
                    substate.set(Substate.LoadError)
                    descLabel.doLineWrapping.set(true)
                    descLabel.text.set(Localization.getValue("editor.dialog.load.error.futureVersion", loadMetadata.programVersion.toString(), "${loadMetadata.containerVersion}"))
                    newContainer.disposeQuietly()
                }
            } else {
                Gdx.app.postRunnable {
                    substate.set(Substate.Loaded(loadMetadata, loadMetadata.libraryRelevantData.levelMetadata?.flashingLightsWarning == true))
                    flashingLightsAcked.set(false)
                    descLabel.text.set(Localization.getValue("editor.dialog.load.loadedInformation", loadMetadata.programVersion, "${loadMetadata.containerVersion}"))
                    loadMetadata.loadOnGLThread()
                    loaded = LoadData(newContainer, loadMetadata, loadMetadata.libraryRelevantData.levelUUID)

                    val newInitialDirectory = if (!newFile.isDirectory) newFile.parentFile else newFile
                    main.persistDirectory(PreferenceKeys.FILE_CHOOSER_PLAY_SAVED_LEVEL, newInitialDirectory)
                    challengeSetting.visible.set(true)
                }
            }
        } catch (e: Exception) {
            Paintbox.LOGGER.warn("Error occurred while loading container:")
            e.printStackTrace()
            val exClassName = e.javaClass.name
            Gdx.app.postRunnable {
                substate.set(Substate.LoadError)
                descLabel.doLineWrapping.set(true)
                descLabel.setScaleXY(0.75f)
                descLabel.text.set(Localization.getValue("editor.dialog.load.loadError", exClassName))
                newContainer.disposeQuietly()
            }
        }
    }

    data class LoadData(val newContainer: Container, val loadMetadata: Container.LoadMetadata, val modernLevelUUID: UUID?)
}