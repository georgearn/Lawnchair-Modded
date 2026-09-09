package app.lawnchair.smartspace.model

data class NowPlayingActions(
    val isPlaying: Boolean,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
)
