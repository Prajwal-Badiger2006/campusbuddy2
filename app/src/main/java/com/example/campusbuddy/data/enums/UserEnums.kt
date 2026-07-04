package com.example.campusbuddy.data.enums

import kotlinx.serialization.Serializable

@Serializable
enum class UserStatus {
    UNVERIFIED,
    VERIFIED,
    ALUMNI,
    BANNED
}

@Serializable
enum class UserRole {
    USER,
    ADMIN
}

@Serializable
enum class RequestType(val displayName: String) {
    STUDY("Study"),
    PROJECT("Project"),
    HACKATHON("Hackathon"),
    EVENT("Event"),
    PLACEMENT("Placement")
}

@Serializable
enum class RangeType(val displayName: String) {
    CLASS("Class"),
    DEPARTMENT("Department"),
    COLLEGE("College"),
    NEARBY("Nearby Colleges"),
    ONLINE("Online")
}

@Serializable
enum class RequestStatus {
    OPEN,
    FULL,
    CLOSED,
    CANCELLED
}

@Serializable
enum class ResponseStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}

@Serializable
enum class NotificationType(val displayName: String) {
    REQUEST_RECEIVED("Request Received"),
    REQUEST_ACCEPTED("Request Accepted"),
    REQUEST_REJECTED("Request Rejected"),
    NEW_MESSAGE("New Message"),
    STREAK_MILESTONE("Streak Milestone")
}
