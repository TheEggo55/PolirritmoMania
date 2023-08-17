package polyrhythmmania.screen.mainmenu.menu

import paintbox.ui.Anchor
import paintbox.ui.area.Insets
import paintbox.ui.control.ScrollPane
import paintbox.ui.layout.HBox
import paintbox.ui.layout.VBox
import polyrhythmmania.Localization
import polyrhythmmania.Settings
import polyrhythmmania.ui.PRManiaSkins
import polyrhythmmania.world.render.ForceTexturePack
import polyrhythmmania.world.render.ForceTilesetPalette


class GraphicsSettingsMenu(menuCol: MenuCollection) : StandardMenu(menuCol) {

    private val settings: Settings = menuCol.main.settings

    init {
        this.setSize(MMMenu.WIDTH_MEDIUM)
        this.titleText.bind { Localization.getVar("mainMenu.graphicsSettings.title").use() }
        this.contentPane.bounds.height.set(300f)

        val scrollPane = ScrollPane().apply {
            Anchor.TopLeft.configure(this)
            this.bindHeightToParent(-40f)

            this.hBarPolicy.set(ScrollPane.ScrollBarPolicy.NEVER)
            this.vBarPolicy.set(ScrollPane.ScrollBarPolicy.AS_NEEDED)

            val scrollBarSkinID = PRManiaSkins.SCROLLBAR_SKIN
            this.vBar.skinID.set(scrollBarSkinID)
            this.hBar.skinID.set(scrollBarSkinID)

            this.vBar.unitIncrement.set(10f)
            this.vBar.blockIncrement.set(40f)
        }
        val hbox = HBox().apply {
            Anchor.BottomLeft.configure(this)
            this.spacing.set(8f)
            this.padding.set(Insets(4f, 0f, 2f, 2f))
            this.bounds.height.set(40f)
        }

        contentPane.addChild(scrollPane)
        contentPane.addChild(hbox)
        
        val vbox = VBox().apply {
            Anchor.TopLeft.configure(this)
            this.spacing.set(0f)
            this.bindHeightToParent(-40f)
            this.margin.set(Insets(0f, 0f, 0f, 4f))
        }
        

        vbox.temporarilyDisableLayouts {
            val (flipPane, flipCheck) = createCheckboxOption({ Localization.getVar("mainMenu.graphicsSettings.mainMenuFlipAnimation").use() })
            flipCheck.selectedState.set(main.settings.mainMenuFlipAnimation.getOrCompute())
            flipCheck.onCheckChanged = {
                main.settings.mainMenuFlipAnimation.set(it)
            }
            vbox += flipPane
            
            val (achvNotifPane, achvNotifCheck) = createCheckboxOption({ Localization.getVar("mainMenu.graphicsSettings.achievementNotifs").use() })
            achvNotifCheck.selectedState.set(main.settings.achievementNotifications.getOrCompute())
            achvNotifCheck.onCheckChanged = {
                main.settings.achievementNotifications.set(it)
            }
            vbox += achvNotifPane

            val (forceTexPackPane, forceTexPackCombobox) = createComboboxOption(ForceTexturePack.entries, main.settings.forceTexturePack.getOrCompute(),
                    { Localization.getVar("mainMenu.graphicsSettings.forceTexturePack").use() },
                    percentageContent = 0.4f, itemToString = { choice ->
                Localization.getValue("mainMenu.graphicsSettings.forceTexturePack.${
                    when (choice) {
                        ForceTexturePack.NO_FORCE -> "noForce"
                        ForceTexturePack.FORCE_GBA -> "forceGBA"
                        ForceTexturePack.FORCE_HD -> "forceHD"
                        ForceTexturePack.FORCE_ARCADE -> "forceArcade"
                    }
                }")
            })
            forceTexPackCombobox.setScaleXY(0.75f)
            forceTexPackCombobox.selectedItem.addListener { 
                main.settings.forceTexturePack.set(it.getOrCompute())
            }
            forceTexPackPane.label.tooltipElement.set(createTooltip(Localization.getVar("mainMenu.graphicsSettings.forceTexturePack.tooltip")))
            vbox += forceTexPackPane

            val (forcePalettePane, forcePaletteCombobox) = createComboboxOption(ForceTilesetPalette.entries, main.settings.forceTilesetPalette.getOrCompute(),
                    { Localization.getVar("mainMenu.graphicsSettings.forceTilesetPalette").use() },
                    percentageContent = 0.4f, itemToString = { choice ->
                Localization.getValue("mainMenu.graphicsSettings.forceTilesetPalette.${
                    when (choice) {
                        ForceTilesetPalette.NO_FORCE -> "noForce"
                        ForceTilesetPalette.FORCE_PR1 -> "redGreen"
                        ForceTilesetPalette.FORCE_PR2 -> "redBlue"
                        ForceTilesetPalette.ORANGE_BLUE -> "orangeBlue"
                    }
                }")
            })
            forcePaletteCombobox.setScaleXY(0.75f)
            forcePaletteCombobox.selectedItem.addListener { 
                main.settings.forceTilesetPalette.set(it.getOrCompute())
            }
            forcePalettePane.label.tooltipElement.set(createTooltip(Localization.getVar("mainMenu.graphicsSettings.forceTilesetPalette.tooltip")))
            vbox += forcePalettePane

            val (reducedMotionPane, reducedMotionCheck) = createCheckboxOption({ Localization.getVar("mainMenu.graphicsSettings.reducedMotion").use() })
            reducedMotionCheck.selectedState.set(main.settings.reducedMotion.getOrCompute())
            reducedMotionCheck.tooltipElement.set(createTooltip(Localization.getVar("mainMenu.graphicsSettings.reducedMotion.tooltip")))
            reducedMotionCheck.onCheckChanged = {
                main.settings.reducedMotion.set(it)
            }
            vbox += reducedMotionPane
        }

        hbox.temporarilyDisableLayouts {
            hbox += createSmallButton(binding = { Localization.getVar("common.back").use() }).apply {
                this.bounds.width.set(100f)
                this.setOnAction {
                    menuCol.popLastMenu()
                }
            }
        }
        vbox.sizeHeightToChildren(100f)
        scrollPane.setContent(vbox)
        
    }

}