package polyrhythmmania.editor.pane.dialog

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.eclipsesource.json.Json
import com.eclipsesource.json.WriterConfig
import paintbox.binding.FloatVar
import paintbox.binding.Var
import paintbox.font.TextAlign
import paintbox.packing.PackedSheet
import paintbox.registry.AssetRegistry
import paintbox.ui.Anchor
import paintbox.ui.ImageNode
import paintbox.ui.Pane
import paintbox.ui.UIElement
import paintbox.ui.area.Insets
import paintbox.ui.border.SolidBorder
import paintbox.ui.control.*
import paintbox.ui.element.RectElement
import paintbox.ui.layout.HBox
import paintbox.ui.layout.VBox
import paintbox.util.Matrix4Stack
import paintbox.util.gdxutils.disposeQuietly
import paintbox.util.gdxutils.grey
import polyrhythmmania.Localization
import polyrhythmmania.editor.pane.EditorPane
import polyrhythmmania.ui.ColourPicker
import polyrhythmmania.ui.PRManiaSkins
import polyrhythmmania.world.World
import polyrhythmmania.world.entity.*
import polyrhythmmania.world.render.WorldRenderer
import polyrhythmmania.world.texturepack.StockTexturePacks
import polyrhythmmania.world.tileset.ColorMapping
import polyrhythmmania.world.tileset.Tileset
import polyrhythmmania.world.tileset.TilesetPalette
import polyrhythmmania.world.tileset.TintedRegion
import kotlin.math.sign


