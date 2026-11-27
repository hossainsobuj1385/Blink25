package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.AccountStatus
import com.example.domain.BlinkValidators
import com.example.domain.ReminderProfile
import com.example.domain.SoundType
import com.example.domain.ThemeMode
import com.example.domain.UserAccount
import com.example.domain.ValidationResult
import com.example.domain.buildDefaultProfiles
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
import java.util.TimeZone
import java.util.UUID

/**
 * Dedicated Firestore service for storing, retrieving, observing, and validating
 * authenticated user profile settings ([UserAccount]) and reminder profiles ([ReminderProfile]).
 */
class UserProfileSettingsService(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAuthenticatedUid(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Authenticated user mismatch: access denied." }
        return uid
    }

    fun validateUserAccountStructure(account: UserAccount): ValidationResult =
        BlinkValidators.validateUserAccountStructure(account)

    fun validateSettingsUpdateMap(updates: Map<String, Any?>): ValidationResult =
        BlinkValidators.validateUserSettingsUpdates(updates)

    fun validateProfileStructure(
        profile: ReminderProfile,
        requireExistingId: Boolean = false
    ): ValidationResult = BlinkValidators.validateReminderProfileStructure(profile, requireExistingId)

    suspend fun getUserAccountSettings(userId: String): Result<UserAccount?> {
        val path = "users/$userId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val snapshot = db.collection("users").document(uid).get().await()
            val account = snapshot.toObject(
                UserAccount::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    fun observeUserAccountSettings(userId: String): Flow<UserAccount?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndLog(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    suspend fun storeUserAccountSettings(account: UserAccount): Result<Unit> {
        val path = "users/${account.userId}"
        return try {
            val uid = requireAuthenticatedUid(account.userId)
            val validation = validateUserAccountStructure(account)
            if (validation is ValidationResult.Invalid) {
                return Result.failure(IllegalArgumentException(validation.reason))
            }

            val payload = mapOf(
                "userId" to uid,
                "displayName" to account.displayName.trim(),
                "email" to account.email?.trim()?.take(120),
                "accountStatus" to account.accountStatus,
                "themeMode" to account.themeMode,
                "languageCode" to account.languageCode,
                "activeProfileId" to account.activeProfileId,
                "soundEnabled" to account.soundEnabled,
                "soundType" to account.soundType,
                "soundVolume" to account.soundVolume,
                "notificationsEnabled" to account.notificationsEnabled,
                "focusModeActive" to account.focusModeActive,
                "meetingModeActive" to account.meetingModeActive,
                "meetingModeMinutes" to account.meetingModeMinutes,
                "quietHoursEnabled" to account.quietHoursEnabled,
                "quietHoursStart" to account.quietHoursStart.trim(),
                "quietHoursEnd" to account.quietHoursEnd.trim(),
                "timezone" to account.timezone.trim(),
                "reducedMotion" to account.reducedMotion,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("users").document(uid).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserSettings(userId: String, updates: Map<String, Any?>): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val filtered = updates
                .filterKeys { it !in setOf("id", "userId", "createdAt", "accountStatus") }
                .filterValues { it != null }
            val validation = validateSettingsUpdateMap(filtered)
            if (validation is ValidationResult.Invalid) {
                return Result.failure(IllegalArgumentException(validation.reason))
            }

            val clean = mutableMapOf<String, Any>()
            for ((key, value) in filtered) {
                when (key) {
                    "displayName", "quietHoursStart", "quietHoursEnd", "timezone" ->
                        clean[key] = (value as String).trim()
                    "meetingModeMinutes" ->
                        clean[key] = (value as Number).toLong()
                    "soundVolume" ->
                        clean[key] = (value as Number).toDouble()
                    else ->
                        clean[key] = value!!
                }
            }
            clean["updatedAt"] = FieldValue.serverTimestamp()
            db.collection("users").document(uid).update(clean).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun ensureDefaultProfileSettings(userId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val userRef = db.collection("users").document(uid)
            val snap = userRef.get().await()
            if (!snap.exists()) {
                val firebaseUser = auth.currentUser
                val name = firebaseUser?.displayName?.trim()?.take(80)?.ifBlank { null }
                    ?: firebaseUser?.email?.substringBefore("@")?.trim()?.take(80)?.ifBlank { null }
                    ?: "Blink25 Member"
                val tz = TimeZone.getDefault().id.take(64).ifBlank { "UTC" }
                val defaultAccount = UserAccount(
                    userId = uid,
                    displayName = name,
                    email = firebaseUser?.email?.take(120),
                    accountStatus = AccountStatus.ACTIVE.name,
                    themeMode = ThemeMode.SYSTEM.name,
                    languageCode = "en",
                    activeProfileId = "work_default",
                    soundEnabled = true,
                    soundType = SoundType.GENTLE_BELL.name,
                    soundVolume = 0.6,
                    notificationsEnabled = true,
                    focusModeActive = false,
                    meetingModeActive = false,
                    meetingModeMinutes = 30L,
                    quietHoursEnabled = false,
                    quietHoursStart = "22:00",
                    quietHoursEnd = "07:00",
                    timezone = tz,
                    reducedMotion = false
                )
                storeUserAccountSettings(defaultAccount).getOrThrow()
            }

            val profilesCol = userRef.collection("profiles")
            val existingProfiles = profilesCol.get().await()
            if (existingProfiles.isEmpty) {
                for ((profileId, profile) in buildDefaultProfiles(uid)) {
                    val candidate = profile.copy(id = profileId, userId = uid)
                    val validation = validateProfileStructure(candidate, requireExistingId = true)
                    require(validation.isValid) {
                        (validation as? ValidationResult.Invalid)?.reason ?: "Invalid default profile structure"
                    }
                    val profilePayload = buildProfileCreatePayload(uid, candidate)
                    profilesCol.document(profileId).set(profilePayload).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun getReminderProfiles(userId: String): Result<List<ReminderProfile>> {
        val path = "users/$userId/profiles"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val snapshot = db.collection("users").document(uid).collection("profiles").get().await()
            val profiles = snapshot.toObjects(
                ReminderProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(profiles)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getReminderProfile(userId: String, profileId: String): Result<ReminderProfile?> {
        val path = "users/$userId/profiles/$profileId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val idCheck = BlinkValidators.validateDocumentId(profileId, "Profile ID")
            if (idCheck is ValidationResult.Invalid) {
                return Result.failure(IllegalArgumentException(idCheck.reason))
            }
            val snapshot = db.collection("users").document(uid)
                .collection("profiles").document(profileId)
                .get().await()
            val profile = snapshot.toObject(
                ReminderProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    fun observeReminderProfiles(userId: String): Flow<List<ReminderProfile>> = flow {
        val path = "users/$userId/profiles"
        emitAll(
            db.collection("users").document(userId).collection("profiles")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReminderProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun storeReminderProfile(
        userId: String,
        profile: ReminderProfile,
        isNew: Boolean
    ): Result<String> {
        val docId = if (isNew || profile.id.isBlank()) {
            "prof_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        } else {
            profile.id
        }
        val path = "users/$userId/profiles/$docId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val candidate = profile.copy(id = docId, userId = uid)
            val validation = validateProfileStructure(candidate, requireExistingId = true)
            if (validation is ValidationResult.Invalid) {
                return Result.failure(IllegalArgumentException(validation.reason))
            }

            val docRef = db.collection("users").document(uid).collection("profiles").document(docId)
            if (isNew || profile.id.isBlank()) {
                docRef.set(buildProfileCreatePayload(uid, candidate)).await()
            } else {
                docRef.update(buildProfileUpdatePayload(candidate)).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteReminderProfile(userId: String, profileId: String): Result<Unit> {
        val path = "users/$userId/profiles/$profileId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val idCheck = BlinkValidators.validateDocumentId(profileId, "Profile ID")
            if (idCheck is ValidationResult.Invalid) {
                return Result.failure(IllegalArgumentException(idCheck.reason))
            }
            db.collection("users").document(uid).collection("profiles").document(profileId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun buildProfileCreatePayload(uid: String, profile: ReminderProfile): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to profile.name.trim(),
        "profileType" to profile.profileType,
        "blinkIntervalSeconds" to profile.blinkIntervalSeconds,
        "earlyWarningSeconds" to profile.earlyWarningSeconds,
        "eyeRestIntervalMinutes" to profile.eyeRestIntervalMinutes,
        "eyeRestDurationSeconds" to profile.eyeRestDurationSeconds,
        "twentyTwentyTwentyEnabled" to profile.twentyTwentyTwentyEnabled,
        "soundType" to profile.soundType,
        "soundVolume" to profile.soundVolume,
        "visualReminderEnabled" to profile.visualReminderEnabled,
        "snoozeMinutes" to profile.snoozeMinutes,
        "enabled" to profile.enabled,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun buildProfileUpdatePayload(profile: ReminderProfile): Map<String, Any> = mapOf(
        "name" to profile.name.trim(),
        "profileType" to profile.profileType,
        "blinkIntervalSeconds" to profile.blinkIntervalSeconds,
        "earlyWarningSeconds" to profile.earlyWarningSeconds,
        "eyeRestIntervalMinutes" to profile.eyeRestIntervalMinutes,
        "eyeRestDurationSeconds" to profile.eyeRestDurationSeconds,
        "twentyTwentyTwentyEnabled" to profile.twentyTwentyTwentyEnabled,
        "soundType" to profile.soundType,
        "soundVolume" to profile.soundVolume,
        "visualReminderEnabled" to profile.visualReminderEnabled,
        "snoozeMinutes" to profile.snoozeMinutes,
        "enabled" to profile.enabled,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun unwrapAndLog(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
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
