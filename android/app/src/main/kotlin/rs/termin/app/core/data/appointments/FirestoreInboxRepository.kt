package rs.termin.app.core.data.appointments

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import rs.termin.app.core.data.firebase.snapshots
import rs.termin.app.core.data.firebase.toInboxMessage
import rs.termin.app.core.domain.model.InboxMessage
import rs.termin.app.core.domain.repository.InboxRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreInboxRepository @Inject constructor(private val firestore: FirebaseFirestore) : InboxRepository {

    private fun inbox(uid: String) = firestore.collection("users").document(uid).collection("inbox")

    override fun observeInbox(uid: String): Flow<List<InboxMessage>> =
        inbox(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(LIMIT).snapshots()
            .map { snap -> snap.documents.map { it.toInboxMessage() } }

    override suspend fun markRead(uid: String, messageId: String) {
        inbox(uid).document(messageId).update("read", true).await()
    }

    override suspend fun markAllRead(uid: String, ids: List<String>) {
        if (ids.isEmpty()) return
        val batch = firestore.batch()
        ids.forEach { batch.update(inbox(uid).document(it), "read", true) }
        batch.commit().await()
    }

    override suspend fun delete(uid: String, messageId: String) {
        inbox(uid).document(messageId).delete().await()
    }

    private companion object {
        const val LIMIT = 50L
    }
}
