const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_ha";
const ADMIN_EMAIL = "ha2420331@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPayload(uid) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    displayName: "Alice",
    email: "alice@example.com",
    accountStatus: "ACTIVE",
    themeMode: "SYSTEM",
    languageCode: "en",
    activeProfileId: "work_default",
    soundEnabled: true,
    soundType: "GENTLE_BELL",
    soundVolume: 0.6,
    notificationsEnabled: true,
    focusModeActive: false,
    meetingModeActive: false,
    meetingModeMinutes: 30,
    quietHoursEnabled: false,
    quietHoursStart: "22:00",
    quietHoursEnd: "07:00",
    timezone: "UTC",
    reducedMotion: false,
    createdAt: now,
    updatedAt: now,
  };
}

function validProfilePayload(uid) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    name: "Work",
    profileType: "WORK",
    blinkIntervalSeconds: 25,
    earlyWarningSeconds: 5,
    eyeRestIntervalMinutes: 20,
    eyeRestDurationSeconds: 20,
    twentyTwentyTwentyEnabled: true,
    soundType: "GENTLE_BELL",
    soundVolume: 0.6,
    visualReminderEnabled: true,
    snoozeMinutes: 5,
    enabled: true,
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated requests are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("profiles").get());
  await assertFails(unauthDb.collection("announcements").where("published", "==", true).get());
  await assertFails(unauthDb.collection("admin_audit_logs").get());
});

test("User account: owner create, partial update, cross-user isolation, and accountStatus guard", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Partial update allowed fields
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      focusModeActive: true,
      languageCode: "bn",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // User cannot suspend/reactivate self
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      accountStatus: "SUSPENDED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // User cannot mutate createdAt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Cross-user PII read/write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hijacked", updatedAt: new Date(Date.now() - 200) }));
  await assertFails(bobDb.collection("users").get());
});

test("Profiles, Schedules, Sessions, and Support Requests: CRUD & schema validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Create valid profile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("profiles").doc("work_default").set(validProfilePayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("profiles").get()
  );

  // Reject earlyWarningSeconds >= blinkIntervalSeconds
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("profiles").doc("bad_profile").set({
      ...validProfilePayload(ALICE_UID),
      blinkIntervalSeconds: 20,
      earlyWarningSeconds: 20,
    })
  );

  // Reject extra @DocumentId "id" key
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("profiles").doc("extra_id").set({
      ...validProfilePayload(ALICE_UID),
      id: "extra_id",
    })
  );

  // Valid partial update on profile including new soundType
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("profiles").doc("work_default").update({
      blinkIntervalSeconds: 30,
      earlyWarningSeconds: 10,
      soundType: "CRYSTAL_PING",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Schedule create & list
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("schedules").doc("sched_1").set({
      userId: ALICE_UID,
      profileId: "work_default",
      scheduleName: "Morning Desk",
      startTime: "09:00",
      endTime: "17:00",
      weekdays: [1, 2, 3, 4, 5],
      timezone: "UTC",
      blinkEnabled: true,
      eyeRestEnabled: true,
      enabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Session summary create & immutable update check
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").set({
      userId: ALICE_UID,
      profileId: "work_default",
      profileName: "Work",
      durationSeconds: 1200,
      blinksTriggered: 48,
      eyeRestsCompleted: 1,
      eyeRestsSkipped: 0,
      dayKey: "2026-11-26",
      createdAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").update({ blinksTriggered: 999 })
  );

  // Support request create (must start OPEN)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("support_requests").doc("req_1").set({
      userId: ALICE_UID,
      subject: "Sound question",
      message: "Can I preview Quiet Pulse before activating?",
      status: "OPEN",
      createdAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("support_requests").doc("req_2").set({
      userId: ALICE_UID,
      subject: "Fake resolved",
      message: "Trying to bypass status",
      status: "RESOLVED",
      createdAt: now,
    })
  );

  // Cross-user subcollection isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("profiles").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("schedules").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").get());
});

test("Announcements & Admin Audit Logs: RBAC, verified email guard, and query alignment", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_1", { email: ADMIN_EMAIL, email_verified: false }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const now = new Date(Date.now() - 1000);

  // Unverified email spoof must fail
  await assertFails(
    spoofedAdminDb.collection("announcements").doc("ann_1").set({
      title: "Spoof",
      message: "Spoofed admin",
      published: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Verified admin creates published + draft announcements
  await assertSucceeds(
    adminDb.collection("announcements").doc("ann_pub").set({
      title: "Blink25 Wellness Update",
      message: "Remember to take gentle 20-20-20 breaks.",
      published: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("announcements").doc("ann_draft").set({
      title: "Internal Draft",
      message: "Not published yet",
      published: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Regular user can query published announcements, NOT unfiltered or draft
  await assertSucceeds(aliceDb.collection("announcements").where("published", "==", true).get());
  await assertFails(aliceDb.collection("announcements").get());
  await assertFails(aliceDb.collection("announcements").doc("ann_draft").get());

  // Admin audit logs: only verified admin can create/read; immutable
  await assertSucceeds(
    adminDb.collection("admin_audit_logs").doc("log_1").set({
      adminId: ADMIN_UID,
      action: "PUBLISH_ANNOUNCEMENT",
      targetUserId: "system",
      details: "Published ann_pub",
      createdAt: now,
    })
  );
  await assertFails(aliceDb.collection("admin_audit_logs").get());
  await assertFails(adminDb.collection("admin_audit_logs").doc("log_1").delete());
});
