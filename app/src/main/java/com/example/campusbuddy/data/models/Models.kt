package com.example.campusbuddy.data.models

import com.example.campusbuddy.data.enums.*
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String = "",
    val fullName: String = "",
    val collegeEmail: String = "",
    val registrationNumber: String = "",
    val collegeName: String = "",
    val department: String = "",
    val year: String = "",
    val bio: String? = null,
    val profilePhotoUrl: String? = null,
    val interests: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val availability: List<String> = emptyList(),
    val goals: List<String> = emptyList(),
    val isVerifiedStudent: Boolean = false,
    val status: UserStatus = UserStatus.UNVERIFIED,
    val role: UserRole = UserRole.USER,
    val currentStreak: Int = 0,
    val reliabilityScore: Float = 0.0f,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class PartnerRequest(
    val id: Long = 0,
    val creatorId: String = "",
    val type: RequestType = RequestType.STUDY,
    val title: String = "",
    val description: String? = null,
    val subjectOrTopic: String? = null,
    val preferredRange: RangeType = RangeType.COLLEGE,
    val preferredSkills: List<String> = emptyList(),
    val availability: String? = null,
    val neededCount: Int = 1,
    val acceptedCount: Int = 0,
    val status: RequestStatus = RequestStatus.OPEN,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class PartnerResponse(
    val id: Long = 0,
    val requestId: Long = 0,
    val responderId: String = "",
    val status: ResponseStatus = ResponseStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class Match(
    val id: Long = 0,
    val user1Id: String = "",
    val user2Id: String = "",
    val requestId: Long = 0,
    val matchScore: Float = 0.0f,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class Conversation(
    val id: Long = 0,
    val isGroup: Boolean = false,
    val createdBy: String = "",
    val memberIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class Message(
    val id: Long = 0,
    val conversationId: Long = 0,
    val senderId: String = "",
    val content: String = "",
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class Notification(
    val id: Long = 0,
    val userId: String = "",
    val type: NotificationType = NotificationType.NEW_MESSAGE,
    val title: String = "",
    val body: String = "",
    val isRead: Boolean = false,
    val referenceId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ActivityLog(
    val id: Long = 0,
    val userId: String = "",
    val activityDate: String = "",
    val activityType: String = ""
)
