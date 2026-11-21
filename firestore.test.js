const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_sasah";
const OWNER_EMAIL = "sasah2619@gmail.com";
const ALICE_UID = "alice_123";
const ALICE_EMAIL = "alice@example.com";
const BOB_UID = "bob_456";
const BOB_EMAIL = "bob@example.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsPast(ms = 2000) {
  return new Date(Date.now() - ms);
}

function validWorkingHours() {
  return {
    sunday: { open: true, from: "09:00", to: "18:00" },
    monday: { open: true, from: "09:00", to: "18:00" },
    tuesday: { open: true, from: "09:00", to: "18:00" },
    wednesday: { open: true, from: "09:00", to: "18:00" },
    thursday: { open: true, from: "09:00", to: "18:00" },
    friday: { open: false, from: "09:00", to: "18:00" },
    saturday: { open: true, from: "10:00", to: "16:00" },
  };
}

function validSystemConfig(ownerEmail = OWNER_EMAIL) {
  return {
    app_name: "نَسَق",
    owner_email: ownerEmail,
    business_name: "مركز نَسَق للخدمات",
    business_description: "حجز المواعيد بكل سهولة",
    business_phone: "07700000000",
    business_address: "بغداد - المنصور",
    currency: "د.ع",
    country_code: "+964",
    whatsapp_confirm_template: "مرحباً {customer_name}، تم تأكيد حجزك {booking_code}",
    whatsapp_reminder_template: "تذكير بموعدك {date} الساعة {start_time}",
    working_hours: validWorkingHours(),
    blocked_slots: [],
    updated_at: tsPast(),
  };
}

function validUserPayload(uid, email, role = "user") {
  const now = tsPast();
  return {
    uid,
    email,
    display_name: "مستخدم تجريبي",
    photo_url: "https://example.com/photo.png",
    phone: "07711112222",
    role,
    is_blocked: false,
    total_bookings: 0,
    total_spent: 0,
    admin_notes: "",
    created_at: now,
    last_login: now,
  };
}

function validServicePayload(orderIndex = 0) {
  const now = tsPast();
  return {
    name: "استشارة طبية",
    description: "فحص وتشخيص شامل",
    price: 25000,
    duration_minutes: 30,
    is_active: true,
    order_index: orderIndex,
    created_at: now,
    updated_at: now,
  };
}

function validAppointmentPayload(uid, email, serviceId = "svc_1") {
  const now = tsPast();
  return {
    booking_code: "#NSQ-1024",
    user_uid: uid,
    customer_name: "علياء محمد",
    customer_email: email,
    customer_phone: "07711112222",
    service_id: serviceId,
    service_name: "استشارة طبية",
    service_price: 25000,
    service_duration: 30,
    date: "2026-11-25",
    start_time: "10:00",
    end_time: "10:30",
    status: "pending",
    customer_notes: "يفضل الفترة الصباحية",
    admin_notes: "",
    reminder_sent: false,
    created_at: now,
    updated_at: now,
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

test("Unauthenticated user: denied from users, appointments, slot_locks, and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("appointments").get());
  await assertFails(unauthDb.collection("slot_locks").get());
  await assertFails(unauthDb.collection("system_config").get());
  await assertFails(
    unauthDb.collection("services").doc("svc_1").set(validServicePayload())
  );
  // Public catalog & config/main get allowed
  await assertSucceeds(unauthDb.collection("system_config").doc("main").get());
  await assertSucceeds(unauthDb.collection("services").get());
});

test("Email spoofing: unverified email matching owner is denied", async () => {
  const spoofDb = testEnv
    .authenticatedContext(OWNER_UID, {
      email: OWNER_EMAIL,
      email_verified: false,
    })
    .firestore();
  await assertFails(
    spoofDb.collection("system_config").doc("main").set(validSystemConfig())
  );
  await assertFails(
    spoofDb
      .collection("users")
      .doc(OWNER_UID)
      .set(validUserPayload(OWNER_UID, OWNER_EMAIL, "owner"))
  );
});

test("System config & services: initial owner can initialize config and manage services; regular user cannot", async () => {
  const ownerDb = testEnv
    .authenticatedContext(OWNER_UID, {
      email: OWNER_EMAIL,
      email_verified: true,
    })
    .firestore();
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, {
      email: ALICE_EMAIL,
      email_verified: true,
    })
    .firestore();

  // Alice cannot initialize system_config/main
  await assertFails(
    aliceDb
      .collection("system_config")
      .doc("main")
      .set(validSystemConfig(ALICE_EMAIL))
  );

  // Initial owner can create system_config/main
  await assertSucceeds(
    ownerDb.collection("system_config").doc("main").set(validSystemConfig())
  );

  // Alice cannot update system_config/main or delete it
  await assertFails(
    aliceDb
      .collection("system_config")
      .doc("main")
      .update({ owner_email: ALICE_EMAIL, updated_at: tsPast() })
  );
  await assertFails(aliceDb.collection("system_config").doc("main").delete());

  // Owner can create, update, delete service; Alice cannot
  await assertSucceeds(
    ownerDb.collection("services").doc("svc_1").set(validServicePayload(1))
  );
  await assertFails(
    aliceDb.collection("services").doc("svc_2").set(validServicePayload(2))
  );
  await assertSucceeds(
    ownerDb
      .collection("services")
      .doc("svc_1")
      .update({ price: 30000, updated_at: tsPast(500) })
  );
});

