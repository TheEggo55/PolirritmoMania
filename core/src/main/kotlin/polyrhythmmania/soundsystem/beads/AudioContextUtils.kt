package polyrhythmmania.soundsystem.beads


import net.beadsproject.beads.core.AudioContext


/**
 * Gets all the averaged values for the current out buffer of the context.
 */
fun AudioContext.getValues(buffer: FloatArray) {
    if (buffer.size != this.bufferSize)
        error("Buffer size incorrect, got ${buffer.size}, should be ${this.bufferSize}")

    for (channel in 0..<out.ins) {
        for (i in buffer.indices) {
            if (channel == 0)
                buffer[i] = 0f
            buffer[i] += out.getValue(channel, i)

            if (i == buffer.size - 1) {
                buffer[i] = buffer[i] / out.ins
            }
        }
    }
}

/**
 * Gets all the values for the given channel for the current out buffer of the context.
 */
fun AudioContext.getValues(buffer: FloatArray, channel: Int) {
    if (buffer.size != this.bufferSize)
        error("Buffer size incorrect, got ${buffer.size}, should be ${this.bufferSize}")

    for (i in buffer.indices) {
        buffer[i] = out.getValue(channel, i)
    }
}
