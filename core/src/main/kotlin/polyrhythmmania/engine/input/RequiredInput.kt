package polyrhythmmania.engine.input

data class RequiredInput(val beat: Float, val inputType: InputType) {
    var wasHit: Boolean = false
    var hitScore: InputScore = InputScore.MISS
}