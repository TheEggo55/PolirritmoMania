package polyrhythmmania.storymode.gamemode.boss

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import paintbox.binding.BooleanVar
import polyrhythmmania.PRManiaGame
import polyrhythmmania.container.GlobalContainerSettings
import polyrhythmmania.editor.block.Block
import polyrhythmmania.editor.block.BlockType
import polyrhythmmania.editor.block.GenericBlock
import polyrhythmmania.engine.Event
import polyrhythmmania.engine.tempo.TempoChange
import polyrhythmmania.gamemodes.CanPreventPausing
import polyrhythmmania.gamemodes.ChangeMusicVolMultiplierEvent
import polyrhythmmania.gamemodes.GameMode
import polyrhythmmania.storymode.contract.Contract
import polyrhythmmania.storymode.gamemode.AbstractStoryGameMode
import polyrhythmmania.storymode.gamemode.boss.pattern.BossPatternPools
import polyrhythmmania.storymode.gamemode.boss.scripting.*
import polyrhythmmania.storymode.music.StemCache
import polyrhythmmania.storymode.music.StoryMusicAssets
import polyrhythmmania.world.World
import polyrhythmmania.world.WorldMode
import polyrhythmmania.world.WorldType
import polyrhythmmania.world.entity.Entity
import polyrhythmmania.world.entity.EntityCube
import polyrhythmmania.world.render.ForceTexturePack
import java.util.*


