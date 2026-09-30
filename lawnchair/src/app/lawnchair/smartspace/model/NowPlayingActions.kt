package app.lawnchair.smartspace.model

/**
 * Equality only considers [isPlaying]: the callbacks are recreated on every media update,
 * and comparing them would make every update look like a change and rebind the card.
 */
class NowPlayingActions(
    val isPlaying: Boolean,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
) {
    override fun equals(other: Any?): Boolean = other is NowPlayingActions && other.isPlaying == isPlaying

    override fun hashCode(): Int = isPlaying.hashCode()
}