class PaletteEditDialog(
        editorPane: EditorPane, val tilesetPalette: TilesetPalette,
        val baseTileset: TilesetPalette?, val canChangeEnabledState: Boolean,
        val disposeOnClose: Boolean,
        val titleLocalization: String = "editor.dialog.tilesetPalette.title",
) : EditorDialog(editorPane), Disposable {

    companion object {
        private val PR1_CONFIG: TilesetPalette = TilesetPalette.createGBA1TilesetPalette()
        private val PR2_CONFIG: TilesetPalette = TilesetPalette.createGBA2TilesetPalette()
        private val COLOURLESS_CONFIG: TilesetPalette = TilesetPalette.createColourlessTilesetPalette()
        private val ORANGE_BLUE_CONFIG: TilesetPalette = TilesetPalette.createOrangeBlueTilesetPalette()
    }

    data class ResetDefault(val baseConfig: TilesetPalette, val localization: String)
    
    private val availableResetDefaults: List<ResetDefault> = listOfNotNull(
            ResetDefault(PR1_CONFIG, "pr1"),
            ResetDefault(PR2_CONFIG, "pr2"),
            ResetDefault(COLOURLESS_CONFIG, "colourless"),
            ResetDefault(ORANGE_BLUE_CONFIG, "orangeBlue"),
            baseTileset?.let { ResetDefault(it, "base") }
    )
    private var resetDefault: ResetDefault = availableResetDefaults.first()
    private val tempTileset: Tileset = Tileset(StockTexturePacks.gba /* This isn't used for rendering so any stock texture pack is fine */).apply {
        tilesetPalette.applyTo(this)
    }

    val groupFaceYMapping: ColorMappingGroupedCubeFaceY = ColorMappingGroupedCubeFaceY("groupCubeFaceYMapping")
    val groupPistonAFaceZMapping: ColorMappingGroupedPistonFaceZ = ColorMappingGroupedPistonFaceZ("groupPistonAFaceZMapping", "pistonAFaceX", "pistonAFaceZ", { it.pistonAFaceXColor }, { it.pistonAFaceZColor })
    val groupPistonDpadFaceZMapping: ColorMappingGroupedPistonFaceZ = ColorMappingGroupedPistonFaceZ("groupPistonDpadFaceZMapping", "pistonDpadFaceX", "pistonDpadFaceZ", { it.pistonDpadFaceXColor }, { it.pistonDpadFaceZColor })
    val groupPistonsFaceMapping: ColorMappingBothPistonFaces = ColorMappingBothPistonFaces("groupBothPistonFaces", groupPistonAFaceZMapping, groupPistonDpadFaceZMapping)
    val groupRodsBorderMapping: ColorMappingGroupedRodsBorder = ColorMappingGroupedRodsBorder("groupRodsBorder")
    val groupRodsFillMapping: ColorMappingGroupedRodsFill = ColorMappingGroupedRodsFill("groupRodsFill")
    private val groupMappings: List<ColorMapping> = listOf(groupFaceYMapping, groupPistonsFaceMapping, groupPistonAFaceZMapping,
            groupPistonDpadFaceZMapping, groupRodsBorderMapping, groupRodsFillMapping)
    val allMappings: List<ColorMapping> = groupMappings + tilesetPalette.allMappings
    val allMappingsByID: Map<String, ColorMapping> = allMappings.associateBy { it.id }
    val currentMapping: Var<ColorMapping> = Var(allMappings[0])
    
    val objPreview: ObjectPreview = ObjectPreview()
    val colourPicker: ColourPicker = ColourPicker(hasAlpha = true, font = editorPane.palette.musicDialogFont).apply { 
        this.setColor(currentMapping.getOrCompute().color.getOrCompute(), true)
        this.showAlphaPane.set(false)
    }
    val enabledCheckbox: CheckBox = CheckBox(binding = { Localization.getVar("editor.dialog.tilesetPalette.enabled").use() })

    /**
     * When false, updating the color in [ColourPicker] will NOT apply that colour to the tileset.
     * Used when switching between colour properties since there's no need for it to be applied
     */
    private var shouldColorPickerUpdateUpdateTileset: Boolean = true
    
    private val rodRotation: FloatVar = FloatVar(0f)

    init {
        resetGroupMappingsToTileset()
        
        this.titleLabel.text.bind { Localization.getVar(titleLocalization).use() }

        bottomPane.addChild(Button("").apply {
            Anchor.BottomRight.configure(this)
            this.bindWidthToSelfHeight()
            this.applyDialogStyleBottom()
            this.setOnAction {
                attemptClose()
            }
            this += ImageNode(TextureRegion(AssetRegistry.get<PackedSheet>("ui_icon_editor_linear")["x"])).apply {
                this.tint.bind { editorPane.palette.toolbarIconToolNeutralTint.use() }
            }
            this.tooltipElement.set(editorPane.createDefaultTooltip(Localization.getVar("common.close")))
        })

        val scrollPane: ScrollPane = ScrollPane().apply {
            this.vBarPolicy.set(ScrollPane.ScrollBarPolicy.ALWAYS)
            this.hBarPolicy.set(ScrollPane.ScrollBarPolicy.NEVER)
            this.bindWidthToParent(multiplier = 0.4f)
            this.vBar.blockIncrement.set(64f)
            this.vBar.skinID.set(PRManiaSkins.SCROLLBAR_SKIN)
        }
        contentPane.addChild(scrollPane)

        val listVbox = VBox().apply {
            this.spacing.set(1f)
        }

        listVbox.temporarilyDisableLayouts {
            val toggleGroup = ToggleGroup()
            allMappings.forEachIndexed { index, mapping ->
                listVbox += RadioButton(binding = { Localization.getVar("editor.dialog.tilesetPalette.object.${mapping.id}").use() },
                        font = editorPane.palette.musicDialogFont).apply {
                    this.textLabel.textColor.set(Color.WHITE.cpy())
                    this.textLabel.margin.set(Insets(0f, 0f, 8f, 8f))
                    this.textLabel.markup.set(editorPane.palette.markup)
                    if (canChangeEnabledState) {
                        this.color.bind {
                            val enabled = mapping.enabled.use()
                            if (enabled) Color.WHITE else Color.GRAY
                        }
                    } else {
                        this.color.set(Color.WHITE.cpy())
                    }
                    this.imageNode.padding.set(Insets(4f))
                    toggleGroup.addToggle(this)
                    this.bounds.height.set(48f)
                    this.onSelected = {
                        currentMapping.set(mapping)
                        shouldColorPickerUpdateUpdateTileset = false
                        updateColourPickerToMapping(mapping)
                        shouldColorPickerUpdateUpdateTileset = true
                    }
                    if (index == 0) selectedState.set(true)
                }
            }
        }
        listVbox.sizeHeightToChildren(300f)
        scrollPane.setContent(listVbox)

        val previewVbox = VBox().apply {
            Anchor.TopRight.configure(this)
            this.bindWidthToParent(multiplier = 0.6f, adjust = -8f)
            this.spacing.set(12f)
        }
        contentPane.addChild(previewVbox)
        previewVbox.temporarilyDisableLayouts {
            previewVbox += HBox().apply {
                this.spacing.set(8f)
                this.margin.set(Insets(4f))
                this.bounds.height.set(200f)
                this.temporarilyDisableLayouts {
                    this += objPreview.apply {
                        this.bounds.width.bind { 
                            bounds.height.use() * (16f / 9f)
                        }
                    }
                    this += VBox().also { v -> 
                        v.bindWidthToParent(adjustBinding = { objPreview.bounds.width.use() * -1 + -5f })
                        v.spacing.set(4f)
                        v.temporarilyDisableLayouts { 
                            v += HBox().apply {
                                this.spacing.set(8f)
                                this.bounds.height.set(64f)
                                this.temporarilyDisableLayouts { 
                                    this += RectElement(Color().grey(0.95f)).apply {
                                        this.bounds.width.set(64f)
                                        this.padding.set(Insets(2f))
                                        this += ImageNode(AssetRegistry.get<PackedSheet>("tileset_gba")["platform"]).apply {
                                            Anchor.Centre.configure(this)
                                        }
                                        this += ImageNode(AssetRegistry.get<PackedSheet>("tileset_gba")["xyz"]).apply { 
                                            Anchor.Centre.configure(this)
                                        }
                                    }
                                    this += TextLabel("[b][color=#FF0000]X-[] [color=#00D815]Y+[] [color=#0000FF]Z+[][]").apply { 
                                        this.markup.set(editorPane.palette.markup)
                                        this.bounds.width.set(100f)
                                        this.textColor.set(Color.WHITE)
                                        this.renderBackground.set(true)
                                        this.bgPadding.set(Insets(8f))
                                        (this.skin.getOrCompute() as TextLabelSkin).defaultBgColor.set(Color(1f, 1f, 1f, 01f))
                                    }
                                    this += VBox().apply {
                                        this.padding.set(Insets(1f))
                                        this.border.set(Insets(1f))
                                        this.borderStyle.set(SolidBorder(Color.WHITE))
                                        this.bounds.width.set(150f)
                                        this.spacing.set(0f)
                                        this += TextLabel(binding = { Localization.getVar("editor.dialog.tilesetPalette.rotateRod").use() }).apply {
                                            this.padding.set(Insets(1f, 1f, 2f, 2f))
                                            this.markup.set(editorPane.palette.markup)
                                            this.textColor.set(Color.WHITE)
                                            this.bounds.height.set(28f)
                                            this.renderAlign.set(Align.left)
                                        }
                                        this += Pane().apply {
                                            this.bounds.height.set(28f)
                                            this.padding.set(Insets(2f, 2f, 1f, 1f))
                                            this += Slider().apply slider@{
                                                this.minimum.set(0f)
                                                this.maximum.set(1f)
                                                this.tickUnit.set(0f)
                                                this.setValue(0f)
                                                rodRotation.bind { this@slider.value.use() * 2f }
                                            }
                                        }
                                    }
                                }
                            }
                            v += Button(binding = { Localization.getVar("editor.dialog.tilesetPalette.reset").use() },
                                    font = editorPane.palette.musicDialogFont).apply {
                                this.applyDialogStyleContent()
                                this.bounds.height.set(40f)
                                this.setOnAction {
                                    val currentMapping = currentMapping.getOrCompute()
                                    val affectedMappings: Set<ColorMapping> = if (currentMapping is ColorMappingGroup) {
                                        currentMapping.affectsMappings.toSet()
                                    } else setOf(currentMapping)
                                    val baseConfig = resetDefault.baseConfig
                                    affectedMappings.forEach { cm ->
                                        val id = cm.id
                                        val baseConfigMapping = baseConfig.allMappingsByID.getValue(id)
                                        val tilesetPaletteMapping = tilesetPalette.allMappingsByID.getValue(id)
                                        tilesetPaletteMapping.color.set(baseConfigMapping.color.getOrCompute().cpy())
                                    }
                                    
                                    tilesetPalette.applyTo(tempTileset)
                                    
                                    currentMapping.color.set(currentMapping.tilesetGetter(tempTileset).getOrCompute().cpy())
                                    updateColourPickerToMapping()
                                }
                            }
                            v += HBox().apply {
                                this.visible.set(canChangeEnabledState)
                                this.bounds.height.set(40f)
                                this.spacing.set(4f)
                                this.temporarilyDisableLayouts {
                                    val checkbox = enabledCheckbox.apply {
                                        this.bounds.width.set(150f)
                                        this.textLabel.markup.set(editorPane.palette.markup)
                                        this.tooltipElement.set(editorPane.createDefaultTooltip(Localization.getVar("editor.dialog.tilesetPalette.enabled.tooltip")))
                                        this.imageNode.padding.set(Insets(4f))
                                        this.color.set(Color.WHITE.cpy())
                                        
                                        this.setOnAction { // This overrides the default behaviour of CheckBox
                                            val newState = checkedState.invert()
                                            val currentMapping = currentMapping.getOrCompute()
                                            if (currentMapping is ColorMappingGroup) {
                                                currentMapping.affectsMappings.forEach { m ->
                                                    m.enabled.set(newState)
                                                }
                                            } else {
                                                currentMapping.enabled.set(newState)
                                            }
                                        }
                                    }
                                    this += checkbox
                                    this += Button(binding = { Localization.getVar("editor.dialog.tilesetPalette.enableAll").use() },
                                            font = editorPane.palette.musicDialogFont).apply {
                                        this.bounds.width.set(90f)
                                        this.setScaleXY(0.8f)
                                        this.setOnAction {
                                            checkbox.checkedState.set(true)
                                            tilesetPalette.allMappings.forEach { m ->
                                                m.enabled.set(true)
                                            }
                                        }
                                    }
                                    this += Button(binding = { Localization.getVar("editor.dialog.tilesetPalette.disableAll").use() },
                                            font = editorPane.palette.musicDialogFont).apply {
                                        this.bounds.width.set(90f)
                                        this.setScaleXY(0.8f)
                                        this.setOnAction {
                                            checkbox.checkedState.set(false)
                                            tilesetPalette.allMappings.forEach { m ->
                                                m.enabled.set(false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            previewVbox += colourPicker.apply {
                this.bindWidthToParent()
                this.bounds.height.set(220f)
            }
        }


        val bottomLeftHbox = HBox().apply {
            this.spacing.set(8f)
            this.bindWidthToParent(multiplier = 0.8f)
        }
        bottomLeftHbox.temporarilyDisableLayouts {
            bottomLeftHbox += TextLabel(binding = { Localization.getVar("editor.dialog.tilesetPalette.resetLabel").use() },
                    font = editorPane.palette.musicDialogFont).apply {
                this.markup.set(editorPane.palette.markup)
                this.textColor.set(Color.WHITE.cpy())
                this.renderAlign.set(Align.right)
                this.textAlign.set(TextAlign.RIGHT)
                this.doLineWrapping.set(true)
                this.bounds.width.set(250f)
            }
            bottomLeftHbox += ComboBox(availableResetDefaults, resetDefault).apply {
                Anchor.CentreLeft.configure(this)
                this.markup.set(editorPane.palette.markup)
                this.setScaleXY(0.875f)
                this.bounds.height.set(32f)
                this.bounds.width.set(250f)
                this.itemStringConverter.set {
                    Localization.getValue("editor.dialog.tilesetPalette.reset.${it.localization}")
                }
                this.onItemSelected = {
                    resetDefault = it
                }
            }
            bottomLeftHbox += Button(binding = { Localization.getVar("editor.dialog.tilesetPalette.resetAll").use() },
                    font = editorPane.palette.musicDialogFont).apply {
                this.applyDialogStyleBottom()
                this.bounds.width.set(325f)
                this.setOnAction {
                    val baseConfig = resetDefault.baseConfig
                    baseConfig.allMappings.forEach { baseMapping ->
                        val m = tilesetPalette.allMappingsByID.getValue(baseMapping.id)
                        val baseColor = baseMapping.color.getOrCompute()
                        m.color.set(baseColor.cpy())
                    }
                    tilesetPalette.applyTo(objPreview.worldRenderer.tileset)
                    resetGroupMappingsToTileset()
                    updateColourPickerToMapping()
                }
            }
        }
        bottomPane.addChild(bottomLeftHbox)

        val bottomRightHbox = HBox().apply {
            this.spacing.set(8f)
            Anchor.TopRight.configure(this, offsetX = { -(bounds.height.use() + 8f) })
            this.align.set(HBox.Align.RIGHT)
            this.bindWidthToParent(multiplierBinding = { 0.2f }, adjustBinding = { -(bounds.height.use() + 8f) })
        }
        bottomRightHbox.temporarilyDisableLayouts {
            bottomRightHbox += Button("").apply {
                this.applyDialogStyleBottom()
                this.bindWidthToSelfHeight()
                this.padding.set(Insets(8f))
                this += ImageNode(TextureRegion(AssetRegistry.get<Texture>("ui_colour_picker_copy")))
                this.tooltipElement.set(editorPane.createDefaultTooltip(Localization.getVar("editor.dialog.tilesetPalette.copyAll")))
                this.setOnAction {
                    Gdx.app.clipboard.contents = tilesetPalette.toJson().toString(WriterConfig.MINIMAL)
                }
            }
            bottomRightHbox += Button("").apply {
                this.applyDialogStyleBottom()
                this.bindWidthToSelfHeight()
                this.padding.set(Insets(8f))
                this += ImageNode(TextureRegion(AssetRegistry.get<Texture>("ui_colour_picker_paste")))
                this.tooltipElement.set(editorPane.createDefaultTooltip(Localization.getVar("editor.dialog.tilesetPalette.pasteAll")))
                this.setOnAction {
                    val clipboard = Gdx.app.clipboard
                    if (clipboard.hasContents()) {
                        try {
                            val jsonValue = Json.parse(clipboard.contents)
                            if (jsonValue.isObject) {
                                tilesetPalette.fromJson(jsonValue.asObject())
                                tilesetPalette.applyTo(objPreview.worldRenderer.tileset)
//                                applyCurrentMappingToPreview(currentMapping.getOrCompute().color.getOrCompute())
                                resetGroupMappingsToTileset()
                                shouldColorPickerUpdateUpdateTileset = false
                                updateColourPickerToMapping()
                                shouldColorPickerUpdateUpdateTileset = true
                            }
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
        }
        bottomPane.addChild(bottomRightHbox)
    }
    
    init {
        colourPicker.currentColor.addListener { c ->
            if (shouldColorPickerUpdateUpdateTileset) {
                applyCurrentMappingToPreview(c.getOrCompute().cpy())
            }
        }
    }
    
    private fun resetGroupMappingsToTileset() {
        groupMappings.forEach { m ->
            m.color.set(m.tilesetGetter(objPreview.worldRenderer.tileset).getOrCompute().cpy())
        }
    }
    
    private fun applyCurrentMappingToPreview(newColor: Color) {
        val m = currentMapping.getOrCompute()
        m.color.set(newColor.cpy())
        m.applyTo(objPreview.worldRenderer.tileset)
    }
    
    private fun updateColourPickerToMapping(mapping: ColorMapping = currentMapping.getOrCompute()) {
        colourPicker.showAlphaPane.set(mapping.canAdjustAlpha)
        colourPicker.setColor(mapping.color.getOrCompute(), true)
        enabledCheckbox.checkedState.set(mapping.enabled.get())
    }
    
    fun prepareShow(): PaletteEditDialog {
        shouldColorPickerUpdateUpdateTileset = false
        tilesetPalette.applyTo(objPreview.worldRenderer.tileset)
        resetGroupMappingsToTileset()
        updateColourPickerToMapping()
        shouldColorPickerUpdateUpdateTileset = true
        return this
    }

    override fun canCloseDialog(): Boolean {
        return true
    }

    override fun onCloseDialog() {
        super.onCloseDialog()
        editor.updatePaletteAndTexPackChangesState()
        if (disposeOnClose) {
            this.disposeQuietly()
        }
    }

    override fun dispose() {
        this.objPreview.disposeQuietly()
    }
    

    inner class ObjectPreview : UIElement(), Disposable {
        
        val world: World = World()
        val worldRenderer: WorldRenderer = WorldRenderer(world, Tileset(editor.container.renderer.tileset.texturePack).apply { 
            tilesetPalette.applyTo(this)
        })
        
        val rodEntityA: EntityRodDecor
        val rodEntityDpad: EntityRodDecor
        
        init {
            this += ImageNode(editor.previewTextureRegion)
            
            class RotatableRod(val isDpadRow: Boolean) : EntityRodDecor(world) {
                override fun getAnimationAlpha(): Float {
                    return ((rodRotation.get() + (if (isDpadRow) 0.5f else 0f)) % 1f).coerceIn(0f, 1f)
                }
                override fun getGroundBorderAnimations(tileset: Tileset): List<TintedRegion> {
                    return if (isDpadRow) tileset.rodDpadGroundBorderAnimations else tileset.rodAGroundBorderAnimations
                }
                override fun getAerialBorderAnimations(tileset: Tileset): List<TintedRegion> {
                    return if (isDpadRow) tileset.rodDpadAerialBorderAnimations else tileset.rodAAerialBorderAnimations
                }
                override fun getGroundFillAnimations(tileset: Tileset): List<TintedRegion> {
                    return if (isDpadRow) tileset.rodDpadGroundFillAnimations else tileset.rodAGroundFillAnimations
                }
                override fun getAerialFillAnimations(tileset: Tileset): List<TintedRegion> {
                    return if (isDpadRow) tileset.rodDpadAerialFillAnimations else tileset.rodAAerialFillAnimations
                }
            }
            rodEntityA = RotatableRod(false)
            rodEntityDpad = RotatableRod(true)
        }
        
        init {
            world.clearEntities()
            for (x in 2..12) {
                for (z in -5..4) {
                    val ent = if (z == 0) EntityPlatform(world, withLine = x == 4) else EntityCube(world, withLine = x == 4, withBorder = z == 1)
                    world.addEntity(ent.apply { 
                        this.position.set(x.toFloat(), -1f, z.toFloat())
                    })
                    if (z == 0 && x <= 4) {
                        world.addEntity(EntityPlatform(world, withLine = x == 4).apply {
                            this.position.set(x.toFloat(), 0f, z.toFloat())
                        })
                    }
                }
            }

            val pistonA = EntityPiston(world).apply {
                this.position.set(6f, 0f, 0f)
                this.type = EntityPiston.Type.PISTON_A
                this.pistonState = EntityPiston.PistonState.FULLY_EXTENDED
            }
            world.addEntity(pistonA)
            val pistonDpad = EntityPiston(world).apply {
                this.position.set(9f, 0f, 0f)
                this.type = EntityPiston.Type.PISTON_DPAD
                this.pistonState = EntityPiston.PistonState.FULLY_EXTENDED
            }
            world.addEntity(pistonDpad)
            world.addEntity(EntityCube(world).apply { 
                this.position.set(10f, 0f, -3f)
            })
            world.addEntity(rodEntityA.apply {
                this.position.set(4f, 1f, 0f)
            })
            world.addEntity(rodEntityDpad.apply {
                this.position.set(8.25f, 0f, 0f)
            })

            // Button signs
            val signs = mutableListOf<EntitySign>()
            signs += EntitySign(world, EntitySign.Type.SYMBOL_A).apply {
                this.position.set(5f, 2f, -3f)
            }
            signs += EntitySign(world, EntitySign.Type.SYMBOL_DPAD).apply {
                this.position.set(6f, 2f, -3f)
            }
            signs += EntitySign(world, EntitySign.Type.JP_BO).apply {
                this.position.set(4f, 2f, -2f)
            }
            signs += EntitySign(world, EntitySign.Type.JP_TA).apply {
                this.position.set(5f, 2f, -2f)
            }
            signs += EntitySign(world, EntitySign.Type.JP_N).apply {
                this.position.set(6f, 2f, -2f)
            }
            signs.forEach { sign ->
                world.addEntity(sign)
            }
        }

        override fun renderSelf(originX: Float, originY: Float, batch: SpriteBatch) {
            val renderBounds = this.paddingZone
            val x = renderBounds.x.get() + originX
            val y = originY - renderBounds.y.get()
            val w = renderBounds.width.get()
            val h = renderBounds.height.get()
            val lastPackedColor = batch.packedColor


            val cam = worldRenderer.camera
            cam.zoom = 1f / 2f
            cam.position.x = 3.5f
            cam.position.y = 1f
            cam.update()

            batch.end()
            val prevMatrix = Matrix4Stack.getAndPush().set(batch.projectionMatrix)
            batch.projectionMatrix = cam.combined
            val frameBuffer = editor.previewFrameBuffer
            if (frameBuffer != null) {
                frameBuffer.begin()
                Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
                worldRenderer.render(batch)
                frameBuffer.end()
            }
            batch.projectionMatrix = prevMatrix
            batch.begin()

            Matrix4Stack.pop()
            
            batch.packedColor = lastPackedColor
        }

        override fun dispose() {
            worldRenderer.disposeQuietly()
        }
    }
    

    open inner class ColorMappingGroup(
            id: String, val affectsMappings: List<ColorMapping>,
            tilesetGetter: (Tileset) -> Var<Color>
    ) : ColorMapping(id, tilesetGetter) {
        
        init {
            this.enabled.eagerBind {
                var anyEnabled = false
                affectsMappings.forEach { m ->
                    // Intentionally iterating through all of them since they are all dependencies.
                    // The order of the anyEnabled assignment is also intentional to ensure Var.use() is tested
                    anyEnabled = m.enabled.use() || anyEnabled
                }
                anyEnabled
            }
        }
    }

    inner class ColorMappingGroupedCubeFaceY(id: String)
        : ColorMappingGroup(id,
            listOf("cubeFaceY", "cubeBorder", "signShadow", "cubeBorderZ", "cubeFaceZ", "cubeFaceX").map { tilesetPalette.allMappingsByID.getValue(it) },
            { it.cubeFaceY.color }) {
        
        private val hsv: FloatArray = FloatArray(3) { 0f }

        override fun applyTo(tileset: Tileset) {
            val varr = tilesetGetter(tileset)
            val thisColor = this.color.getOrCompute()
            varr.set(thisColor.cpy())
            allMappingsByID.getValue("cubeFaceY").color.set(thisColor.cpy())

            thisColor.toHsv(hsv)
            hsv[1] = (hsv[1] + 0.18f * hsv[1].sign)
            hsv[2] = (hsv[2] - 0.17f)
            val borderColor = Color(1f, 1f, 1f, thisColor.a).fromHsv(hsv)
            tileset.cubeBorder.color.set(borderColor.cpy())
            allMappingsByID.getValue("cubeBorder").color.set(borderColor.cpy())
            tileset.signShadowColor.set(borderColor.cpy())
            allMappingsByID.getValue("signShadow").color.set(borderColor.cpy())

            hsv[1] = (hsv[1] + 0.03f * hsv[1].sign)
            hsv[2] = (hsv[2] - 0.13f)
            val cubeBorderZColor = Color(1f, 1f, 1f, thisColor.a).fromHsv(hsv)
            tileset.cubeBorderZ.color.set(cubeBorderZColor)
            allMappingsByID.getValue("cubeBorderZ").color.set(cubeBorderZColor.cpy())
            
            // Face
            thisColor.toHsv(hsv)
            hsv[1] = (hsv[1] + 0.08f * hsv[1].sign)
            hsv[2] = (hsv[2] - 0.10f)
            val faceZColor = Color(1f, 1f, 1f, thisColor.a).fromHsv(hsv)
            tileset.cubeFaceZ.color.set(faceZColor.cpy())
            allMappingsByID.getValue("cubeFaceZ").color.set(faceZColor.cpy())
            thisColor.toHsv(hsv)
            hsv[1] = (hsv[1] + 0.11f * hsv[1].sign)
            hsv[2] = (hsv[2] - 0.13f)
            val faceXColor = Color(1f, 1f, 1f, thisColor.a).fromHsv(hsv)
            tileset.cubeFaceX.color.set(faceXColor.cpy())
            allMappingsByID.getValue("cubeFaceX").color.set(faceXColor.cpy())
        }
    }

    inner class ColorMappingGroupedPistonFaceZ(
            id: String, val faceXID: String, val faceZID: String,
            val tilesetGetterFaceX: (Tileset) -> Var<Color>,
            val tilesetGetterFaceZ: (Tileset) -> Var<Color>
    ) : ColorMappingGroup(id, listOf(faceZID, faceXID).map { tilesetPalette.allMappingsByID.getValue(it) }, tilesetGetterFaceZ) {

        private val hsv: FloatArray = FloatArray(3) { 0f }

        override fun applyTo(tileset: Tileset) {
            val varr = tilesetGetterFaceZ(tileset)
            val thisColor = this.color.getOrCompute()
            varr.set(thisColor.cpy())
            allMappingsByID.getValue(faceZID).color.set(thisColor.cpy())

            thisColor.toHsv(hsv)
            hsv[2] = (hsv[2] - 0.20f)
            val pistonFaceX = Color(1f, 1f, 1f, thisColor.a).fromHsv(hsv)
            tilesetGetterFaceX(tileset).set(pistonFaceX.cpy())
            allMappingsByID.getValue(faceXID).color.set(pistonFaceX.cpy())
        }
    }

    inner class ColorMappingBothPistonFaces(
            id: String, val firstPiston: ColorMappingGroupedPistonFaceZ, val secondPiston: ColorMappingGroupedPistonFaceZ
    ) : ColorMappingGroup(id, listOf(firstPiston.faceXID, firstPiston.faceZID, secondPiston.faceXID, secondPiston.faceZID).map { tilesetPalette.allMappingsByID.getValue(it) },
            firstPiston.tilesetGetterFaceZ) {

        override fun applyTo(tileset: Tileset) {
            val thisColor = this.color.getOrCompute()
            firstPiston.color.set(thisColor.cpy())
            secondPiston.color.set(thisColor.cpy())
            
            firstPiston.applyTo(tileset)
            secondPiston.applyTo(tileset)
        }
    }
    
    inner class ColorMappingGroupedRodsBorder(id: String)
        : ColorMappingGroup(id,
            listOf("rodABorder", "rodDpadBorder").map { tilesetPalette.allMappingsByID.getValue(it) },
            { it.rodABorderColor }) {

        override fun applyTo(tileset: Tileset) {
            val thisColor = this.color.getOrCompute()
            affectsMappings.forEach { cm ->
                cm.color.set(thisColor.cpy())
                cm.applyTo(tileset)
            }
        }
    }
    
    inner class ColorMappingGroupedRodsFill(id: String)
        : ColorMappingGroup(id,
            listOf("rodAFill", "rodDpadFill").map { tilesetPalette.allMappingsByID.getValue(it) },
            { it.rodAFillColor }) {

        override fun applyTo(tileset: Tileset) {
            val thisColor = this.color.getOrCompute()
            affectsMappings.forEach { cm -> 
                cm.color.set(thisColor.cpy())
                cm.applyTo(tileset)
            }
        }
    }
    
}
