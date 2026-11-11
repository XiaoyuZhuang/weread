package com.xiaoyu.weread

import java.util.concurrent.CancellationException

/** Cancellation belongs to one question, including all of its HTTP stages. */
internal class RequestSession {
    @Volatile var isCancelled = false
        private set
    private var cancelCall: (() -> Unit)? = null

    @Synchronized fun attach(cancel: () -> Unit) {
        if (isCancelled) {
            cancel()
            throw CancellationException("Question replaced")
        }
        cancelCall = cancel
    }

    @Synchronized fun detach() { cancelCall = null }

    @Synchronized fun cancel() {
        isCancelled = true
        cancelCall?.invoke()
        cancelCall = null
    }

    fun checkActive() {
        if (isCancelled) throw CancellationException("Question replaced")
    }
}
