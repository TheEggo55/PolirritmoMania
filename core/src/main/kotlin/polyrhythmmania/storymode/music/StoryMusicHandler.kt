package polyrhythmmania.storymode.music

import com.badlogic.gdx.Gdx
import net.beadsproject.beads.core.AudioContext
import net.beadsproject.beads.core.Bead
import net.beadsproject.beads.core.UGen
import net.beadsproject.beads.ugens.*
import paintbox.Paintbox
import paintbox.binding.FloatVar
import paintbox.binding.ReadOnlyFloatVar
import polyrhythmmania.PRManiaGame
import polyrhythmmania.engine.tempo.TempoUtils
import polyrhythmmania.soundsystem.SoundSystem
import polyrhythmmania.soundsystem.sample.InMemoryMusicSample
import polyrhythmmania.soundsystem.sample.MusicSample
import polyrhythmmania.soundsystem.sample.MusicSamplePlayer
import polyrhythmmania.storymode.StorySession
import polyrhythmmania.storymode.contract.Contracts
import polyrhythmmania.storymode.inbox.InboxDB
import polyrhythmmania.storymode.inbox.InboxItem
import polyrhythmmania.storymode.inbox.InboxState


class StoryMusicHandler(val storySession: StorySession) {

    companion object {

        const val LOOP_SAMPLES_START: Int = 0
        const val LOOP_SAMPLES_END: Int = 2_373_981
        const val BPM: Float = 107f
        const val DURATION_BEATS: Float = 96f

    }

    private class StemPlayer(val stemID: String, val stem: Stem, val player: MusicSamplePlayer) :
        Plug(player.context, player.outs) {

        private var envelope: Envelope? = null

        init {
            this.addInput(player)
        }

        fun fadeTo(gain: Float, seconds: Float, delaySec: Float, startGain: Float = -1f) {
            val oldEnvelope = this.envelope
            if (oldEnvelope != null) {
                oldEnvelope.kill()
                this.removeDependent(oldEnvelope)
                this.envelope = null
            }

            val oldGain = player.gain
            val newEnvelope = object : Envelope(this.context, oldGain) {
                override fun calculateBuffer() {
                    super.calculateBuffer()
                    val currentValue = this.currentValue
                    player.gain = currentValue
                    if (currentValue == 0f) {
                        player.pause(true)
                    } else {
                        player.pause(false)
                    }
                }
            }.apply {
                if (delaySec > 0f) {
                    this.addSegment(if (startGain < 0f) oldGain else startGain, delaySec * 1000)
                }
                this.addSegment(gain, seconds * 1000)
            }
            this.envelope = newEnvelope
            this.addDependent(newEnvelope)
        }
    }

    val soundSystem: SoundSystem = SoundSystem.createDefaultSoundSystemOpenAL()
    private val audioContext: AudioContext = soundSystem.audioContext
    private val playerInput: Gain = Gain(audioContext, 2, 1f)
    private val bandpass: UGen = Muffled(audioContext, playerInput.outs)
    private val crossFade: CrossFade = CrossFade(soundSystem.audioContext, playerInput)
    private var shouldBeBandpass: Boolean = false

    private val stemPlayers: MutableMap<String, StemPlayer> = mutableMapOf()
    private val menuMusicVolume: ReadOnlyFloatVar =
        FloatVar { bindAndGet(PRManiaGame.instance.settings.menuMusicVolume) / 100f }

    private var targetStemMix: StemMix = StemMix.NONE

    val currentBeat: ReadOnlyFloatVar = FloatVar(0f)

    init {
        bandpass.addInput(playerInput)
        audioContext.out.addInput(crossFade)

        menuMusicVolume.addListener { updateMainGain() }
        updateMainGain()

        startSounds()
    }

    fun transitionToBandpass(bandpass: Boolean, durationMs: Float = 250f) {
        if (shouldBeBandpass == bandpass) return
        shouldBeBandpass = bandpass

        crossFade.fadeTo(if (bandpass) this.bandpass else playerInput, durationMs)
    }

    fun frameUpdate() {
        val currentPosMs = stemPlayers.values.firstOrNull { !it.player.isPaused }?.player?.position?.toFloat() ?: 0f
        (currentBeat as FloatVar).set(TempoUtils.secondsToBeats(currentPosMs / 1000f, BPM) % DURATION_BEATS)
    }

    fun transitionToStemMix(stemMix: StemMix, durationSec: Float, delaySec: Float = 0f, startGain: Float = -1f) {
        val oldTarget = targetStemMix
        val shouldRestart = stemPlayers.values.all { it.player.isPaused }

        targetStemMix = stemMix

        val stemsToBeRemoved = oldTarget.stemIDs - targetStemMix.stemIDs
        stemsToBeRemoved.forEach { stemID ->
            stemPlayers[stemID]?.fadeTo(0f, durationSec, delaySec)
        }

        val stemsToBeAdded = targetStemMix.stemIDs
        val restartPosition =
            if (shouldRestart) 0.0 else (stemPlayers.values.firstOrNull { !it.player.isPaused }?.player?.position
                ?: 0.0)
        stemsToBeAdded.forEach { stemID ->
            val stemPlayer = getStemPlayer(stemID)
            stemPlayer.player.position = restartPosition
            stemPlayer.fadeTo(1f, durationSec, delaySec, startGain)
        }
    }

