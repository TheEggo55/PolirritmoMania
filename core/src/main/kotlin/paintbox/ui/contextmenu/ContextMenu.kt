package paintbox.ui.contextmenu

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Align
import paintbox.binding.FloatVar
import paintbox.binding.Var
import paintbox.binding.invert
import paintbox.font.TextAlign
import paintbox.ui.*
import paintbox.ui.area.Insets
import paintbox.ui.border.SolidBorder
import paintbox.ui.control.*
import paintbox.ui.element.RectElement
import paintbox.ui.skin.DefaultSkins
import paintbox.ui.skin.Skin
import paintbox.ui.skin.SkinFactory
import paintbox.util.gdxutils.grey


/**
 * A [ContextMenu] is a container of [MenuItem]. When shown, it computes the widths and heights of
 * its [MenuItem] children and lays them out.
 *
 * To add a root context menu, call [SceneRoot.showRootContextMenu]. To add a child menu, call [addChildMenu].
 *
 * A [ContextMenu] can spawn more sub-menus through the [Menu] menu item. As such, the [childMenu] will be set to the
 * new sub-menu and the child's [parentMenu] will be set to the parent menu.
 */
open class ContextMenu : Control<ContextMenu>() {

    companion object {
        const val SKIN_ID: String = "ContextMenu"
        const val CONTEXT_MENU_BUTTON_SKIN_ID: String = "ContextMenu_Button"

        init {
            DefaultSkins.register(SKIN_ID, SkinFactory { element: ContextMenu ->
                ContextMenuSkin(element)
            })
            DefaultSkins.register(CONTEXT_MENU_BUTTON_SKIN_ID, SkinFactory { element: Button ->
                ContextMenuButtonSkin(element)
            })
        }
    }

    val parentMenu: Var<ContextMenu?> = Var(null)
    val childMenu: Var<ContextMenu?> = Var(null)

    val defaultWidth: FloatVar = FloatVar(200f)

    var menuItems: List<MenuItem> = emptyList()
        private set

    /**
     * The list of [MenuItem]s that are currently displayed. Updated as part of [computeSize].
     */
    private var activeMenuItems: List<MenuItemMetadata> = emptyList()

    var onAddedToScene: (SceneRoot) -> Unit = {}
    var onRemovedFromScene: (SceneRoot) -> Unit = {}

    val backgroundRect: RectElement
    
    init {
        this.border.set(Insets(1f))
        this.borderStyle.set(SolidBorder(Color.BLACK))
        this.bounds.width.set(defaultWidth.get())
        this.bounds.height.set(defaultWidth.get())
        backgroundRect = RectElement(Color().grey(1f, 0.95f))
        addChild(backgroundRect)

        this.addInputEventListener { event ->
            true // Accepts any input events so the context menu doesn't get closed
        }
    }