class StoryBossGameMode(main: PRManiaGame, val debugPhase: DebugPhase = DebugPhase.NONE) :
    AbstractStoryGameMode(main), World.WorldResetListener, CanPreventPausing {

    companion object {

        private const val INTRO_CARD_TIME_SEC: Float = 2.5f // Duration of intro segment
        const val BPM: Float = 186f
        val BOSS_POSITION: Vector3 = Vector3(5 + 11f, 1f + (14 / 32f), -3f)

        fun getFactory(debugPhase: DebugPhase = DebugPhase.NONE): Contract.GamemodeFactory =
            object : Contract.GamemodeFactory {
                private var firstCall = true

                override fun load(delta: Float, main: PRManiaGame): GameMode? {
                    return if (firstCall) {
                        firstCall = false
                        StoryMusicAssets.initBossStems()
                        null
                    } else {
                        val bossStems = StoryMusicAssets.bossStems
                        val keys = bossStems.keys
                        val ready = keys.all { key ->
                            val stem = bossStems.getOrLoad(key)
                            stem?.musicFinishedLoading?.get() ?: false
                        }

                        if (ready) StoryBossGameMode(main, debugPhase) else null
                    }
                }
            }
    }

    enum class DebugPhase {
        NONE,
        A1,
        A2,
        B1,
        B2,
        C,
        C_VAR1,
        C_VAR2,
        C_VAR3,
        D,
        D_VAR1,
        D_VAR2,
        D_VAR3,
        E1,
        E2,
        F,
        F_VAR1,
        F_VAR2,
        F_VAR3,
    }

    private val checkForRodsThatCollidedWithBossRunnable = CheckForRodsThatCollidedWithBossRunnable()

    val stems: StemCache = StoryMusicAssets.bossStems
    val modifierModule: BossModifierModule

    val random: Random = Random()
    val patternPools: BossPatternPools = BossPatternPools(random)

    private var alreadySawLightsIntro: Boolean =
        false // Set to true once lights intro shown, for the lifetime of this GameMode

    override val preventPausing: BooleanVar = BooleanVar(false)
    private var wasBossDefeatedTriggered: Boolean = false

    init {
        world.worldMode = WorldMode(WorldType.Polyrhythm(showRaisedPlatformsRepeated = false))
        world.showInputFeedback = true
        world.worldResetListeners += this as World.WorldResetListener

        modifierModule = BossModifierModule(engine.modifiers)
        engine.modifiers.addModifierModule(modifierModule)

        engine.postRunnable(checkForRodsThatCollidedWithBossRunnable)

        modifierModule.bossHealth.currentHP.addListener { hpVar ->
            if (hpVar.getOrCompute() == 0 && !wasBossDefeatedTriggered) {
                wasBossDefeatedTriggered = true

                Gdx.app.postRunnable {
                    onBossDefeated()
                }
            }
        }
    }

    private fun shouldShowLightsIntro(): Boolean = debugPhase == DebugPhase.NONE && !alreadySawLightsIntro

    override fun initialize() {
        engine.tempos.addTempoChange(TempoChange(0f, BPM))
        addInitialBlocks()
    }

    override fun onWorldReset(world: World) {
        val list = mutableListOf<Entity>()

        // Extra blocks due to more extreme zoom-out
        list.addAll(createExtraBlockEntities())
        
        list += EntityBossRobotUpside(world, this, BOSS_POSITION)
        list += EntityBossRobotMiddle(world, this, BOSS_POSITION)
        list += EntityBossRobotFace(world, this, BOSS_POSITION)
        list += EntityBossRobotDownside(world, this, BOSS_POSITION)

        list.forEach(world::addEntity)

        patternPools.allPools.forEach { pool ->
            pool.resetAndShuffle()
        }

        setAmbientLightToBlack(fullDarkness = shouldShowLightsIntro())
        wasBossDefeatedTriggered = false
    }

    private fun setAmbientLightToBlack(fullDarkness: Boolean) {
        val ambientLight = world.spotlights.ambientLight
        ambientLight.color.set(Color.BLACK)
        ambientLight.strength = if (fullDarkness) 0f else (20 / 255f)
    }

    private fun createExtraBlockEntities(): List<Entity> {
        val list = mutableListOf<Entity>()

        fun addCube(x: Int, y: Int, z: Int) {
            list += EntityCube(world, false).apply {
                this.position.set(x.toFloat(), y.toFloat(), z.toFloat())
            }
        }

        for (x in 8..15) addCube(x, 3, -11)
        for (x in 8..18) addCube(x, 3, -12)
        for (x in 9..17) addCube(x, 3, -13)
        for (x in 10..16) addCube(x, 3, -14)
        for (x in 11..15) addCube(x, 3, -15)

        for (x in 11..14) addCube(x, 0, 10)
        for (x in 12..13) addCube(x, 0, 11)

//        for (x in 8..13) {
//            for (z in -12 downTo -13) {
//                list += EntityCube(world, false).apply {
//                    this.position.set(x.toFloat(), 3f, z.toFloat())
//                }
//            }
//        }

        return list
    }

    private fun addInitialBlocks() {
        val blocks = mutableListOf<Block>()

        blocks += InitScriptBlock()

        blocks += GenericBlock(engine, true) {
            listOf(MarkLightsIntroAsSeenEvent())
        }.apply {
            this.beat = 17f
        }

        container.addBlocks(blocks)
    }

    private fun onBossDefeated() {
        preventPausing.set(true)

        engine.inputter.areInputsLocked = true
        val currentEvents = engine.events.toList()
        val eventsToDelete = currentEvents.filterNot { evt ->
            // Boss music that already started playing is exempt because it has to keep updating the player volume
            (evt is BossMusicEvent && evt.audioUpdateCompletion != Event.UpdateCompletion.PENDING) 
        }
        engine.removeEvents(eventsToDelete)

        val currentBeat = engine.beat
        val currentSec = engine.seconds

        val silentAfterSec = 0.5f
        val silentAtBeat = engine.tempos.secondsToBeats(currentSec + silentAfterSec, disregardSwing = true)
        engine.addEvent(ChangeMusicVolMultiplierEvent(engine, 1f, 0f, currentBeat, silentAtBeat - currentBeat))

        val script = Script(currentBeat, this@StoryBossGameMode, 1f)
        val bossScriptEnd = BossScriptEnd(this, script)
        script.addEventsToQueue(bossScriptEnd.getEvents())
        engine.addEvent(script)
    }


    private inner class CheckForRodsThatCollidedWithBossRunnable : Runnable {

        var cancel: Boolean = false

        override fun run() {
            modifierModule.checkForRodsThatCollidedWithBoss()
            if (!cancel) {
                engine.postRunnable(this)
            }
        }
    }

    private inner class MarkLightsIntroAsSeenEvent : Event(engine) {

        override fun onStart(currentBeat: Float) {
            Gdx.app.postRunnable {
                alreadySawLightsIntro = true
            }
        }
    }

    //region GameMode overrides

    override fun getIntroCardTimeOverride(): Float {
        return 0f
    }

    override fun getSecondsToDelayAtStartOverride(): Float {
        return if (shouldShowLightsIntro()) {
            0f
        } else {
            // This fixes negative audioOffset causing desyncing at the start (issue #53)
            ((engine.inputCalibration.audioOffsetMs / 1000f * -1) + 0.1f).coerceIn(0f, 1f)
        }
    }

    override fun shouldPauseWhileInIntroCardOverride(): Boolean {
        return false
    }

    override fun createGlobalContainerSettings(): GlobalContainerSettings {
        return super.createGlobalContainerSettings().copy(
            forceTexturePack = ForceTexturePack.FORCE_GBA,
            reducedMotion = false,
            numberOfSpotlightsOverride = 16
        )
    }

    //endregion

    //region Init blocks

    private fun onMainScriptCreated(script: Script) {
        val phase1Factory =
            when (this.debugPhase) {
                DebugPhase.NONE -> null
                DebugPhase.A1 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(BossScriptPhase1A1(it))
                    }

                DebugPhase.A2 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(BossScriptPhase1A2(it))
                    }

                DebugPhase.B1 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(BossScriptPhase1B1(it))
                    }

                DebugPhase.B2 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(BossScriptPhase1B2(it))
                    }

                DebugPhase.C -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1C(it, variantIndex = 0),
                            BossScriptPhase1C(it, variantIndex = 1),
                            BossScriptPhase1C(it, variantIndex = 2),
                        )
                    }

                DebugPhase.C_VAR1 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1C(it, variantIndex = 0),
                        )
                    }

                DebugPhase.C_VAR2 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1C(it, variantIndex = 1),
                        )
                    }

                DebugPhase.C_VAR3 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1C(it, variantIndex = 2),
                        )
                    }

                DebugPhase.D -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1D(it, variantIndex = 0),
                            BossScriptPhase1D(it, variantIndex = 1),
                            BossScriptPhase1D(it, variantIndex = 2),
                        )
                    }

                DebugPhase.D_VAR1 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1D(it, variantIndex = 0),
                        )
                    }

                DebugPhase.D_VAR2 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1D(it, variantIndex = 1),
                        )
                    }

                DebugPhase.D_VAR3 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1D(it, variantIndex = 2),
                        )
                    }

                DebugPhase.E1 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1E1(it)
                        )
                    }

                DebugPhase.E2 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1E2(it)
                        )
                    }

                DebugPhase.F -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1F(it, variantIndex = 0),
                            BossScriptPhase1F(it, variantIndex = 1),
                            BossScriptPhase1F(it, variantIndex = 2),
                        )
                    }

                DebugPhase.F_VAR1 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1F(it, variantIndex = 0),
                        )
                    }

                DebugPhase.F_VAR2 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1F(it, variantIndex = 1),
                        )
                    }

                DebugPhase.F_VAR3 -> fun(intro: BossScriptIntro) =
                    BossScriptPhase1DebugLoop(intro.gamemode, intro.script) {
                        listOf(
                            BossScriptPhase1F(it, variantIndex = 2),
                        )
                    }
            }
        val func: ScriptFunction = BossScriptIntro(
            this,
            script,
            shouldShowLightsIntro(),
            phase1Factory
        )
        script.addEventsToQueue(func.getEvents())
    }

    private abstract inner class AbstractBlock : Block(engine, EnumSet.allOf(BlockType::class.java)) {

        final override fun copy(): Block = throw NotImplementedError()
    }

    private inner class InitScriptBlock : AbstractBlock() {

        override fun compileIntoEvents(): List<Event> {
            val script = Script(0f, this@StoryBossGameMode, 5f) // 1 + 4, 4 for deploy rods

            this@StoryBossGameMode.onMainScriptCreated(script)

            return listOf(script)
        }
    }

    //endregion
}
