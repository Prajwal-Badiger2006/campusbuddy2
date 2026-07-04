package com.example.campusbuddy.data.repository

import android.net.Uri
import com.example.campusbuddy.data.enums.*
import com.example.campusbuddy.data.firebase.FirebaseService
import com.example.campusbuddy.data.models.*
import com.google.firebase.auth.FirebaseUser
import java.text.SimpleDateFormat
import java.util.*

class CampusBuddyRepository {
    private val firebase = FirebaseService()

    // Auth
    fun getCurrentFirebaseUser(): FirebaseUser? = firebase.getCurrentUser()

    suspend fun signup(fullName: String, email: String, password: String, regNumber: String,
                        collegeName: String, department: String, year: String): Result<UserProfile> {
        return try {
            val user = firebase.signupWithEmail(email, password)
            val profile = UserProfile(
                id = user.uid,
                fullName = fullName,
                collegeEmail = email,
                registrationNumber = regNumber,
                collegeName = collegeName,
                department = department,
                year = year,
                status = UserStatus.UNVERIFIED,
                role = UserRole.USER
            )
            kotlinx.coroutines.withTimeout(10000) {
                firebase.createUserProfile(profile)
            }
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<UserProfile> {
        return try {
            val user = firebase.loginWithEmail(email, password)
            val profile = firebase.getUserProfile(user.uid) ?: throw Exception("Profile not found")
            // Update streak
            updateStreak(user.uid)
            firebase.logActivity(user.uid, "LOGIN")
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithEmail(email: String, password: String): Result<UserProfile> {
        return login(email, password)
    }

    fun logout() = firebase.signOut()

    // User Profile
    suspend fun getUserProfile(uid: String): Result<UserProfile> {
        return try {
            val profile = firebase.getUserProfile(uid) ?: throw Exception("User not found")
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(uid: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            firebase.updateUserProfile(uid, updates)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadProfilePhoto(uid: String, uri: Uri): Result<String> {
        return try {
            val url = firebase.uploadProfilePhoto(uid, uri)
            firebase.updateUserProfile(uid, mapOf("profilePhotoUrl" to url))
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Partner Requests
    suspend fun createPartnerRequest(request: PartnerRequest): Result<Long> {
        return try {
            val id = firebase.createPartnerRequest(request)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPartnerRequests(collegeName: String): Result<List<PartnerRequest>> {
        return try {
            val requests = firebase.getPartnerRequests(collegeName)
            Result.success(requests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPartnerRequestById(requestId: Long): Result<PartnerRequest> {
        return try {
            val request = firebase.getPartnerRequestById(requestId) ?: throw Exception("Request not found")
            Result.success(request)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserRequests(creatorId: String): Result<List<PartnerRequest>> {
        return try {
            val requests = firebase.getUserRequests(creatorId)
            Result.success(requests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeRequest(requestId: Long): Result<Unit> {
        return try {
            firebase.updatePartnerRequest(requestId, mapOf("status" to RequestStatus.CLOSED.name))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Partner Responses
    suspend fun respondToRequest(requestId: Long, responderId: String): Result<Unit> {
        return try {
            val response = PartnerResponse(
                requestId = requestId,
                responderId = responderId,
                status = ResponseStatus.PENDING
            )
            firebase.createPartnerResponse(response)
            // Get request to find creator
            val request = firebase.getPartnerRequestById(requestId)
            request?.let {
                firebase.createNotification(
                    Notification(
                        userId = it.creatorId,
                        type = NotificationType.REQUEST_RECEIVED,
                        title = "New Request Response",
                        body = "Someone wants to join your request",
                        referenceId = requestId.toString()
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getResponsesForRequest(requestId: Long): Result<List<PartnerResponse>> {
        return try {
            val responses = firebase.getResponsesForRequest(requestId)
            Result.success(responses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptResponse(response: PartnerResponse, requestId: Long): Result<Unit> {
        return try {
            firebase.updatePartnerResponse(response.id, mapOf("status" to ResponseStatus.ACCEPTED.name))
            // Get request
            val request = firebase.getPartnerRequestById(requestId)
            // Create match
            val match = Match(
                user1Id = request?.creatorId ?: "",
                user2Id = response.responderId,
                requestId = requestId,
                matchScore = 0.85f
            )
            firebase.createMatch(match)
            // Create conversation
            val conversation = Conversation(
                isGroup = false,
                createdBy = request?.creatorId ?: "",
                memberIds = listOf(request?.creatorId ?: "", response.responderId)
            )
            val convId = firebase.createConversation(conversation)
            // Update accepted count
            request?.let {
                val newCount = it.acceptedCount + 1
                firebase.updatePartnerRequest(requestId, mapOf("acceptedCount" to newCount))
                if (newCount >= it.neededCount) {
                    firebase.updatePartnerRequest(requestId, mapOf("status" to RequestStatus.FULL.name))
                }
            }
            // Notify responder
            firebase.createNotification(
                Notification(
                    userId = response.responderId,
                    type = NotificationType.REQUEST_ACCEPTED,
                    title = "Request Accepted!",
                    body = "Your request to join has been accepted",
                    referenceId = convId.toString()
                )
            )
            // Recalculate reliability
            recalculateReliability(request?.creatorId ?: "")
            recalculateReliability(response.responderId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectResponse(response: PartnerResponse): Result<Unit> {
        return try {
            firebase.updatePartnerResponse(response.id, mapOf("status" to ResponseStatus.REJECTED.name))
            firebase.createNotification(
                Notification(
                    userId = response.responderId,
                    type = NotificationType.REQUEST_REJECTED,
                    title = "Request Rejected",
                    body = "Your request to join was not accepted",
                    referenceId = null
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Chats
    suspend fun getUserConversations(userId: String): Result<List<Conversation>> {
        return try {
            val conversations = firebase.getUserConversations(userId)
            Result.success(conversations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessages(conversationId: Long): Result<List<Message>> {
        return try {
            val messages = firebase.getMessages(conversationId)
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(conversationId: Long, senderId: String, content: String): Result<Unit> {
        return try {
            val message = Message(
                conversationId = conversationId,
                senderId = senderId,
                content = content
            )
            firebase.sendMessage(message)
            firebase.logActivity(senderId, "MESSAGE")
            // Notify other members
            val conversation = firebase.getUserConversations(senderId).firstOrNull { it.id == conversationId }
            conversation?.memberIds?.filter { it != senderId }?.forEach { memberId ->
                firebase.createNotification(
                    Notification(
                        userId = memberId,
                        type = NotificationType.NEW_MESSAGE,
                        title = "New Message",
                        body = content.take(50),
                        referenceId = conversationId.toString()
                    )
                )
            }
            recalculateReliability(senderId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markMessagesAsRead(conversationId: Long, userId: String) {
        firebase.markMessagesAsRead(conversationId, userId)
    }

    // Notifications
    suspend fun getNotifications(userId: String): Result<List<Notification>> {
        return try {
            val notifications = firebase.getUserNotifications(userId)
            Result.success(notifications)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUnreadNotificationCount(userId: String): Int {
        return try {
            firebase.getUnreadNotificationCount(userId)
        } catch (e: Exception) { 0 }
    }

    suspend fun markNotificationAsRead(notificationId: Long) {
        firebase.markNotificationAsRead(notificationId)
    }

    suspend fun markAllNotificationsAsRead(userId: String) {
        firebase.markAllNotificationsAsRead(userId)
    }

    // Matches
    suspend fun getUserMatches(userId: String): Result<List<Match>> {
        return try {
            val matches = firebase.getUserMatches(userId)
            Result.success(matches)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Recommended Partners
    suspend fun getRecommendedPartners(profile: UserProfile): Result<List<UserProfile>> {
        return try {
            val partners = firebase.getRecommendedPartners(profile)
            Result.success(partners)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Streak
    private suspend fun updateStreak(userId: String) {
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(System.currentTimeMillis() - 86400000))
        val yesterdayActivity = firebase.getActivityForDate(userId, yesterday)
        val profile = firebase.getUserProfile(userId) ?: return
        val newStreak = if (yesterdayActivity.isNotEmpty()) profile.currentStreak + 1 else 1
        firebase.updateUserProfile(userId, mapOf("currentStreak" to newStreak))

        // Check milestones
        if (newStreak == 7 || newStreak == 14 || newStreak == 30) {
            firebase.createNotification(
                Notification(
                    userId = userId,
                    type = NotificationType.STREAK_MILESTONE,
                    title = "${newStreak}-day streak!",
                    body = "Congratulations on your ${newStreak}-day streak!",
                    referenceId = null
                )
            )
        }
    }

    // Reliability
    private suspend fun recalculateReliability(userId: String) {
        val matches = firebase.getUserMatches(userId)
        val totalInteractions = matches.size
        if (totalInteractions == 0) return

        val completedInteractions = matches.count { match ->
            val conversations = firebase.getUserConversations(userId)
            conversations.any { conv ->
                conv.memberIds.contains(userId) && conv.memberIds.contains(
                    if (match.user1Id == userId) match.user2Id else match.user1Id
                )
            }
        }

        val score = if (totalInteractions > 0) {
            (completedInteractions.toFloat() / totalInteractions) * 100
        } else 0f

        firebase.updateUserProfile(userId, mapOf("reliabilityScore" to score))
    }

    // Report User
    suspend fun reportUser(targetUserId: String, reason: String, description: String?) {
        val report = mapOf(
            "reportedUserId" to targetUserId,
            "reason" to reason,
            "description" to (description ?: ""),
            "status" to "OPEN",
            "createdAt" to System.currentTimeMillis()
        )
        // Store in a reports collection
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("reports")
            .add(report)
    }
}
