package polyrhythmmania.solitaire

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import paintbox.Paintbox
import paintbox.binding.BooleanVar
import paintbox.binding.FloatVar
import paintbox.packing.PackedSheet
import paintbox.ui.*
import paintbox.util.ColorStack
import paintbox.util.gdxutils.fillRect
import paintbox.util.gdxutils.maxX
import paintbox.util.gdxutils.maxY
import paintbox.util.gdxutils.scaleMul
import polyrhythmmania.PRManiaGame
import polyrhythmmania.statistics.GlobalStats
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min


class SolitaireGame(val deck: List<Card> = Card.STANDARD_DECK.toList().shuffled())
    : ActionablePane() {
    
    companion object {
        private const val SHOW_ALL_CARDS_DEBUG: Boolean = false
    }
    
    data class ZoneTarget(val zone: CardZone, val index: Int, val offsetX: Float, val offsetY: Float)
    
    inner class CardZone(initX: Float, initY: Float, val maxCapacity: Int, val canDragFrom: Boolean,
                         val showOutline: Boolean = true) {
        
        val x: FloatVar = FloatVar(initX)
        val y: FloatVar = FloatVar(initY)
        val stack: CardStack = CardStack(mutableListOf())
        var dontStackDown: Boolean = false
        val stackOffset: Float
            get() = if (dontStackDown || stack.flippedOver.get()) cardStackOffsetFlipped else cardStackOffset
        
        init {
            stack.x.bind { x.use() }
            stack.y.bind { y.use() }
        }
        
        fun canDragAsGroup(startIndex: Int): Boolean {
            if (startIndex !in stack.cardList.indices) return false
            
            return checkStackingRules(stack.cardList.drop(startIndex))
        }
    }
    
    inner class DragInfo {
        
        val draggingStack: CardStack = CardStack(mutableListOf())
        
        // Active when dragging
        var oldZone: CardZone? = null
        val offset: Vector2 = Vector2(0f, 0f)
        
        fun isDragging(): Boolean {
            return oldZone != null
        }
        
        fun cancelDrag() {
            val myList = draggingStack.cardList.toList()
            draggingStack.cardList.clear()
            oldZone?.stack?.cardList?.addAll(myList)
            oldZone = null
            playPutdownSound()
        }
        
        private fun playPutdownSound() {
            playSound("sfx_card_putdown", pitch = 1.2f)
        }
        
        fun endDrag(newZone: CardZone) {
            val myList = draggingStack.cardList.toList()
            
            draggingStack.cardList.clear()
            newZone.stack.cardList += myList
            oldZone = null
            playPutdownSound()
            
            if (myList.isNotEmpty()) {
                GlobalStats.solitaireMovesMade.increment()
            }
            
            if (myList.size == 1 && newZone in foundationZones) {
                playSound(myList.last().symbol.getNoteSFXID())
            }
            
            checkTableauAfterDrag()
        }
        
        fun startDrag(zoneTarget: ZoneTarget) {
            val myList = draggingStack.cardList
            myList.clear()
            val zoneCardList = zoneTarget.zone.stack.cardList
            val newSet = zoneCardList.drop(zoneTarget.index)
            
            if (!checkStackingRules(newSet)) {
                return
            }
            
            myList.addAll(newSet)
            repeat(newSet.size) {
                zoneCardList.removeAt(zoneTarget.index)
            }
            
            oldZone = zoneTarget.zone
            offset.set(zoneTarget.offsetX, zoneTarget.offsetY)
            updateDrag()
            
            playSound("sfx_card_pickup", pitch = 1.2f)
        }
        
        fun updateDrag() {
            draggingStack.x.set(lastMouseRelative.x - offset.x)
            draggingStack.y.set(lastMouseRelative.y - offset.y)
        }
    }
    
    inner class CardMoveAnimation(val card: Card, var fromX: Float, var fromY: Float, var toX: Float, var toY: Float,
                                  val fromZone: CardZone?, val targetZone: CardZone?, val duration: Float) {
        var secondsElapsed: Float = 0f
        var currentX: Float = fromX
        var currentY: Float = fromY
        var onComplete: () -> Unit = {}
        var flippedOver: Boolean = false
    }
    
    inner class EnqueuedAnimation(val card: Card, val from: CardZone, val to: CardZone, val duration: Float,
                                  val delay: Float, val under: Boolean,
                                  var onComplete: () -> Unit)

    private val lastMouseAbsolute: Vector2 = Vector2()
    private val lastMouseRelative: Vector2 = Vector2()
    
    val cardWidth: Float = 64f
    val cardHeight: Float = 80f
    val cardStackOffset: Float = 20f
    val cardStackOffsetFlipped: Float = -0.5f
    
    val inputsEnabled: BooleanVar = BooleanVar(false)
    
    private val freeCells: List<CardZone>
    private val playerZones: List<CardZone>
    private val dealZone: CardZone
    private val spareZone: CardZone
    private val foundationZones: List<CardZone>
    
    private val placeableCardZones: List<CardZone>
    private val allCardZones: List<CardZone>
    private val dragInfo: DragInfo = DragInfo()
    
    private val animationQueue: MutableList<EnqueuedAnimation> = mutableListOf()
    private var maxConcurrentAnimations: Int = 1
    private var currentAnimations: List<CardMoveAnimation> = listOf(
        CardMoveAnimation(Card(CardSuit.A, CardSymbol.WIDGET_HALF), -10000f, -10000f, -10000f, -10000f, null, null, duration = 0.75f).apply {
            // Short pause before dealing cards
            val dealSfxID = "sfx_card_deal"
            val dealSfx = SolitaireAssets.get<Sound>(dealSfxID)
            dealSfx.stop()
            this.onComplete = {
                playSound(dealSfxID, pitch = 0.70f)
            }
        }
    )
    private var gameWon: Boolean = false
    
    val gameListeners: MutableList<SolitaireGameListener> = mutableListOf()
    
    init {
        this.bounds.width.set(800f)
        this.bounds.height.set(400f)
        
        // Board is 12.5 units wide (800/64) and 5 units tall (400/80)
        val zoneSpacingX = 0.5f
        val zoneSpacingY = 1f / 3f
        
        freeCells = listOf(
                CardZone((1 + zoneSpacingX) * 0, 0f, 1, true),
                CardZone((1 + zoneSpacingX) * 1, 0f, 1, true),
                CardZone((1 + zoneSpacingX) * 2, 0f, 1, true),
        )
        playerZones = listOf(
                CardZone((1 + zoneSpacingX) * 0, (1 + zoneSpacingY), 999, true),
                CardZone((1 + zoneSpacingX) * 1, (1 + zoneSpacingY), 999, true),
                CardZone((1 + zoneSpacingX) * 2, (1 + zoneSpacingY), 999, true),
                CardZone((1 + zoneSpacingX) * 3, (1 + zoneSpacingY), 999, true),
                CardZone((1 + zoneSpacingX) * 4, (1 + zoneSpacingY), 999, true),
//                CardZone((1 + zoneSpacingX) * 5, (1 + zoneSpacingY), 999, true),
        )
        dealZone = CardZone((1 + zoneSpacingX) * 4.5f, 0f, 2, false, showOutline = false).apply { 
            this.stack.flippedOver.set(true)
        }
        spareZone = CardZone((1 + zoneSpacingX) * 3.5f, 0f, 3, false).apply { 
            this.dontStackDown = true
        }
        val numOfScaleCards = CardSymbol.SCALE_CARDS.size + 1 /* SP card */
        foundationZones = mutableListOf(
                CardZone((1 + zoneSpacingX) * (playerZones.size + 0.5f), 0f, numOfScaleCards, false),
                CardZone((1 + zoneSpacingX) * (playerZones.size + 0.5f), 0f, numOfScaleCards, false),
                CardZone((1 + zoneSpacingX) * (playerZones.size + 0.5f), 0f, numOfScaleCards, false),
        ).apply {
            val totalHeight = this.size * 1 + (this.size - 1) * zoneSpacingY
            this.forEachIndexed { index, zone -> 
                zone.dontStackDown = true
                zone.y.set((5f - totalHeight) / 2 + index * (1 + zoneSpacingY))
            }
        }
        
        placeableCardZones = freeCells + playerZones + foundationZones
        allCardZones = freeCells + playerZones + spareZone + dealZone + foundationZones
        
        // Horizontal center
        val totalWidth = (allCardZones.maxOf { it.x.get() } + 1) - allCardZones.minOf { it.x.get() }
        val hcOffset = (12.5f - totalWidth) / 2
        allCardZones.forEach { 
            it.x.set(it.x.get() + hcOffset)
        }
        
        // Convert card zones from "units" to px
        allCardZones.forEach { zone: CardZone ->
            zone.x.set(zone.x.get() * cardWidth)
            zone.y.set(zone.y.get() * cardHeight)
        }
    }
    
    init {
        this.doClipping.set(true)
        
        addInputEventListener { event ->
            if (inputsEnabled.get()) {
                if (event is MouseInputEvent) {
                    val x = event.x
                    val y = event.y
                    lastMouseAbsolute.set(x, y)
                    val thisPos = this.getPosRelativeToRoot(lastMouseRelative)
                    lastMouseRelative.x = x - thisPos.x
                    lastMouseRelative.y = y - thisPos.y
                }
                
                inputListener(event)
            } else false
        }
    }
    
    init {
        dealZone.stack.cardList.addAll(deck)
        var index = 0
        dealZone.stack.cardList.forEach { card -> 
            val targetZone = if (card.symbol == CardSymbol.SPARE)
                spareZone 
            else playerZones[index++ % playerZones.size]
            enqueueAnimation(card, dealZone, targetZone, duration = 0.1f)
        }
    }
    
    private fun inputListener(event: InputEvent): Boolean {
        return when (event) {
            is TouchDown -> {
                if (event.button == Input.Buttons.LEFT) {
                    if (!dragInfo.isDragging()) {
                        // Check if clicking on any of the zones
                        val selected = getSelectedCardIndex()
                        if (selected != null && selected.zone.canDragFrom && !selected.zone.stack.flippedOver.get()) {
                            dragInfo.startDrag(selected)
                        }
                    }
                } else if (event.button == Input.Buttons.RIGHT && dragInfo.isDragging()) {
                    dragInfo.cancelDrag()
                }

                true
            }
            is TouchDragged -> {
                if (dragInfo.isDragging()) {
                    dragInfo.updateDrag()
                }
                false // TouchDragged should not be consumed
            }
            is ClickReleased -> {
                if (event.button == Input.Buttons.LEFT && dragInfo.isDragging()) {
                    val nearestZone = getNearestOverlappingDragZone()
                    if (nearestZone == null || nearestZone == dragInfo.oldZone || !canPlaceDragOn(nearestZone)) {
                        dragInfo.cancelDrag()
                    } else {
                        dragInfo.endDrag(nearestZone)
                    }
                }
                
                false // ClickReleased always never consumes
            }
            else -> false
        }
    }
    
    private fun checkTableauAfterDrag() {
        if (gameWon) {
            return
        }
        
        // Flip over completed widgets in free cells
        for (freeCell in freeCells) {
            if (!freeCell.stack.flippedOver.get() && freeCell.stack.isWidgetSet()) {
                freeCell.stack.flippedOver.set(true)
                playSound("sfx_flick", vol = 0.65f)
            }
        }
        for (foundation in foundationZones) {
            if (!foundation.stack.flippedOver.get() && foundation.stack.cardList.size == foundation.maxCapacity) {
                foundation.stack.flippedOver.set(true)
            }
        }
        
        // Game complete check
        if ((freeCells + foundationZones).all { z -> z.stack.flippedOver.get() }) {
            gameWon = true
            inputsEnabled.set(false)
            playSound("sfx_win", vol = 0.75f)
            gameListeners.forEach { it.onWin() }
            GlobalStats.solitaireGamesWon.increment()
            // Falldown animation
            maxConcurrentAnimations = Int.MAX_VALUE
            val affectedZones = freeCells + foundationZones + playerZones
            affectedZones.forEach { zone ->
                val invisibleZone = CardZone(zone.x.get(), zone.y.get() + 6f * cardHeight, 999, false, showOutline = false)
                val delayPer = 0.175f
                zone.stack.cardList.asReversed().forEachIndexed { index, card ->
                    enqueueAnimation(card, zone, invisibleZone, duration = 0.75f, delay = delayPer * index, isUnder = true)
                }
            }
            return
        }
        
        // Possible animations for auto-placing into the foundation pile
        val zones = playerZones + freeCells
        for (zone in zones) {
            // Check if last item in the zone can be put in the foundation pile
            // Other cards cannot be played on top of it, and if its value is 3 or greater, all the cards with one less value must ALREADY be in the foundation
            if (zone.canDragFrom && zone.stack.cardList.isNotEmpty()) {
                val tail = zone.stack.cardList.last()
                if (tail.symbol == CardSymbol.SPARE) {
                    // If it is the spare card, move it immediately to the spare zone
                    inputsEnabled.set(false)
                    dragInfo.cancelDrag()
                    enqueueAnimation(tail, zone, spareZone)
                    return
                } else if (!tail.symbol.isNumeric()) {
                    continue
                }

                
                val targetFoundation = foundationZones.firstOrNull { fz ->
                    if (tail.symbol.scaleOrder == 0) { // 1
                        fz.stack.cardList.isEmpty()
                    } else {
                        val lastInFoundation = fz.stack.cardList.lastOrNull()
                        lastInFoundation != null && lastInFoundation.suit == tail.suit && lastInFoundation.symbol.scaleOrder == tail.symbol.scaleOrder - 1
                    }
                }
                
                val canMoveToFoundation: Boolean = when (tail.symbol) {
                    CardSymbol.NUM_1 -> true
                    CardSymbol.NUM_2 -> true
                    else -> {
                        // All other cards with value one less than tail should ALREADY be in a foundation
                        // AKA: no cards with value one less than tail will be in the free zone/player zones
                        zones.all { z ->
                            z.stack.cardList.none { c ->
                                c.symbol.scaleOrder == tail.symbol.scaleOrder - 1
                            }
                        }
                    }
                }
                
                if (targetFoundation != null && canMoveToFoundation) {
                    inputsEnabled.set(false)
                    dragInfo.cancelDrag()
                    enqueueAnimation(tail, zone, targetFoundation) {
                        playSound(tail.symbol.getNoteSFXID())
                    }
                    return
                }
            }
        }
        
        // Check if a spare card can be placed to cap a foundation
        if (spareZone.stack.cardList.isNotEmpty()) {
            val tail = spareZone.stack.cardList.last()
            val tailSuit = tail.suit
            val targetFoundation = foundationZones.firstOrNull { 
                val top = it.stack.cardList.lastOrNull()
                top != null && top.suit == tailSuit && top.symbol == CardSymbol.SCALE_CARDS.first()
            }
            if (targetFoundation != null) {
                inputsEnabled.set(false)
                dragInfo.cancelDrag()
                enqueueAnimation(tail, spareZone, targetFoundation, duration = 0.333f) {
                    playSound(CardSymbol.SPARE.getNoteSFXID())
                }
                return
            }
        }
        
    }
    
    fun checkStackingRules(stack: List<Card>): Boolean {
        stack.forEachIndexed { index, card -> 
            if (index > 0) {
                val prevCard = stack[index - 1]
                
                if (prevCard.symbol.isWidgetLike()) {
                    // Only alternating-symbol widgets can be here, with same suit
                    if (!(card.symbol.isWidgetLike() && card.symbol != prevCard.symbol && prevCard.suit == card.suit)) {
                        return false
                    }
                } else {
                    // Non-WIDGET cards: must be alternating suit and directly one up in DESCENDING scale order
                    if (!(prevCard.suit != card.suit && !card.symbol.isWidgetLike() && prevCard.symbol.scaleOrder - 1 == card.symbol.scaleOrder)) {
                        return false
                    }
                }
            }
        }
        return true
    }
    
    private fun canPlaceDragOn(targetZone: CardZone): Boolean {
        // Can't place if flipped over
        if (targetZone.stack.flippedOver.get()) {
            return false
        }
        
        val dragStack = dragInfo.draggingStack
        val dragStackList = dragStack.cardList
        
        // Special exception when dragging a widget set to a free cell
        if (dragStackList.size == 3 && targetZone in freeCells && targetZone.stack.cardList.isEmpty()) {
            if (dragStack.isWidgetSet()) {
                return true
            }
        }
        
        // Foundation pile check
        if (targetZone in foundationZones) {
            if (dragStackList.size != 1) {
                return false
            }
            val dragItem = dragStackList.first()
            if (targetZone.stack.cardList.isEmpty()) {
                // Can only drag to an empty one if the dragItem has scaleOrder = 0 and if that suit isn't already in another foundation
                if (!(dragItem.symbol.scaleOrder == 0 && foundationZones.none { z -> z.stack.cardList.firstOrNull()?.suit == dragItem.suit })) {
                    return false
                }
            } else {
                val lastInTargetZone = targetZone.stack.cardList.last()
                return dragItem.suit == lastInTargetZone.suit && lastInTargetZone.symbol.scaleOrder == dragItem.symbol.scaleOrder - 1 
            }
        }
        
        // Can't place if exceeds capacity
        if (dragStackList.size + targetZone.stack.cardList.size > targetZone.maxCapacity) {
            return false
        }
        
        return checkStackingRules(listOfNotNull(targetZone.stack.cardList.lastOrNull()) + dragStackList)
    }
    
    private fun CardStack.isWidgetSet(): Boolean {
        return this.cardList.size == 3 && this.cardList[0].symbol == CardSymbol.WIDGET_HALF &&
                this.cardList[1].symbol == CardSymbol.ROD && this.cardList[2].symbol == CardSymbol.WIDGET_HALF
    }
    
    private fun getNearestOverlappingDragZone(): CardZone? {
        var nearest: CardZone? = null
        var mostArea = 0f
        
        val dragX = dragInfo.draggingStack.x.get()
        val dragY = dragInfo.draggingStack.y.get()
        val dragW = cardWidth
        val dragH = cardHeight // Only the topmost card of the stack counts for area checking
        val dragRect = Rectangle(dragX, dragY, dragW, dragH)

        for (zone in placeableCardZones) {
            val zoneRect = Rectangle(zone.x.get(), zone.y.get(), cardWidth, cardHeight + (zone.maxCapacity - 1) * zone.stackOffset)
            if (!dragRect.overlaps(zoneRect)) continue
            
            val minX = max(dragRect.x, zoneRect.x)
            val minY = max(dragRect.y, zoneRect.y)
            val maxX = min(dragRect.maxX, zoneRect.maxX)
            val maxY = min(dragRect.maxY, zoneRect.maxY)
            val overlap = Rectangle(minX, minY, maxX - minX, maxY - minY)
            val area = overlap.area()
            
            if (area > mostArea) {
                mostArea = area
                nearest = zone
            }
        }
        
        return nearest
    }
    
    private fun getSelectedCardIndex(): ZoneTarget? {
        for (zone in allCardZones) {
            val stack = zone.stack
            val cardList = stack.cardList
            if (cardList.isEmpty()) continue

            // Height of the zone is cardHeight + (n - 1) * cardStackOffset 
            val cardStackOffset = zone.stackOffset
            val cardHeightMinusOffset = cardHeight - cardStackOffset
            if (lastMouseRelative.x in zone.x.get()..(zone.x.get() + cardWidth) &&
                    lastMouseRelative.y in zone.y.get()..(zone.y.get() + (cardList.size * cardStackOffset) + cardHeightMinusOffset)) {
                val cardIndex = floor((lastMouseRelative.y - zone.y.get()) / cardStackOffset).toInt().coerceIn(0, cardList.size - 1)
                return ZoneTarget(zone, cardIndex, lastMouseRelative.x - zone.x.get(), lastMouseRelative.y - (zone.y.get() + cardIndex * cardStackOffset))
            }
        }
        
        return null
    }
    
    private fun enqueueAnimation(card: Card, fromZone: CardZone, toZone: CardZone, duration: Float = 0.25f,
                                 delay: Float = 0f, isUnder: Boolean = false, onComplete: () -> Unit = {}) {
        animationQueue += EnqueuedAnimation(card, fromZone, toZone, duration, delay, isUnder, onComplete)
    }

    override fun renderSelf(originX: Float, originY: Float, batch: SpriteBatch) {
        // Animations
        val currentAnimationList = this.currentAnimations
        if (currentAnimationList.size < maxConcurrentAnimations) {
            if (animationQueue.isNotEmpty()) {
                val next = animationQueue.removeFirst()
                val fromCardList = next.from.stack.cardList
                val newAnimation = CardMoveAnimation(next.card,
                        next.from.x.get(), next.from.y.get() + (fromCardList.size) * next.from.stackOffset,
                        next.to.x.get(), next.to.y.get() + (next.to.stack.cardList.size) * next.to.stackOffset,
                        next.from, next.to, next.duration).apply {
                    this.onComplete = next.onComplete
                    this.secondsElapsed = -next.delay
                    this.flippedOver = next.from.stack.flippedOver.get()
                }
                if (next.under) {
                    this.currentAnimations = listOf(newAnimation) + this.currentAnimations
                } else {
                    this.currentAnimations = this.currentAnimations + newAnimation
                }
                inputsEnabled.set(false)
            }
        }

        this.currentAnimations.forEach { currentAnimation ->
            val oldSeconds = currentAnimation.secondsElapsed
            currentAnimation.secondsElapsed = (currentAnimation.secondsElapsed + Gdx.graphics.deltaTime)
            if (oldSeconds <= 0f && currentAnimation.secondsElapsed > 0) {
                if (currentAnimation.targetZone != null) {
                    currentAnimation.toX = currentAnimation.targetZone.x.get()
                    currentAnimation.toY = currentAnimation.targetZone.y.get() + (currentAnimation.targetZone.stack.cardList.size) * currentAnimation.targetZone.stackOffset
                }
                
                val fromCardList = currentAnimation.fromZone?.stack?.cardList
                if (fromCardList != null) {
                    currentAnimation.fromX = currentAnimation.fromZone.x.get()
                    currentAnimation.fromY = currentAnimation.fromZone.y.get() + (fromCardList.size - 1) * currentAnimation.fromZone.stackOffset
                    
                    val index = fromCardList.lastIndexOf(currentAnimation.card)
                    if (index >= 0) {
                        fromCardList.removeAt(index)
                    }
                }
            }

            val progress = if (currentAnimation.duration <= 0f) 1f else (currentAnimation.secondsElapsed / currentAnimation.duration).coerceIn(0f, 1f)
            val interpolation = Interpolation.linear
            currentAnimation.currentX = interpolation.apply(currentAnimation.fromX, currentAnimation.toX, progress)
            currentAnimation.currentY = interpolation.apply(currentAnimation.fromY, currentAnimation.toY, progress)

            if (progress >= 1f) {
                this.currentAnimations -= currentAnimation

                // Add to that zone
                if (currentAnimation.targetZone != null) {
                    currentAnimation.targetZone.stack.cardList += currentAnimation.card
                }

                currentAnimation.onComplete()

                if (animationQueue.isEmpty()) {
                    checkTableauAfterDrag()
                    if (!gameWon) {
                        inputsEnabled.set(true)
                    }
                }
            }
        }
        
        val renderBounds = this.paddingZone
        val x = renderBounds.x.get() + originX
        val y = originY - renderBounds.y.get()
        val w = renderBounds.width.get()
        val h = renderBounds.height.get()
        val lastPackedColor = batch.packedColor

        val opacity: Float = this.apparentOpacity.get()
        val tmpColor: Color = ColorStack.getAndPush().set(1f, 1f, 1f, 1f)
        tmpColor.a *= opacity
        
        batch.color = tmpColor
//        batch.fillRect(x, y - h, w, h) // From RectElement
        
        val paintboxFont = PRManiaGame.instance.fontMainMenuMain
        val bmFont = paintboxFont.begin()
        bmFont.scaleMul(0.75f)

        val sheet = SolitaireAssets.get<PackedSheet>("cards")
        for (zone in allCardZones) {
            if (!zone.showOutline) continue
            val cs = zone.stack
            val renderX = x + cs.x.get()
            val renderY = y - cs.y.get()

            batch.setColor(1f, 1f, 1f, 1f)
            batch.draw(sheet["zone_outline"], renderX, renderY - cardHeight, cardWidth, cardHeight)
        }
        
        batch.color = tmpColor
        for (zone in allCardZones) {
            val cs = zone.stack
            val renderX = x + cs.x.get()
            val renderY = y - cs.y.get()

            renderCardStack(renderX, renderY, batch, cs, zone.stackOffset, bmFont)
        }
        val dragStack = dragInfo.draggingStack
        renderCardStack(x + dragStack.x.get(), y - dragStack.y.get(), batch, dragStack, cardStackOffset, bmFont)
        
        if (currentAnimations.isNotEmpty()) {
            for (it in currentAnimations.asReversed()) {
                if (it.secondsElapsed < 0f) continue
                renderCard(x + it.currentX, y - it.currentY, batch, it.card, it.flippedOver, bmFont)
            }
        }
        
        // Show all cards debug
        if (SHOW_ALL_CARDS_DEBUG && Paintbox.uiDebugOutlines.getOrCompute() == Paintbox.UIDebugOutlineMode.ONLY_VISIBLE) {
            batch.fillRect(x, y - h, w, h)
            
            val deck = Card.STANDARD_DECK
            val cardsPerRow = 10
            deck.forEachIndexed { index, card ->
                val col = (index + 1) % cardsPerRow
                val row = (index + 1) / cardsPerRow
                renderCard(4f + x + col * (cardWidth + 1), y - 4f - row * (cardHeight + 1), batch, card, false, bmFont)
            }
            renderCard(4f + x, y - 4f, batch, deck.first(), true, bmFont)
        }
        
        paintboxFont.end()

        ColorStack.pop()
        batch.packedColor = lastPackedColor
        
        // Right click to cancel when NOT clicking this element
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT) && dragInfo.isDragging()) {
            dragInfo.cancelDrag()
        }
    }
    
    private fun renderCardStack(x: Float, y: Float, batch: SpriteBatch, cardStack: CardStack, stackOffset: Float,
                                font: BitmapFont) {
        val flippedOver = cardStack.flippedOver.get()
        cardStack.cardList.forEachIndexed { index, card ->
            renderCard(x, y - index * stackOffset, batch, card, flippedOver, font)
        }
    }

    private fun renderCard(x: Float, y: Float, batch: SpriteBatch, card: Card, flippedOver: Boolean, font: BitmapFont) {
        val lastPackedColor = batch.packedColor

        val sheet = SolitaireAssets.get<PackedSheet>("cards")
        val cw = cardWidth
        val ch = cardHeight
        val renderX = x
        val renderY = (y - ch)
        if (flippedOver) {
            batch.setColor(1f, 1f, 1f, 1f)
            batch.draw(sheet["card_back"], renderX, renderY, cw, ch)
        } else {
            batch.setColor(1f, 1f, 1f, 1f)
            batch.draw(sheet["card_front"], renderX, renderY, cw, ch)
            
            val indent = 7f
            val symbolIndentX = indent + card.symbol.offsetX
            val symbolIndentY = indent + card.symbol.offsetY
            val suitIndent = indent
            batch.color = card.suit.color
            // Symbol in top left and upside-down bottom right
            val symbolRegion = sheet[card.symbol.spriteID]
            batch.draw(symbolRegion, renderX + symbolIndentX, renderY + ch - symbolIndentY - symbolRegion.originalHeight)
            batch.draw(symbolRegion, renderX + cw - (symbolIndentX), renderY + symbolIndentY + symbolRegion.originalHeight,
                    -symbolRegion.originalWidth * 1f, -symbolRegion.originalHeight * 1f)
            // Suit in top right and upside-down bottom left
            val suitRegion = sheet[card.suit.spriteID]
            batch.draw(suitRegion, renderX + cw - (suitIndent + suitRegion.originalWidth), renderY + ch - suitIndent - suitRegion.originalHeight)
            batch.draw(suitRegion, renderX + suitIndent + suitRegion.originalWidth, renderY + suitIndent + suitRegion.originalHeight,
                    -suitRegion.originalWidth * 1f, -suitRegion.originalHeight * 1f)
        }

        batch.packedColor = lastPackedColor
    }

    private fun playSound(id: String, vol: Float = 1f, pitch: Float = 1f, pan: Float = 0f) {
        val main = PRManiaGame.instance
        if (main.settings.solitaireSFX.getOrCompute()) {
            main.playMenuSfx(SolitaireAssets.get<Sound>(id), vol, pitch, pan)
        }
    }
}