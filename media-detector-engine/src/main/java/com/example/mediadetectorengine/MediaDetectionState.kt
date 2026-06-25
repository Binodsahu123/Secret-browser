package com.example.mediadetectorengine

sealed interface MediaDetectionState {
    object Idle : MediaDetectionState
    object Scanning : MediaDetectionState
    data class CandidatesFound(val candidates: List<MediaCandidateModel>) : MediaDetectionState
    object PromptShown : MediaDetectionState
    object Validating : MediaDetectionState
    object Queueing : MediaDetectionState
    object Downloading : MediaDetectionState
    object Completed : MediaDetectionState
    data class Failed(val error: String) : MediaDetectionState
    data class Unsupported(val reason: String) : MediaDetectionState
    object Blocked : MediaDetectionState
    object Recovering : MediaDetectionState
}
