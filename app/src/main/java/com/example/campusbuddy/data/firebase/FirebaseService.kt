package com.example.campusbuddy.data.firebase

import android.net.Uri
import com.example.campusbuddy.data.enums.*
import com.example.campusbuddy.data.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class FirebaseService {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    // === AUTH ===
    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    suspend fun signupWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user!!
    }

    suspend fun loginWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user!!
    }

    fun signOut() = auth.signOut()

    suspend fun deleteCurrentUser() {
        auth.currentUser?.delete()?.await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    // === USER PROFILE ===
    suspend fun createUserProfile(profile: UserProfile) {
        db.collection("userProfiles").document(profile.id).set(profile).await()
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        val doc = db.collection("userProfiles").document(uid).get().await()
        return doc.toObject(UserProfile::class.java)
    }

    suspend fun updateUserProfile(uid: String, updates: Map<String, Any>) {
        db.collection("userProfiles").document(uid).update(updates).await()
    }

    suspend fun getUserProfileById(userId: String): UserProfile? {
        return getUserProfile(userId)
    }

    // === PARTNER REQUESTS ===
    private val partnerRequestRef: CollectionReference
        get() = db.collection("partnerRequests")

    suspend fun createPartnerRequest(request: PartnerRequest): Long {
        val docRef = partnerRequestRef.add(request).await()
        val id = docRef.id.hashCode().toLong()
        docRef.update("id", id).await()
        return id
    }

    suspend fun getPartnerRequests(collegeName: String, limit: Int = 10): List<PartnerRequest> {
        return partnerRequestRef
            .whereEqualTo("status", RequestStatus.OPEN.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()
            .mapNotNull { it.toObject(PartnerRequest::class.java) }
    }

    suspend fun getPartnerRequestById(requestId: Long): PartnerRequest? {
        val snapshot = partnerRequestRef.whereEqualTo("id", requestId).get().await()
        return snapshot.documents.firstOrNull()?.toObject(PartnerRequest::class.java)
    }

    suspend fun getUserRequests(creatorId: String): List<PartnerRequest> {
        return partnerRequestRef
            .whereEqualTo("creatorId", creatorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .mapNotNull { it.toObject(PartnerRequest::class.java) }
    }

    suspend fun updatePartnerRequest(requestId: Long, updates: Map<String, Any>) {
        val snapshot = partnerRequestRef.whereEqualTo("id", requestId).get().await()
        snapshot.documents.firstOrNull()?.reference?.update(updates)
    }

    // === PARTNER RESPONSES ===
    private val partnerResponseRef: CollectionReference
        get() = db.collection("partnerResponses")

    suspend fun createPartnerResponse(response: PartnerResponse): Long {
        val docRef = partnerResponseRef.add(response).await()
        val id = docRef.id.hashCode().toLong()
        docRef.update("id", id).await()
        return id
    }

    suspend fun getResponsesForRequest(requestId: Long): List<PartnerResponse> {
        return partnerResponseRef
            .whereEqualTo("requestId", requestId)
            .get()
            .await()
            .mapNotNull { it.toObject(PartnerResponse::class.java) }
    }

    suspend fun getUserResponseForRequest(responderId: String, requestId: Long): PartnerResponse? {
        val snapshot = partnerResponseRef
            .whereEqualTo("responderId", responderId)
            .whereEqualTo("requestId", requestId)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.toObject(PartnerResponse::class.java)
    }

    suspend fun updatePartnerResponse(responseId: Long, updates: Map<String, Any>) {
        val snapshot = partnerResponseRef.whereEqualTo("id", responseId).get().await()
        snapshot.documents.firstOrNull()?.reference?.update(updates)
    }

    // === MATCHES ===
    private val matchRef: CollectionReference
        get() = db.collection("matches")

    suspend fun createMatch(match: Match): Long {
        val docRef = matchRef.add(match).await()
        val id = docRef.id.hashCode().toLong()
        docRef.update("id", id).await()
        return id
    }

    suspend fun getUserMatches(userId: String): List<Match> {
        val matches1 = matchRef.whereEqualTo("user1Id", userId).get().await()
            .mapNotNull { it.toObject(Match::class.java) }
        val matches2 = matchRef.whereEqualTo("user2Id", userId).get().await()
            .mapNotNull { it.toObject(Match::class.java) }
        return matches1 + matches2
    }

    // === CONVERSATIONS ===
    private val conversationRef: CollectionReference
        get() = db.collection("conversations")

    suspend fun createConversation(conversation: Conversation): Long {
        val docRef = conversationRef.add(conversation).await()
        val id = docRef.id.hashCode().toLong()
        docRef.update("id", id).await()
        return id
    }

    suspend fun getUserConversations(userId: String): List<Conversation> {
        return conversationRef
            .whereArrayContains("memberIds", userId)
            .get()
            .await()
            .mapNotNull { it.toObject(Conversation::class.java) }
    }

    // === MESSAGES ===
    private val messageRef: CollectionReference
        get() = db.collection("messages")

    suspend fun sendMessage(message: Message): Long {
        val docRef = messageRef.add(message).await()
        val id = docRef.id.hashCode().toLong()
        docRef.update("id", id).await()
        return id
    }

    suspend fun getMessages(conversationId: Long): List<Message> {
        return messageRef
            .whereEqualTo("conversationId", conversationId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .await()
            .mapNotNull { it.toObject(Message::class.java) }
    }

    // === REAL-TIME FLOWS ===

    /**
     * Returns a [Flow] that emits the list of messages for a conversation
     * whenever a change occurs in Firestore.
     */
    fun getMessagesFlow(conversationId: Long): Flow<List<Message>> = callbackFlow {
        val subscription = messageRef
            .whereEqualTo("conversationId", conversationId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { it.toObject(Message::class.java) }
                    trySend(messages)
                }
            }
        awaitClose { subscription.remove() }
    }

    /**
     * Returns a [Flow] that emits the list of notifications for a user
     * whenever a change occurs in Firestore.
     */
    fun getNotificationsFlow(userId: String): Flow<List<Notification>> = callbackFlow {
        val subscription = notificationRef
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val notifications = snapshot.documents.mapNotNull { it.toObject(Notification::class.java) }
                    trySend(notifications)
                }
            }
        awaitClose { subscription.remove() }
    }

    /**
     * Returns a [Flow] that emits the list of matches for a user
     * whenever a change occurs in Firestore (listens to both user1Id and user2Id).
     */
    fun getMatchesFlow(userId: String): Flow<List<Match>> = callbackFlow {
        var matches1 = emptyList<Match>()
        var matches2 = emptyList<Match>()

        val reg1 = matchRef
            .whereEqualTo("user1Id", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    matches1 = snapshot.documents.mapNotNull { it.toObject(Match::class.java) }
                    trySend(matches1 + matches2)
                }
            }

        val reg2 = matchRef
            .whereEqualTo("user2Id", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    matches2 = snapshot.documents.mapNotNull { it.toObject(Match::class.java) }
                    trySend(matches1 + matches2)
                }
            }

        awaitClose {
            reg1.remove()
            reg2.remove()
        }
    }

    suspend fun markMessagesAsRead(conversationId: Long, userId: String) {
        val snapshot = messageRef
            .whereEqualTo("conversationId", conversationId)
            .whereEqualTo("isRead", false)
            .get()
            .await()
        snapshot.documents.forEach { doc ->
            val msg = doc.toObject(Message::class.java)
            if (msg?.senderId != userId) {
                doc.reference.update("isRead", true)
            }
        }
    }

    // === NOTIFICATIONS ===
    private val notificationRef: CollectionReference
        get() = db.collection("notifications")

    suspend fun createNotification(notification: Notification): Long {
        val docRef = notificationRef.add(notification).await()
        val id = docRef.id.hashCode().toLong()
        docRef.update("id", id).await()
        return id
    }

    suspend fun getUserNotifications(userId: String): List<Notification> {
        return notificationRef
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .await()
            .mapNotNull { it.toObject(Notification::class.java) }
    }

    suspend fun getUnreadNotificationCount(userId: String): Int {
        val snapshot = notificationRef
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get()
            .await()
        return snapshot.size()
    }

    suspend fun markNotificationAsRead(notificationId: Long) {
        val snapshot = notificationRef.whereEqualTo("id", notificationId).get().await()
        snapshot.documents.firstOrNull()?.reference?.update("isRead", true)
    }

    suspend fun markAllNotificationsAsRead(userId: String) {
        val snapshot = notificationRef
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get()
            .await()
        snapshot.documents.forEach { it.reference.update("isRead", true) }
    }

    // === ACTIVITY LOG ===
    private val activityLogRef: CollectionReference
        get() = db.collection("activityLogs")

    suspend fun logActivity(userId: String, activityType: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val log = ActivityLog(
            userId = userId,
            activityDate = today,
            activityType = activityType
        )
        activityLogRef.add(log).await()
    }

    suspend fun getActivityForDate(userId: String, date: String): List<ActivityLog> {
        return activityLogRef
            .whereEqualTo("userId", userId)
            .whereEqualTo("activityDate", date)
            .get()
            .await()
            .mapNotNull { it.toObject(ActivityLog::class.java) }
    }

    // === STORAGE ===
    suspend fun uploadProfilePhoto(uid: String, uri: Uri): String {
        val ref = storage.reference.child("profilePhotos/$uid")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    // === RECOMMENDED PARTNERS ===
    suspend fun getRecommendedPartners(userProfile: UserProfile, limit: Int = 5): List<UserProfile> {
        val allUsers = db.collection("userProfiles")
            .whereEqualTo("collegeName", userProfile.collegeName)
            .limit(20)
            .get()
            .await()
            .mapNotNull { it.toObject(UserProfile::class.java) }
            .filter { it.id != userProfile.id }

        // Score based on shared interests, skills, goals, availability
        return allUsers.map { user ->
            val sharedInterests = user.interests.intersect(userProfile.interests.toSet()).size
            val sharedSkills = user.skills.intersect(userProfile.skills.toSet()).size
            val sharedGoals = user.goals.intersect(userProfile.goals.toSet()).size
            val sharedAvailability = user.availability.intersect(userProfile.availability.toSet()).size
            val totalScore = (sharedInterests * 0.4f) + (sharedSkills * 0.3f) + (sharedGoals * 0.2f) + (sharedAvailability * 0.1f)
            user to totalScore
        }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