    fun transitionToTitleMix() {
        transitionToStemMix(getTitleStemMix(), 1f)
    }

    fun transitionToPostResultsMix(isSuperHard: Boolean) {
        transitionToStemMix(getPostResultsStemMix(isSuperHard), 1.675f, delaySec = 2.54f, startGain = 0.3f)
    }

    fun transitionToDesktopMix(inboxState: InboxState? = null) {
        transitionToStemMix(getDesktopStemMix(inboxState), 1f)
    }

    fun fadeOut(durationSec: Float) {
        transitionToStemMix(StemMix.NONE, durationSec)
    }

    fun fadeOutAndDispose(durationSec: Float) {
        fadeOut(durationSec)
        audioContext.out.addDependent(DelayTrigger(audioContext, durationSec * 1000.0, object : Bead() {
            override fun messageReceived(message: Bead?) {
                Gdx.app.postRunnable {
                    stopSounds()
                }
            }
        }))
    }

    private fun createNewStemPlayer(stemID: String, stem: Stem, sample: MusicSample): StemPlayer {
        return StemPlayer(stemID, stem, MusicSamplePlayer(sample, audioContext).apply {
            this.loopStartMs = sample.samplesToMs(LOOP_SAMPLES_START.toDouble()).toFloat()
            this.loopEndMs = sample.samplesToMs(LOOP_SAMPLES_END.toDouble()).toFloat()
            this.loopType = SamplePlayer.LoopType.LOOP_FORWARDS

            this.gain = 0f

            val existingPlayer = stemPlayers.values.firstOrNull()
            if (existingPlayer != null) {
                this.position = existingPlayer.player.position
            }
        })
    }

    private fun getStemPlayer(stemID: String): StemPlayer {
        return stemPlayers.getOrPut(stemID) {
            val stem = StoryMusicAssets.titleStems.getOrLoad(stemID) ?: error("No stem found with ID $stemID")
            val sample: MusicSample = if (stem.isSampleAccessible.get()) stem.sample else {
                Paintbox.LOGGER.error(
                    "Attempted to get sample for stem ${stem.file} before it was accessible!",
                    tag = "StoryMusicHandler"
                )
                InMemoryMusicSample(ByteArray(2), nChannels = 1) // Blank sample
            }
            createNewStemPlayer(stemID, stem, sample).apply {
                playerInput.addInput(this)
            }
        }
    }

    private fun updateMainGain() {
        this.audioContext.out.gain = menuMusicVolume.get()
    }


    fun getCurrentlyActiveStemMix(): StemMix = this.targetStemMix

    fun getTitleStemMix(): StemMix = StemMixes.titleMain

    fun getPostResultsStemMix(isSuperHard: Boolean): StemMix =
        if (isSuperHard) getPostResultsStemMixSuperHard() else getPostResultsStemMixNormal()

    fun getPostResultsStemMixNormal(): StemMix = StemMixes.desktopResults
    fun getPostResultsStemMixSuperHard(): StemMix = StemMixes.desktopResultsSuperHard

    fun getDesktopStemMix(useInboxState: InboxState?): StemMix {
        val inboxState =
            useInboxState ?: storySession.currentSavefile?.inboxState ?: return StemMixes.desktopPreTraining101

        val tutorial1InboxItemID = InboxItem.ContractDoc.getDefaultContractDocID(Contracts.ID_TUTORIAL1)

        return when {
            inboxState.getItemState(InboxDB.ITEM_TO_TRIGGER_POSTGAME_MUSIC_MIX)?.completion?.shouldCountAsCompleted() == true ->
                StemMixes.desktopPostGame
            inboxState.getItemState(InboxDB.ITEM_TO_TRIGGER_POSTBOSS_MAIN_MUSIC_MIX)?.completion?.shouldCountAsCompleted() == true ->
                StemMixes.desktopPostBossMain
            inboxState.getItemState(InboxDB.ITEM_TO_TRIGGER_POSTBOSS_QUIET_MUSIC_MIX)?.completion?.shouldCountAsCompleted() == true ->
                StemMixes.desktopPostBossQuiet
            inboxState.getItemState(InboxDB.ITEM_TO_TRIGGER_POSTBOSS_MINIMAL_MUSIC_MIX)?.completion?.shouldCountAsCompleted() == true ->
                StemMixes.desktopPostBossMinimal
            inboxState.getItemState(InboxDB.ITEM_TO_TRIGGER_POSTBOSS_SILENT_MUSIC_MIX)?.completion?.shouldCountAsCompleted() == true ->
                StemMixes.desktopPostBossSilent
            inboxState.getItemState(InboxDB.ITEM_TO_TRIGGER_PREBOSS_QUIET_MUSIC_MIX)?.completion?.shouldCountAsCompleted() == true ->
                StemMixes.desktopPreBossQuiet
            inboxState.getItemState(InboxDB.ITEM_TO_TRIGGER_MAIN_MUSIC_MIX)?.completion?.shouldCountAsCompleted() == true ->
                StemMixes.desktopMain
            inboxState.getItemState(tutorial1InboxItemID)?.completion?.shouldCountAsCompleted() == true ->
                StemMixes.desktopInternship
            else -> StemMixes.desktopPreTraining101
        }
    }

    fun startSounds() {
        soundSystem.startRealtime()
        soundSystem.setPaused(false)
    }

    fun stopSounds() {
        soundSystem.stopRealtime()
    }

}