    /**
     * Called by [SceneRoot] to compute the bounds of this context menu.
     * Generates the internal [UIElement]s for the menu items.
     */
    fun computeSize(sceneRoot: SceneRoot) {
        val currentItems = menuItems
        // TODO
        val width: FloatVar = FloatVar(defaultWidth.get())
        val metadata: List<MenuItemMetadata> = currentItems.map { item ->
            val useHovered = Var(true)
            val hovered = Var(false)
            val basePane = Pane().also { pane ->
                pane.addInputEventListener { event ->
                    if (event is MouseEntered) {
                        hovered.set(true)
                    } else if (event is MouseExited) {
                        hovered.set(false)
                    }
                    false
                }
                pane.addChild(RectElement(Color.WHITE).apply {
                    this.color.sideEffecting(Color(0.8f, 1f, 1f, 0f)) { existing ->
                        existing.a = if (useHovered.use() && hovered.use()) 0.9f else 0f
                        existing
                    }
                })
            }
            
            val contentPane: Pane = (object : Pane(), HasTooltip by HasTooltip.DefaultImpl() {
            }).also { pane ->
                pane.padding.set(Insets(6f, 6f, 8f, 8f))
                item.createTooltip.invoke(pane.tooltipElement)
            }
            basePane.addChild(contentPane)

            when (item) {
                is CustomMenuItem -> {
                    useHovered.set(false)
                    basePane.bounds.width.bind { width.useF() }
                    val contentPadding = contentPane.padding.getOrCompute()
                    val w = item.element.bounds.width.get() + contentPadding.left + contentPadding.right
                    if (width.get() < w) {
                        width.set(w)
                    }
                    val h = item.element.bounds.height.get() + contentPadding.top + contentPadding.bottom
                    basePane.bounds.height.set(h)
                    contentPane.addChild(item.element)
                }
                is Menu -> TODO() // TODO Implement sub menus
                is SeparatorMenuItem -> {
                    useHovered.set(false)
                    val panePadding = Insets(4f, 4f, 2f, 2f)
                    basePane.bounds.height.set(panePadding.top + panePadding.bottom + 1f)
                    basePane.bounds.width.bind { width.useF() }

                    contentPane.also { pane ->
                        pane.padding.set(panePadding)
                        pane.addChild(RectElement(Color.BLACK))
                    }
                }
                is LabelMenuItem -> {
                    useHovered.set(false)
                    val padding = 2f
                    val panePadding = contentPane.padding.getOrCompute()
                    item.textBlock.computeLayouts()
                    basePane.bounds.height.set(panePadding.top + panePadding.bottom + padding * 2 + item.textBlock.height)
                    basePane.bounds.width.bind { width.useF() }

                    contentPane.also { pane ->
                        pane.addChild(TextLabel("").apply {
                            this.padding.set(Insets.ZERO)
                            this.internalTextBlock.set(item.textBlock)
                            this.textAlign.set(item.textAlign)
                            this.renderAlign.set(item.renderAlign)
                        })
                    }
                }
                is SimpleMenuItem -> {
                    val padding = 2f
                    val panePadding = contentPane.padding.getOrCompute()
                    item.textBlock.computeLayouts()
                    basePane.bounds.height.set(panePadding.top + panePadding.bottom + padding * 2 + item.textBlock.height)
                    basePane.bounds.width.bind { width.useF() }

                    contentPane.also { pane ->
                        pane.addChild(Button("").apply {
                            this.padding.set(Insets.ZERO)
                            this.internalTextBlock.set(item.textBlock)
                            this.skinID.set(CONTEXT_MENU_BUTTON_SKIN_ID)
                            this.textAlign.set(TextAlign.LEFT)
                            this.renderAlign.set(Align.left)
                            this.setOnAction {
                                item.onAction.invoke()
                                if (item.closeMenuAfterAction) {
                                    Gdx.app.postRunnable {
                                        sceneRoot.hideRootContextMenu()
                                    }
                                }
                            }
                        })
                    }
                }
                is CheckBoxMenuItem -> {
                    val padding = 2f
                    val panePadding = contentPane.padding.getOrCompute()
                    item.textBlock.computeLayouts()
                    basePane.bounds.height.set(panePadding.top + panePadding.bottom + padding * 2 + item.textBlock.height)
                    basePane.bounds.width.bind { width.useF() }

                    contentPane.also { pane ->
                        pane.addChild(CheckBox("").apply {
                            this.padding.set(Insets.ZERO)
                            this.textLabel.internalTextBlock.set(item.textBlock)
                            this.textLabel.textAlign.set(TextAlign.LEFT)
                            this.textLabel.renderAlign.set(Align.left)
                            this.checkedState.set(item.checkState.getOrCompute())
                            // One-way binding on the CheckBox's side only.
                            this.checkedState.addListener { l ->
                                item.checkState.set(l.getOrCompute())
                            }
                            this.setOnAction {
                                this.checkedState.invert()
                                item.onAction.invoke()
                                if (item.closeMenuAfterAction) {
                                    Gdx.app.postRunnable {
                                        sceneRoot.hideRootContextMenu()
                                    }
                                }
                            }
                        })
                    }
                }
//                else -> error("MenuItem type was not implemented yet: ${item.javaClass.canonicalName}")
            }
            MenuItemMetadata(item, basePane)
        }

        activeMenuItems.forEach { old ->
            backgroundRect.removeChild(old.element)
        }

        var posY = 0f
        for (it in metadata) {
            val ele = it.element
            ele.bounds.y.set(posY)
            backgroundRect.addChild(ele)
            posY += ele.bounds.height.get()
        }
        activeMenuItems = metadata

        val thisBorder = this.border.getOrCompute()
        this.bounds.width.set(width.get() + thisBorder.left + thisBorder.right)
        this.bounds.height.set(posY + thisBorder.top + thisBorder.bottom)
    }

    fun addMenuItem(child: MenuItem) {
        if (child !in menuItems) {
            menuItems = menuItems + child
        }
    }

    fun removeMenuItem(child: MenuItem) {
        if (child in menuItems) {
            menuItems = menuItems - child
        }
    }

    /**
     * Adds the child menu to the scene and also connects the parent-child relationship.
     */
    fun addChildMenu(child: ContextMenu) {
        removeChildMenu()
        // Order of relationship changes should be in this order exactly: this.childMenu, child.parentMenu, sceneRoot
        // The children will also NOT be added, they have to be added later using addChildMenu
        childMenu.set(child)
        child.parentMenu.set(this)
        this.sceneRoot.getOrCompute()?.addContextMenuToScene(child)
    }

    /**
     * Removes the child menu from the scene and disconnects the parent-child relationship.
     */
    fun removeChildMenu() {
        val child = childMenu.getOrCompute()
        if (child != null) {
            // Order of relationship changes should be in this order exactly: sceneRoot, child.parentMenu, this.childMenu
            child.removeChildMenu()
            this.sceneRoot.getOrCompute()?.removeContextMenuFromScene(child)
            child.parentMenu.set(null)
            childMenu.set(null)
        }
    }

    override fun getDefaultSkinID(): String = ContextMenu.SKIN_ID

    open class ContextMenuSkin(element: ContextMenu) : Skin<ContextMenu>(element) {

        override fun renderSelf(originX: Float, originY: Float, batch: SpriteBatch) {
        }

        override fun renderSelfAfterChildren(originX: Float, originY: Float, batch: SpriteBatch) {
        }
    }

    open class ContextMenuButtonSkin(element: Button) : ButtonSkin(element) {
        init {
            this.roundedRadius.set(0)
            this.defaultBgColor.set(Color.CLEAR)
            this.hoveredBgColor.set(Color.CLEAR)
            this.pressedBgColor.set(Color.CLEAR)
            this.pressedAndHoveredBgColor.set(Color.CLEAR)
        }
    }
}

data class MenuItemMetadata(val menuItem: MenuItem, val element: UIElement)