package polyrhythmmania.editor.pane

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import io.github.chrislo27.paintbox.registry.AssetRegistry
import io.github.chrislo27.paintbox.ui.Anchor
import io.github.chrislo27.paintbox.ui.ImageNode
import io.github.chrislo27.paintbox.ui.Pane
import io.github.chrislo27.paintbox.ui.area.Insets
import io.github.chrislo27.paintbox.ui.border.SolidBorder
import io.github.chrislo27.paintbox.ui.control.Button
import io.github.chrislo27.paintbox.ui.control.ButtonSkin


class Toolbar(val upperPane: UpperPane) : Pane() {

    val editorPane: EditorPane = upperPane.editorPane
    
    val previewSection: Pane
    
    val pauseButton: Button
    val playButton: Button
    val stopButton: Button
    
    init {
        this.border.set(Insets(2f, 2f, 0f, 0f))
        this.borderStyle.set(SolidBorder().apply { this.color.bind { editorPane.palette.upperPaneBorder.use() } })
        this.padding.set(Insets(2f))

        
        
        // Preview section
        previewSection = Pane().apply { 
            Anchor.TopLeft.configure(this)
            this.bounds.width.bind { upperPane.previewPane.contentZone.width.use() - 2f }
            this.border.set(Insets(0f, 0f, 0f, 2f))
            this.borderStyle.set(SolidBorder().apply { this.color.bind { editorPane.palette.previewPaneSeparator.use() } })
        }
        this += previewSection
        
        val playbackButtonPane = Pane().apply { 
            Anchor.Centre.configure(this)
            this.bounds.width.set(32f * 3 + 4f * 2)
        }
        previewSection += playbackButtonPane
        pauseButton = Button("").apply {
            this.bounds.width.set(32f + 4f)
            this.margin.set(Insets(0f, 0f, 0f, 4f))
            Anchor.TopLeft.configure(this, offsetX = 32f * 0 + 4f * 0, offsetY = 0f)
            (this.skin as ButtonSkin).roundedRadius.set(0)
            this += ImageNode(TextureRegion(AssetRegistry.get<Texture>("ui_icon_button_pause"))).apply {
                this.tint.bind { editorPane.palette.menubarIconTint.use() }
            }
        }
        playButton = Button("").apply {
            this.bounds.width.set(32f + 4f)
            this.margin.set(Insets(0f, 0f, 0f, 4f))
            Anchor.TopLeft.configure(this, offsetX = 32f * 1 + 4f * 1, offsetY = 0f)
            (this.skin as ButtonSkin).roundedRadius.set(0)
            this += ImageNode(TextureRegion(AssetRegistry.get<Texture>("ui_icon_button_play"))).apply {
                this.tint.bind { editorPane.palette.menubarIconTint.use() }
            }
        }
        stopButton = Button("").apply {
            this.bounds.width.set(32f)
            this.margin.set(Insets(0f, 0f, 0f, 0f))
            Anchor.TopLeft.configure(this, offsetX = 32f * 2 + 4f * 2, offsetY = 0f)
            (this.skin as ButtonSkin).roundedRadius.set(0)
            this += ImageNode(TextureRegion(AssetRegistry.get<Texture>("ui_icon_button_stop"))).apply {
                this.tint.bind { editorPane.palette.menubarIconTint.use() }
            }
        }
        playbackButtonPane += pauseButton
        playbackButtonPane += playButton
        playbackButtonPane += stopButton
    }
    
}