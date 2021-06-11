package polyrhythmmania.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.Screen
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import paintbox.PaintboxGame
import paintbox.binding.Var
import paintbox.font.TextAlign
import paintbox.packing.PackedSheet
import paintbox.registry.AssetRegistry
import paintbox.transition.FadeIn
import paintbox.transition.FadeOut
import paintbox.transition.TransitionScreen
import paintbox.ui.*
import paintbox.ui.area.Insets
import paintbox.ui.border.SolidBorder
import paintbox.ui.control.Button
import paintbox.ui.control.ButtonSkin
import paintbox.ui.control.TextLabel
import paintbox.ui.element.RectElement
import paintbox.ui.layout.VBox
import paintbox.ui.skin.Skin
import paintbox.ui.skin.SkinFactory
import paintbox.util.MathHelper
import paintbox.util.gdxutils.*
import paintbox.util.sumOfFloat
import polyrhythmmania.Localization
import polyrhythmmania.PRManiaGame
import polyrhythmmania.PRManiaScreen
import polyrhythmmania.container.Container
import polyrhythmmania.engine.Engine
import polyrhythmmania.engine.input.InputKeymapKeyboard
import polyrhythmmania.engine.input.InputType
import polyrhythmmania.screen.mainmenu.menu.TemporaryResultsMenu
import polyrhythmmania.soundsystem.SimpleTimingProvider
import polyrhythmmania.soundsystem.SoundSystem
import polyrhythmmania.soundsystem.TimingProvider
import polyrhythmmania.world.EntityRod
import polyrhythmmania.world.render.WorldRenderer
import space.earlygrey.shapedrawer.ShapeDrawer
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.roundToLong


