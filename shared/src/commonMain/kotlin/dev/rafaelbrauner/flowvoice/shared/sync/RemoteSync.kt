package dev.rafaelbrauner.flowvoice.shared.sync

interface RemoteSync {
    suspend fun pull(): SyncSnapshot?
    suspend fun push(snapshot: SyncSnapshot)
}

class InMemoryRemoteSync : RemoteSync {
    var snapshot: SyncSnapshot? = null
        private set

    override suspend fun pull(): SyncSnapshot? = snapshot

    override suspend fun push(snapshot: SyncSnapshot) {
        this.snapshot = snapshot
    }
}
