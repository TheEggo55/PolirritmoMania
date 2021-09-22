package polyrhythmmania.engine

import paintbox.font.TextAlign


enum class TextBoxStyle {
    DIALOGUE, BLACK;
}

data class TextBox(val text: String, val requiresInput: Boolean,
                   val secsBeforeCanInput: Float = 0.5f,
                   val style: TextBoxStyle = TextBoxStyle.DIALOGUE, 
                   val align: TextAlign = TextAlign.LEFT) {
    fun toActive(): ActiveTextBox = ActiveTextBox(this)
}

data class ActiveTextBox(val textBox: TextBox) {
    var secondsTimer: Float = textBox.secsBeforeCanInput
    var isADown: Boolean = false
    var wasSoundInterfacePaused: Boolean = false
    
    var onComplete: (Engine) -> Unit = {}
}