class PlayScreen(main: PRManiaGame, val container: Container)
    : PRManiaScreen(main) {

    val timing: TimingProvider get() = container.timing
    val soundSystem: SoundSystem
        get() = container.soundSystem ?: error("PlayScreen requires a non-null SoundSystem in the Container")
    val engine: Engine get() = container.engine
    val renderer: WorldRenderer get() = container.renderer
    val batch: SpriteBatch = main.batch

    private val uiCamera: OrthographicCamera = OrthographicCamera().apply {
        this.setToOrtho(false, 1280f, 720f)
        this.update()
    }
    private val sceneRoot: SceneRoot = SceneRoot(uiCamera)
    private val inputProcessor: InputProcessor = sceneRoot.inputSystem
    private val shapeDrawer: ShapeDrawer = ShapeDrawer(batch, PaintboxGame.paintboxSpritesheet.fill)
    private val maxSelectionSize: Int = 3
    private val selectionIndex: Var<Int> = Var(0)
    private val resumeLabel: TextLabel
    private val startOverLabel: TextLabel
    private val quitLabel: TextLabel

    private var isPaused: Boolean = false
    private var isFinished: Boolean = false
    private val pauseBg: PauseBackground by lazy { this.PauseBackground() }

    private val keyboardKeybinds: InputKeymapKeyboard by lazy { main.settings.inputKeymapKeyboard.getOrCompute() }
    private val bgSquareTexReg: TextureRegion = TextureRegion(AssetRegistry.get<Texture>("pause_square"))

    init {
        var nextLayer: UIElement = sceneRoot
        fun addLayer(element: UIElement) {
            nextLayer += element
            nextLayer = element
        }
        addLayer(RectElement(Color(0f, 0f, 0f, 0f)))

        val paddingPane = Pane().apply {
            this.padding.set(Insets(36f, 36f / 4f, 64f, 64f / 4f))
        }
        addLayer(paddingPane)

        val leftVbox = VBox().apply {
            this.spacing.set(16f)
            this.bounds.height.set(300f)
        }
        nextLayer += leftVbox

        leftVbox.temporarilyDisableLayouts {
            leftVbox += TextLabel(binding = { Localization.getVar("play.pause.title").use() }, font = main.fontPauseMenuTitle).apply {
                this.textColor.set(Color.WHITE)
                this.bounds.height.set(128f)
                this.bindWidthToParent(multiplier = 0.5f)
                this.renderAlign.set(Align.left)
            }
        }

        val transparentBlack = Color(0f, 0f, 0f, 0.75f)
        sceneRoot += TextLabel(keyboardKeybinds.toKeyboardString(true), font = main.fontMainMenuRodin).apply {
            Anchor.BottomRight.configure(this)
            this.textColor.set(Color.WHITE)
            this.bounds.width.set(550f)
            this.bounds.height.set(80f)
            this.bgPadding.set(Insets(12f))
            this.renderAlign.set(Align.bottomRight)
            this.textAlign.set(TextAlign.LEFT)
            this.backgroundColor.set(transparentBlack)
            this.renderBackground.set(true)
        }

        val optionsBorderSize = 12f
        val optionsBg = RectElement(transparentBlack).apply {
            Anchor.BottomRight.configure(this, offsetY = -75f)
            this.bounds.width.set(275f + optionsBorderSize * 2)
            this.bounds.height.set(144f + optionsBorderSize * 2)
            this.border.set(Insets(optionsBorderSize))
            this.borderStyle.set(SolidBorder(transparentBlack).apply {
                this.roundedCorners.set(true)
            })
        }
        paddingPane += optionsBg
        fun addArrowImageNode(index: Int): ArrowNode {
            return ArrowNode(TextureRegion(/*AssetRegistry.get<Texture>("pause_rod")*/ AssetRegistry.get<PackedSheet>("ui_icon_editor")["arrow_instantiator_right"])).apply {
                Anchor.CentreLeft.configure(this)
//                this.bindHeightToParent(multiplier = 1.5f)
                this.bounds.height.set(64f)
                this.bounds.width.bind { bounds.height.use() }
                this.bounds.x.bind { -(bounds.width.use() + optionsBorderSize) }
                this.visible.bind { selectionIndex.use() == index }
            }
        }

        val selectedLabelColor = Color(0f, 1f, 1f, 1f)
        val unselectedLabelColor = Color(1f, 1f, 1f, 1f)
        fun createTextLabelOption(localiz: String, index: Int): TextLabel {
            return TextLabel(binding = { Localization.getVar(localiz).use() }, font = main.fontMainMenuMain).apply {
                Anchor.TopLeft.configure(this)
                this.textColor.bind {
                    if (selectionIndex.use() == index) selectedLabelColor else unselectedLabelColor
                }
                this.bounds.height.set(48f)
                this.bgPadding.set(Insets(2f, 2f, 12f, 12f))
                this.renderAlign.set(Align.left)
                this.textAlign.set(TextAlign.LEFT)
                this += addArrowImageNode(index)
                this.setOnAction {
                    attemptPauseEntrySelection()
                }
                this.setOnHoverStart {
                    changeSelectionTo(index)
                }
            }
        }
        resumeLabel = createTextLabelOption("play.pause.resume", 0)
        startOverLabel = createTextLabelOption("play.pause.startOver", 1)
        quitLabel = createTextLabelOption("play.pause.quitToMainMenu", 2)
        optionsBg += VBox().apply {
            this.spacing.set(0f)
            this.temporarilyDisableLayouts {
                this += resumeLabel
                this += startOverLabel
                this += quitLabel
            }
        }
    }

    init {
        engine.endSignalReceived.addListener {
            Gdx.app.postRunnable {
                soundSystem.setPaused(true)
                container.world.entities.filterIsInstance<EntityRod>().forEach { rod ->
                    engine.inputter.submitInputsFromRod(rod)
                }
                transitionToResults()
            }
        }
    }

    fun startGame() {
        timing.seconds = 0f
        val player = engine.soundInterface.getCurrentMusicPlayer(engine.musicData.beadsMusic)
        if (player != null) {
            engine.musicData.setPlayerPositionToCurrentSec()
            player.pause(false)
        }
        engine.autoInputs = false
        engine.inputter.areInputsLocked = false // FIXME may need better input locking mechanism later
        engine.inputter.clearInputs()
        engine.resetEndSignal()

        engine.endSignalReceived.addListener { endSignal ->
            if (endSignal.getOrCompute()) {
                Gdx.app.postRunnable {
                    isFinished = true
                }
            }
        }

        soundSystem.startRealtime()
        unpauseGame(false)
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        val batch = this.batch
        renderer.render(batch, engine)

        val camera = uiCamera
        batch.projectionMatrix = camera.combined
        batch.begin()

        if (isPaused) {
            val width = camera.viewportWidth
            val height = camera.viewportHeight
            val shapeRenderer = main.shapeRenderer
            shapeRenderer.projectionMatrix = camera.combined

            batch.setColor(1f, 1f, 1f, 0.5f)
            batch.fillRect(0f, 0f, width, height)
            batch.setColor(1f, 1f, 1f, 1f)

            val pauseBg = this.pauseBg

            val topLeftX1 = 0f
            val topLeftY1 = height * pauseBg.topTriangleY
            val topLeftX2 = 0f
            val topLeftY2 = height
            val topLeftY3 = height
            val topLeftX3 = topLeftY1 + (topLeftY3 - topLeftY2) * (1f / pauseBg.triangleSlope)
            val botRightX1 = width * pauseBg.botTriangleX
            val botRightY1 = 0f
            val botRightX2 = width
            val botRightY2 = 0f
            val botRightX3 = width
            val botRightY3 = botRightY1 + (botRightX3 - botRightX1) * (pauseBg.triangleSlope)
            val triLineWidth = 12f
            shapeRenderer.prepareStencilMask(batch) {
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                shapeRenderer.triangle(topLeftX1, topLeftY1, topLeftX2, topLeftY2, topLeftX3, topLeftY3)
                shapeRenderer.triangle(botRightX1, botRightY1, botRightX2, botRightY2, botRightX3, botRightY3)
                shapeRenderer.end()
            }.useStencilMask {
                batch.setColor(1f, 1f, 1f, 1f)
                pauseBg.render(delta, batch, camera)
                batch.setColor(1f, 1f, 1f, 1f)
            }

            // Draw lines to hide aliasing
            val shapeDrawer = this.shapeDrawer
            shapeDrawer.setColor(0f, 0f, 0f, 1f)
            shapeDrawer.line(topLeftX1 - triLineWidth, topLeftY1 - triLineWidth * pauseBg.triangleSlope,
                    topLeftX3 + triLineWidth, topLeftY3 + triLineWidth * pauseBg.triangleSlope, triLineWidth, false)
            shapeDrawer.line(botRightX1 - triLineWidth, botRightY1 - triLineWidth * pauseBg.triangleSlope,
                    botRightX3 + triLineWidth, botRightY3 + triLineWidth * pauseBg.triangleSlope, triLineWidth, false)
            shapeDrawer.setColor(1f, 1f, 1f, 1f)
            batch.setColor(1f, 1f, 1f, 1f)

            batch.flush()
            shapeRenderer.projectionMatrix = main.nativeCamera.combined

            sceneRoot.renderAsRoot(batch)
        }

        batch.end()
        batch.projectionMatrix = main.nativeCamera.combined

        super.render(delta)
    }

    override fun renderUpdate() {
        super.renderUpdate()

        if (!isPaused && timing is SimpleTimingProvider) {
            timing.seconds += Gdx.graphics.deltaTime
        }
    }

    private fun transitionToResults() {
        val inputter = engine.inputter
        val nInputs = inputter.totalExpectedInputs
        val score = if (nInputs <= 0) 0f else ((inputter.inputResults.map { it.inputScore }.sumOfFloat { inputScore ->
            inputScore.weight
        } / nInputs) * 100)
        val results = TemporaryResultsMenu.Results(nInputs, score.roundToInt().coerceIn(0, 100), inputter.inputResults)

        val mainMenu = main.mainMenuScreen.prepareShow(doFlipAnimation = true)
        val menuCol = mainMenu.menuCollection
        val tmpResultsMenu = TemporaryResultsMenu(menuCol, results)
        menuCol.addMenu(tmpResultsMenu)
        menuCol.pushNextMenu(tmpResultsMenu, instant = true)
        transitionAway(mainMenu) {}
    }

    private inline fun transitionAway(nextScreen: Screen, action: () -> Unit) {
        isFinished = true
        main.inputMultiplexer.removeProcessor(inputProcessor)
        Gdx.input.isCursorCatched = false

        action.invoke()

        main.screen = TransitionScreen(main, this, nextScreen,
                FadeOut(0.5f, Color(0f, 0f, 0f, 1f)), FadeIn(0.125f, Color(0f, 0f, 0f, 1f))).apply {
            this.onEntryEnd = {
                this@PlayScreen.dispose()
            }
        }
    }

    private fun pauseGame(playSound: Boolean) {
        isPaused = true
        soundSystem.setPaused(true)
        Gdx.input.isCursorCatched = false
        main.inputMultiplexer.removeProcessor(inputProcessor)
        main.inputMultiplexer.addProcessor(inputProcessor)
        selectionIndex.set(0)
        if (playSound) {
            playMenuSound("sfx_pause_enter")
        }
    }

    private fun unpauseGame(playSound: Boolean) {
        isPaused = false
        soundSystem.setPaused(false)
        Gdx.input.isCursorCatched = true
        main.inputMultiplexer.removeProcessor(inputProcessor)
        if (playSound) {
            playMenuSound("sfx_pause_exit")
        }
    }

    private fun resetAndStartOver(playSound: Boolean = true) {
        val blocks = container.blocks.toList()
        engine.removeEvents(engine.events.toList())
        engine.addEvents(blocks.flatMap { it.compileIntoEvents() })
        container.world.resetWorld()
        Gdx.app.postRunnable {
            if (playSound) {
                playMenuSound("sfx_menu_enter_game")
            }
            startGame()
        }
    }

    private fun attemptPauseEntrySelection() {
        when (selectionIndex.getOrCompute()) {
            0 -> { // Resume
                unpauseGame(true)
            }
            1 -> { // Start Over
                resetAndStartOver()
            }
            2 -> { // Quit to Main Menu
                val main = this@PlayScreen.main
                val currentScreen = main.screen
                Gdx.app.postRunnable {
                    val mainMenu = main.mainMenuScreen.prepareShow(doFlipAnimation = true)
                    main.screen = TransitionScreen(main, currentScreen, mainMenu,
                            FadeOut(0.25f, Color(0f, 0f, 0f, 1f)), null).apply {
                        this.onEntryEnd = {
                            if (currentScreen is PlayScreen) {
                                currentScreen.dispose()
                            }
                        }
                    }
                }
                Gdx.app.postRunnable {
                    playMenuSound("sfx_pause_exit")
                }
            }
        }
    }
    
    private fun changeSelectionTo(index: Int) {
        if (selectionIndex.getOrCompute() != index) {
            selectionIndex.set(index)
            playMenuSound("sfx_menu_blip")
        }
    }

    override fun keyDown(keycode: Int): Boolean {
        var consumed = false
        if (!isFinished) {
            if (isPaused) {
                when (keycode) {
                    Input.Keys.ESCAPE -> {
                        unpauseGame(true)
                        consumed = true
                    }
                    keyboardKeybinds.buttonDpadUp -> {
                        val nextIndex = (selectionIndex.getOrCompute() - 1 + maxSelectionSize) % maxSelectionSize
                        changeSelectionTo(nextIndex)
                        consumed = true
                    }
                    keyboardKeybinds.buttonDpadDown -> {
                        val nextIndex = (selectionIndex.getOrCompute() + 1) % maxSelectionSize
                        changeSelectionTo(nextIndex)
                        consumed = true
                    }
                    keyboardKeybinds.buttonA -> {
                        attemptPauseEntrySelection()
                        consumed = true
                    }
                }
            } else {
                val atSeconds = engine.seconds
                when (keycode) {
                    Input.Keys.ESCAPE -> {
                        pauseGame(true)
                        consumed = true
                    }
                    keyboardKeybinds.buttonDpadUp, keyboardKeybinds.buttonDpadDown,
                    keyboardKeybinds.buttonDpadLeft, keyboardKeybinds.buttonDpadRight -> {
                        engine.postRunnable {
                            engine.inputter.onInput(InputType.DPAD, atSeconds)
                        }
                        consumed = true
                    }
                    keyboardKeybinds.buttonA -> {
                        engine.postRunnable {
                            engine.inputter.onInput(InputType.A, atSeconds)
                        }
                        consumed = true
                    }
                }
            }
        }

        return consumed || super.keyDown(keycode)
    }

    private fun playMenuSound(id: String, volume: Float = 1f, pitch: Float = 1f, pan: Float = 0f): Pair<Sound, Long> {
        val sound: Sound = AssetRegistry[id]
        val menuSFXVol = main.settings.menuSfxVolume.getOrCompute() / 100f
        val soundID = sound.play(menuSFXVol * volume, pitch, pan)
        return sound to soundID
    }

    override fun show() {
        super.show()
        startGame()
        Gdx.input.isCursorCatched = true
    }

    override fun hide() {
        super.hide()
        Gdx.input.isCursorCatched = false
        main.inputMultiplexer.removeProcessor(inputProcessor)
    }

    override fun dispose() {
        container.disposeQuietly()
    }

    override fun getDebugString(): String {
        return """SoundSystem: paused=${soundSystem.isPaused}
TimingBead: ${soundSystem.seconds}
---
${engine.getDebugString()}
---
${renderer.getDebugString()}
---
${sceneRoot.mainLayer.lastHoveredElementPath.map { it.javaClass.simpleName }}
"""
    }

    inner class PauseBackground {
        private val seed = Random().nextInt(255)
        private val hsv: FloatArray = FloatArray(3)
        var cycleSpeed: Float = 1 / 30f
        val triangleSlope: Float = 1 / 2f
        val topTriangleY: Float = 2 / 3f
        val botTriangleX: Float = 1 / 3f
        private val topColor: Color = Color.valueOf("4048e0")
        private val bottomColor: Color = Color.valueOf("d020a0")

        fun render(delta: Float, batch: SpriteBatch, camera: OrthographicCamera) {
            val width = camera.viewportWidth
            val height = camera.viewportHeight

            if (cycleSpeed > 0f) {
                topColor.toHsv(hsv)
                hsv[0] = (hsv[0] - delta * cycleSpeed * 360f) % 360f
                topColor.fromHsv(hsv)
                bottomColor.toHsv(hsv)
                hsv[0] = (hsv[0] - delta * cycleSpeed * 360f) % 360f
                bottomColor.fromHsv(hsv)
            }

            batch.drawQuad(0f, 0f, bottomColor, width, 0f, bottomColor,
                    width, height, topColor, 0f, height, topColor)
            batch.setColor(1f, 1f, 1f, 1f)

            // Squares
            val squareCount = 90
            batch.setColor(1f, 1f, 1f, 0.65f)
            for (i in 0 until squareCount) {
                val alpha = i / squareCount.toFloat()
                val size = Interpolation.circleIn.apply(20f, 80f, alpha) * 1.5f
                val rotation = MathHelper.getSawtoothWave(System.currentTimeMillis() + (273L * alpha * 2).roundToLong(),
                        Interpolation.circleOut.apply(0.65f, 1.15f, alpha) * 0.75f) * (if (i % 2 == 0) -1 else 1)

                val yInterval = Interpolation.circleOut.apply(8f, 5f, alpha)
                val yAlpha = 1f - MathHelper.getSawtoothWave(System.currentTimeMillis() + (562L * alpha * 2).roundToLong(), yInterval)
                val x = MathUtils.lerp(width * -0.1f, width * 1.1f, yAlpha)
                val y = (width * 1.41421356f * (i + 23) * (alpha + seed) + (yAlpha * yInterval).roundToInt()) % (width * 1.25f)

                drawSquare(batch, x - size / 2, y - size / 2, rotation * 360f, size)
            }

            batch.setColor(1f, 1f, 1f, 1f)
        }

        private fun drawSquare(batch: SpriteBatch, x: Float, y: Float, rot: Float, size: Float) {
            val width = size
            val height = size
            batch.draw(bgSquareTexReg, x - width / 2, y - height / 2, width / 2, height / 2, width, height, 1f, 1f, rot)
        }
    }

    class ArrowNode(val tex: TextureRegion) : UIElement() {
        override fun renderSelf(originX: Float, originY: Float, batch: SpriteBatch) {
            val renderBounds = this.contentZone
            val x = renderBounds.x.getOrCompute() + originX
            val y = originY - renderBounds.y.getOrCompute()
            val w = renderBounds.width.getOrCompute()
            val h = renderBounds.height.getOrCompute()
            val offsetXMax = (w * 0.35f)
            val offsetX = (MathHelper.getSawtoothWave(1f) * 4f).coerceIn(0f, 1f) * offsetXMax
            batch.draw(tex, x + offsetX - offsetXMax, y - h,
                    0.5f * w, 0.5f * h,
                    w, h, 1f, 1f, 0f)
        }
    }
}