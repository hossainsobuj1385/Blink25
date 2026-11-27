package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.AccountStatus
import com.example.domain.AdminAuditLog
import com.example.domain.Announcement
import com.example.domain.BlinkValidators
import com.example.domain.ReminderProfile
import com.example.domain.ReminderSchedule
import com.example.domain.SessionSummary
import com.example.domain.SoundType
import com.example.domain.SupportRequest
import com.example.domain.SupportStatus
import com.example.domain.UserAccount
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class BlinkRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val profileSettingsService: UserProfileSettingsService = UserProfileSettingsService(db, auth)
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val BOOTSTRAPPED_ADMIN_EMAIL = "ha2420331@gmail.com"
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun isAdminUser(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)
    }

    suspend fun ensureUserBootstrapped(userId: String): Result<Unit> =
        profileSettingsService.ensureDefaultProfileSettings(userId)

    suspend fun getUserAccount(userId: String): Result<UserAccount?> =
        profileSettingsService.getUserAccountSettings(userId)

    fun observeUserAccount(userId: String): Flow<UserAccount?> =
        profileSettingsService.observeUserAccountSettings(userId)

    suspend fun getProfiles(userId: String): Result<List<ReminderProfile>> =
        profileSettingsService.getReminderProfiles(userId)

    suspend fun getProfile(userId: String, profileId: String): Result<ReminderProfile?> =
        profileSettingsService.getReminderProfile(userId, profileId)

    fun observeProfiles(userId: String): Flow<List<ReminderProfile>> =
        profileSettingsService.observeReminderProfiles(userId)

    fun observeSchedules(userId: String): Flow<List<ReminderSchedule>> = flow {
        val path = "users/$userId/schedules"
        emitAll(
            db.collection("users").document(userId).collection("schedules")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ReminderSchedule::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeSessions(userId: String): Flow<List<SessionSummary>> = flow {
        val path = "users/$userId/sessions"
        emitAll(
            db.collection("users").document(userId).collection("sessions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SessionSummary::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observePublishedAnnouncements(): Flow<List<Announcement>> = flow {
        val uid = requireUserId()
        val path = "announcements"
        emitAll(
            db.collection("announcements")
                .whereEqualTo("published", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeUserSupportRequests(userId: String): Flow<List<SupportRequest>> = flow {
        val path = "users/$userId/support_requests"
        emitAll(
            db.collection("users").document(userId).collection("support_requests")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SupportRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun updateUserSettings(userId: String, updates: Map<String, Any?>): Result<Unit> =
        profileSettingsService.updateUserSettings(userId, updates)

    suspend fun saveProfile(userId: String, profile: ReminderProfile, isNew: Boolean): Result<String> =
        profileSettingsService.storeReminderProfile(userId, profile, isNew)

    suspend fun deleteProfile(userId: String, profileId: String): Result<Unit> =
        profileSettingsService.deleteReminderProfile(userId, profileId)

    suspend fun saveSchedule(userId: String, schedule: ReminderSchedule, isNew: Boolean): Result<Unit> {
        val docId = if (isNew || schedule.id.isBlank()) {
            "sched_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        } else {
            schedule.id
        }
        val path = "users/$userId/schedules/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(schedule.scheduleName.trim().length in 1..60) { "Schedule name must be 1..60 chars" }
            require(BlinkValidators.validateTimeHHMM(schedule.startTime)) { "Invalid start time (HH:mm)" }
            require(BlinkValidators.validateTimeHHMM(schedule.endTime)) { "Invalid end time (HH:mm)" }
            require(schedule.weekdays.isNotEmpty() && schedule.weekdays.size <= 7) { "Select at least one weekday" }

            val docRef = db.collection("users").document(uid).collection("schedules").document(docId)
            if (isNew || schedule.id.isBlank()) {
                val payload = mapOf(
                    "userId" to uid,
                    "profileId" to schedule.profileId,
                    "scheduleName" to schedule.scheduleName.trim(),
                    "startTime" to schedule.startTime.trim(),
                    "endTime" to schedule.endTime.trim(),
                    "weekdays" to schedule.weekdays.distinct().sorted(),
                    "timezone" to schedule.timezone.take(64).ifBlank { "UTC" },
                    "blinkEnabled" to schedule.blinkEnabled,
                    "eyeRestEnabled" to schedule.eyeRestEnabled,
                    "enabled" to schedule.enabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "profileId" to schedule.profileId,
                        "scheduleName" to schedule.scheduleName.trim(),
                        "startTime" to schedule.startTime.trim(),
                        "endTime" to schedule.endTime.trim(),
                        "weekdays" to schedule.weekdays.distinct().sorted(),
                        "timezone" to schedule.timezone.take(64).ifBlank { "UTC" },
                        "blinkEnabled" to schedule.blinkEnabled,
                        "eyeRestEnabled" to schedule.eyeRestEnabled,
                        "enabled" to schedule.enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSchedule(userId: String, scheduleId: String): Result<Unit> {
        val path = "users/$userId/schedules/$scheduleId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(scheduleId.isNotBlank())
            db.collection("users").document(uid).collection("schedules").document(scheduleId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun recordSession(userId: String, summary: SessionSummary): Result<Unit> {
        val docId = "sess_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "users/$userId/sessions/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val payload = mapOf(
                "userId" to uid,
                "profileId" to summary.profileId.ifBlank { "work_default" },
                "profileName" to summary.profileName.trim().take(50).ifBlank { "Work" },
                "durationSeconds" to summary.durationSeconds.coerceIn(0L, 86400L),
                "blinksTriggered" to summary.blinksTriggered.coerceIn(0L, 10000L),
                "eyeRestsCompleted" to summary.eyeRestsCompleted.coerceIn(0L, 500L),
                "eyeRestsSkipped" to summary.eyeRestsSkipped.coerceIn(0L, 500L),
                "dayKey" to summary.dayKey,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).collection("sessions").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitSupportRequest(userId: String, subject: String, message: String): Result<Unit> {
        val docId = "req_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "users/$userId/support_requests/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanSubject = subject.trim()
            val cleanMessage = message.trim()
            require(cleanSubject.length in 3..100) { "Subject must be 3 to 100 characters" }
            require(cleanMessage.length in 5..1000) { "Message must be 5 to 1000 characters" }
            db.collection("users").document(uid).collection("support_requests").document(docId).set(
                mapOf(
                    "userId" to uid,
                    "subject" to cleanSubject,
                    "message" to cleanMessage,
                    "status" to SupportStatus.OPEN.name,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun exportUserDataJson(userId: String): Result<String> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val userRef = db.collection("users").document(uid)
            val userDoc = userRef.get().await()
            val profilesSnap = userRef.collection("profiles").get().await()
            val schedulesSnap = userRef.collection("schedules").get().await()
            val sessionsSnap = userRef.collection("sessions").get().await()

            val root = JSONObject().apply {
                put("exportedAtEpochMs", System.currentTimeMillis())
                put("account", JSONObject(userDoc.data ?: emptyMap<String, Any>()))
                put("profiles", JSONArray(profilesSnap.documents.map { JSONObject(it.data ?: emptyMap<String, Any>()) }))
                put("schedules", JSONArray(schedulesSnap.documents.map { JSONObject(it.data ?: emptyMap<String, Any>()) }))
                put("sessions", JSONArray(sessionsSnap.documents.map { JSONObject(it.data ?: emptyMap<String, Any>()) }))
            }
            Result.success(root.toString(2))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserData(userId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val userRef = db.collection("users").document(uid)
            for (sub in listOf("profiles", "schedules", "sessions", "support_requests")) {
                val snap = userRef.collection(sub).get().await()
                for (doc in snap.documents) {
                    doc.reference.delete().await()
                }
            }
            userRef.delete().await()
            auth.currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- ADMIN OPERATIONS (SERVER-ENFORCED BY FIRESTORE RULES) ---
    fun observeAllUsersForAdmin(): Flow<List<UserAccount>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("users")
                .snapshots()
                .map { it.toObjects(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, "users", uid)
                    throw error
                }
        )
    }

    fun observeAllAnnouncementsForAdmin(): Flow<List<Announcement>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("announcements")
                .snapshots()
                .map { it.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, "announcements", uid)
                    throw error
                }
        )
    }

    fun observeAuditLogs(): Flow<List<AdminAuditLog>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("admin_audit_logs")
                .snapshots()
                .map { it.toObjects(AdminAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, "admin_audit_logs", uid)
                    throw error
                }
        )
    }

    suspend fun setUserAccountStatus(targetUserId: String, status: AccountStatus): Result<Unit> {
        val path = "users/$targetUserId"
        return try {
            val adminUid = requireUserId()
            db.collection("users").document(targetUserId).update(
                mapOf(
                    "accountStatus" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(adminUid, "SET_ACCOUNT_STATUS", targetUserId, "Updated status to ${status.name}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createAnnouncement(title: String, message: String, published: Boolean): Result<Unit> {
        val docId = "ann_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "announcements/$docId"
        return try {
            val adminUid = requireUserId()
            require(title.trim().length in 1..120)
            require(message.trim().length in 1..1000)
            db.collection("announcements").document(docId).set(
                mapOf(
                    "title" to title.trim(),
                    "message" to message.trim(),
                    "published" to published,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(adminUid, "CREATE_ANNOUNCEMENT", docId, "Created announcement: ${title.trim()}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleAnnouncementPublished(announcementId: String, published: Boolean): Result<Unit> {
        val path = "announcements/$announcementId"
        return try {
            val adminUid = requireUserId()
            db.collection("announcements").document(announcementId).update(
                mapOf(
                    "published" to published,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(adminUid, "TOGGLE_ANNOUNCEMENT", announcementId, "Set published=$published")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun writeAuditLog(adminUid: String, action: String, targetId: String, details: String) {
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        db.collection("admin_audit_logs").document(logId).set(
            mapOf(
                "adminId" to adminUid,
                "action" to action.take(80),
                "targetUserId" to targetId.take(128),
                "details" to details.take(500),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    private fun unwrapAndLog(error: Throwable, operationType: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
