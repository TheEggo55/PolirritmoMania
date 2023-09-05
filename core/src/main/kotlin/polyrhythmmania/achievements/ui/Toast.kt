package polyrhythmmania.achievements.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Align
import paintbox.packing.PackedSheet
import paintbox.registry.AssetRegistry
import paintbox.ui.*
import paintbox.ui.area.Insets
import paintbox.ui.border.SolidBorder
import paintbox.ui.control.TextLabel
import paintbox.ui.element.RectElement
import paintbox.util.gdxutils.grey
import polyrhythmmania.Localization
import polyrhythmmania.PRManiaGame
import polyrhythmmania.achievements.Achievement
import polyrhythmmania.achievements.AchievementCategory
import polyrhythmmania.achievements.Fulfillment


class Toast(val achievement: Achievement, val fulfillment: Fulfillment) : UIElement() {
    
    val imageIcon: ImageIcon = ImageIcon(null, renderingMode = ImageRenderingMode.MAINTAIN_ASPECT_RATIO)
    val titleLabel: TextLabel
    val nameLabel: TextLabel
    
    init {
        this.bounds.height.set(80f)
        this.bindWidthToSelfHeight(multiplier = 4.5f)
        
        val outermostBorderColor = Color(0f, 0f, 0f, 1f)
        val middleBorderColor = Color().grey(85f / 255f).lerp(achievement.rank.color, 0.5f)
        val innermostColor = Color().grey(33f / 255f)
        
        val innermostRect = RectElement(innermostColor).also { rect ->
            rect.padding.set(Insets(8f + 2))
            rect.border.set(Insets(3f))
            rect.borderStyle.set(SolidBorder(middleBorderColor))
        }
        
        // Outermost border
        this += RectElement(outermostBorderColor).apply { 
            this.border.set(Insets(6f - 2))
            this.borderStyle.set(SolidBorder().also { border ->
                border.color.bind { this@apply.color.use() }
                border.roundedCorners.set(true)
            })
            
            // Middle border
            this += RectElement(innermostColor).also { rect ->
                rect.border.set(Insets(3f))
                rect.borderStyle.set(SolidBorder(middleBorderColor).apply {
                    this.roundedCorners.set(true)
                })
                rect += innermostRect
            }
        }
        
        innermostRect += imageIcon.apply {
            Anchor.TopLeft.configure(this)    
            this.bindWidthToSelfHeight()
        }
        val main = PRManiaGame.instance
        titleLabel = TextLabel("", font = main.fontMainMenuMain).apply { 
            Anchor.TopLeft.configure(this)
            this.bindHeightToParent(multiplier = 0.5f, adjust = -2f)
            this.setScaleXY(0.8f)
            this.renderAlign.set(Align.left)
            this.padding.set(Insets.ZERO)
            this.textColor.set(achievement.rank.color.cpy())
        }
        nameLabel = TextLabel("", font = main.fontMainMenuMain).apply { 
            Anchor.BottomLeft.configure(this)
            this.bindHeightToParent(multiplier = 0.5f, adjust = -2f)
            this.setScaleXY(0.8f)
            this.renderAlign.set(Align.left)
            this.padding.set(Insets.ZERO)
            this.textColor.set(Color.LIGHT_GRAY)
        }
        
        val pane = Pane().apply {
            Anchor.TopRight.configure(this)    
            this.bindWidthToParent(adjustBinding = {
                -((parent.use()?.contentZone?.height?.use() ?: 0f) + 8f)
            })
            this += titleLabel
            this += nameLabel
        }
        innermostRect += pane
    }
    
    init {
        val shouldSayHidden = achievement.isHidden && achievement.category != AchievementCategory.STORY_MODE
        this.titleLabel.text.set(Localization.getValue(achievement.rank.toAchievementLocalizationID(shouldSayHidden)))
        this.nameLabel.text.set(achievement.getLocalizedName().getOrCompute())
        this.imageIcon.textureRegion.set(TextureRegion(AssetRegistry.get<PackedSheet>("achievements_icon")[achievement.getIconID()]))
    }
}