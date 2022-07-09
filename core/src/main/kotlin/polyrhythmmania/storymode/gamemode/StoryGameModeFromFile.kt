package polyrhythmmania.storymode.gamemode

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import polyrhythmmania.PRManiaGame
import polyrhythmmania.editor.EditorSpecialFlags
import polyrhythmmania.util.TempFileUtils
import java.util.*


class StoryGameModeFromFile(main: PRManiaGame, val file: FileHandle)
    : AbstractStoryGameMode(main) {

    init {
        // FIXME tmp file copy should probably be elsewhere, like a loading screen
        val tmpFile = TempFileUtils.createTempFile("storylevel")
        file.read().buffered().use { input ->
            tmpFile.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }
        val loadMetadata = this.container.readFromFile(tmpFile, EnumSet.of(EditorSpecialFlags.STORY_MODE))
        Gdx.app.postRunnable { loadMetadata.loadOnGLThread() }
        tmpFile.deleteOnExit()
        println(this.container.texturePackSource.getOrCompute())
    }

    override fun initialize() {
        
    }
}