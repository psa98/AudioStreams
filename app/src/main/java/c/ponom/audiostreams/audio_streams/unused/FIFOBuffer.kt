package c.ponom.audiostreams.audio_streams.unused

private const val BUFFER_ARRAY_SIZE=4096*1024
private const val BUFFER_SIZE=16*1024


class FIFOBuffer {
    val fifoShortBuffer = ShortArray(BUFFER_ARRAY_SIZE)

    var  posWindowStart=0

}