test("Users: self-creation restricted to role=user, no privilege escalation, PII isolated", async () => {
  const ownerDb = testEnv
    .authenticatedContext(OWNER_UID, {
      email: OWNER_EMAIL,
      email_verified: true,
    })
    .firestore();
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, {
      email: ALICE_EMAIL,
      email_verified: true,
    })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, {
      email: BOB_EMAIL,
      email_verified: true,
    })
    .firestore();

  // Alice cannot create user with role=owner
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validUserPayload(ALICE_UID, ALICE_EMAIL, "owner"))
  );

  // Alice creates valid user profile
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validUserPayload(ALICE_UID, ALICE_EMAIL, "user"))
  );

  // Alice can update her own allowed profile fields
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      display_name: "علياء المحدثة",
      phone: "07799998888",
      last_login: tsPast(500),
    })
  );

  // Alice cannot escalate role, unblock, or modify admin_notes/total_spent
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "owner",
      last_login: tsPast(500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      admin_notes: "تعديل غير مصرح",
      last_login: tsPast(500),
    })
  );

  // Bob cannot read Alice's profile or list users
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());

  // Owner can list users and update admin_notes & is_blocked
  await assertSucceeds(ownerDb.collection("users").get());
  await assertSucceeds(
    ownerDb.collection("users").doc(ALICE_UID).update({
      admin_notes: "عميلة مميزة",
      is_blocked: false,
      total_bookings: 1,
      total_spent: 25000,
    })
  );
});

