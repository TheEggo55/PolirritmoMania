package polyrhythmmania.discordrpc

import club.minnced.discord.rpc.DiscordEventHandlers
import club.minnced.discord.rpc.DiscordRPC
import club.minnced.discord.rpc.DiscordRichPresence
import club.minnced.discord.rpc.DiscordUser
import kotlin.concurrent.thread


object DiscordHelper {

    const val DISCORD_APP_ID = "869413093665558589"
    const val DEFAULT_LARGE_IMAGE = "square_logo"
    private var inited = false
    var initTime: Long = 0L
        private set

    private val lib: DiscordRPC
        get() = DiscordRPC.INSTANCE
    @Volatile
    private var queuedPresence: DiscordRichPresence? = null
    @Volatile
    private var lastSent: DiscordRichPresence? = null
    @Volatile
    var enabled = true
        set(value) {
            val old = field
            field = value
            if (value) {
                if (!old) {
                    queuedPresence = lastSent
                }
                signalUpdate(true)
            } else {
                clearPresence()
            }
        }
    @Volatile
    var currentUser: DiscordUser? = null

    @Synchronized
    fun init(enabled: Boolean = DiscordHelper.enabled) {
        if (inited)
            return
        inited = true
        initTime = System.currentTimeMillis()
        DiscordHelper.enabled = enabled

        lib.Discord_Initialize(DISCORD_APP_ID, DiscordEventHandlers().apply {
            this.ready = DiscordEventHandlers.OnReady {
                currentUser = it
            }
        }, true, "")

        Runtime.getRuntime().addShutdownHook(thread(start = false, name = "Discord-RPC Shutdown", block = lib::Discord_Shutdown))

        thread(isDaemon = true, name = "Discord-RPC Callback Handler", priority = 2) {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(1000L)
                } catch (ignored: InterruptedException) {
                }
                lib.Discord_RunCallbacks()
            }
        }
    }

    @Synchronized
    private fun signalUpdate(force: Boolean = false) {
        if (enabled) {
            val queued = queuedPresence
            val lastSent = lastSent
            if (force || (queued !== null && lastSent !== queued)) {
                lib.Discord_UpdatePresence(queued)
                DiscordHelper.lastSent = queued
                queuedPresence = null
            }
        }
    }

    @Synchronized
    fun clearPresence() {
        lib.Discord_ClearPresence()
    }

    @Synchronized
    fun updatePresence(presence: DiscordRichPresence) {
        queuedPresence = presence
        signalUpdate()
    }

    @Synchronized
    fun updatePresence(presenceState: PresenceState) {
        updatePresence(RichPresence(presenceState))
    }
    
}

fun DiscordUser.stringify(): String = "$username#$discriminator ($userId) (av: $avatar)"