test("Appointments & slot_locks: atomic create, scoped list, user cancel, blocked user denial, and owner actions", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("system_config").doc("main").set(validSystemConfig());
    await db.collection("services").doc("svc_1").set(validServicePayload());
    await db
      .collection("users")
      .doc(ALICE_UID)
      .set(validUserPayload(ALICE_UID, ALICE_EMAIL, "user"));
    await db
      .collection("users")
      .doc(BOB_UID)
      .set({
        ...validUserPayload(BOB_UID, BOB_EMAIL, "user"),
        is_blocked: true,
      });
  });

  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, {
      email: ALICE_EMAIL,
      email_verified: true,
    })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, {
      email: BOB_EMAIL,
      email_verified: true,
    })
    .firestore();
  const ownerDb = testEnv
    .authenticatedContext(OWNER_UID, {
      email: OWNER_EMAIL,
      email_verified: true,
    })
    .firestore();

  // Blocked user Bob cannot create appointment
  await assertFails(
    bobDb
      .collection("appointments")
      .doc("apt_bob")
      .set(validAppointmentPayload(BOB_UID, BOB_EMAIL, "svc_1"))
  );

  // Alice cannot create appointment with @DocumentId 'id' or confirmed status
  await assertFails(
    aliceDb
      .collection("appointments")
      .doc("apt_bad")
      .set({
        ...validAppointmentPayload(ALICE_UID, ALICE_EMAIL, "svc_1"),
        id: "apt_bad",
      })
  );
  await assertFails(
    aliceDb
      .collection("appointments")
      .doc("apt_bad2")
      .set({
        ...validAppointmentPayload(ALICE_UID, ALICE_EMAIL, "svc_1"),
        status: "confirmed",
      })
  );

  // Alice atomically creates appointment + slot_lock in a batch
  const batch = aliceDb.batch();
  batch.set(
    aliceDb.collection("appointments").doc("apt_1"),
    validAppointmentPayload(ALICE_UID, ALICE_EMAIL, "svc_1")
  );
  batch.set(aliceDb.collection("slot_locks").doc("apt_1"), {
    date: "2026-11-25",
    start_time: "10:00",
    end_time: "10:30",
    status: "active",
    updated_at: tsPast(),
  });
  await assertSucceeds(batch.commit());

  // Unfiltered appointments list by Alice fails; scoped list succeeds
  await assertFails(aliceDb.collection("appointments").get());
  await assertSucceeds(
    aliceDb
      .collection("appointments")
      .where("user_uid", "==", ALICE_UID)
      .get()
  );

  // Slot locks list succeeds for signed-in users without exposing PII
  await assertSucceeds(
    bobDb
      .collection("slot_locks")
      .where("date", "==", "2026-11-25")
      .where("status", "==", "active")
      .get()
  );

  // Bob cannot read or cancel Alice's appointment
  await assertFails(bobDb.collection("appointments").doc("apt_1").get());
  await assertFails(
    bobDb
      .collection("appointments")
      .doc("apt_1")
      .update({ status: "cancelled", updated_at: tsPast(500) })
  );

  // Owner confirms and sends reminder
  await assertSucceeds(
    ownerDb.collection("appointments").doc("apt_1").update({
      status: "confirmed",
      reminder_sent: true,
      admin_notes: "تم التواصل هاتفياً",
      updated_at: tsPast(500),
    })
  );

  // Alice cancels her confirmed appointment and releases slot lock atomically
  const cancelBatch = aliceDb.batch();
  cancelBatch.update(aliceDb.collection("appointments").doc("apt_1"), {
    status: "cancelled",
    updated_at: tsPast(300),
  });
  cancelBatch.update(aliceDb.collection("slot_locks").doc("apt_1"), {
    status: "released",
    updated_at: tsPast(300),
  });
  await assertSucceeds(cancelBatch.commit());
});

test("Ownership transfer: current owner transfers to Alice, Alice becomes owner and previous owner loses access", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("system_config").doc("main").set(validSystemConfig());
  });

  const ownerDb = testEnv
    .authenticatedContext(OWNER_UID, {
      email: OWNER_EMAIL,
      email_verified: true,
    })
    .firestore();
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, {
      email: ALICE_EMAIL,
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    ownerDb.collection("system_config").doc("main").update({
      owner_email: ALICE_EMAIL,
      previous_owner_email: OWNER_EMAIL,
      ownership_transferred_at: tsPast(500),
      updated_at: tsPast(500),
    })
  );

  // Previous owner can no longer create services
  await assertFails(
    ownerDb.collection("services").doc("svc_new").set(validServicePayload())
  );

  // New owner Alice can create services
  await assertSucceeds(
    aliceDb.collection("services").doc("svc_new").set(validServicePayload())
  );
});
