const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfile(uid) {
  return {
    userId: uid,
    displayName: "Alice",
    email: "alice@example.com",
    themeMode: "system",
    defaultProvider: "deepmind",
    defaultModelId: "gemini-3.8-flash",
    defaultSystemPrompt: "You are helpful.",
    temperature: 0.7,
    streamResponses: true,
    syncEnabled: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validChat(uid) {
  return {
    userId: uid,
    title: "Architecture Notes",
    provider: "anthropic",
    modelId: "claude-sonnet-4-5",
    systemPrompt: "",
    lastMessagePreview: "Let's plan this.",
    messageCount: 1,
    archived: false,
    pinned: false,
    activePluginIds: [],
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
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

test("Unauthenticated user cannot read or write user profile or chats", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
});

test("Alice can create, read, and update her own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(ref.set(validProfile(ALICE_UID)));
  await assertSucceeds(ref.get());
  await assertSucceeds(ref.update({ themeMode: "dark", updatedAt: serverTimestamp() }));
});

test("Bob cannot read or write Alice's profile, chats, prompts, plugins, or logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").set(validChat(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("prompts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("plugins").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("logs").get());
});

test("Shadow update with extra ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(ref.set(validProfile(ALICE_UID)));
  await assertFails(ref.update({ isAdmin: true, updatedAt: serverTimestamp() }));
});

test("Identity spoofing in payload is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(BOB_UID)));
});

test("Chat message requires existing parent chat and rejects updates to immutable fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const msgRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("chats")
    .doc("chat_missing")
    .collection("messages")
    .doc("msg_1");

  await assertFails(
    msgRef.set({
      userId: ALICE_UID,
      chatId: "chat_missing",
      role: "user",
      content: "Hello",
      provider: "deepmind",
      modelId: "gemini-3.8-flash",
      tokenCountEstimate: 2,
      latencyMs: 0,
      isError: false,
      createdAt: serverTimestamp(),
    })
  );

  const chatRef = aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1");
  await assertSucceeds(chatRef.set(validChat(ALICE_UID)));
  const validMsgRef = chatRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    validMsgRef.set({
      userId: ALICE_UID,
      chatId: "chat_1",
      role: "assistant",
      content: "Hi there!",
      provider: "deepmind",
      modelId: "gemini-3.8-flash",
      tokenCountEstimate: 4,
      latencyMs: 120,
      isError: false,
      createdAt: serverTimestamp(),
    })
  );
  await assertFails(validMsgRef.update({ role: "user" }));
  await assertSucceeds(validMsgRef.update({ content: "Updated markdown", latencyMs: 220 }));
});

test("Prompts, plugins, and append-only logs validate cleanly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const promptRef = aliceDb.collection("users").doc(ALICE_UID).collection("prompts").doc("prompt_1");
  await assertSucceeds(
    promptRef.set({
      userId: ALICE_UID,
      title: "Code Review",
      description: "Thorough architecture review",
      content: "Review the following code for correctness and security.",
      category: "Engineering",
      tags: ["code", "review"],
      isFavorite: true,
      usageCount: 0,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  const pluginRef = aliceDb.collection("users").doc(ALICE_UID).collection("plugins").doc("plugin_1");
  await assertSucceeds(
    pluginRef.set({
      userId: ALICE_UID,
      name: "Custom Local Gateway",
      description: "OpenAI-compatible endpoint",
      kind: "custom_endpoint",
      baseUrl: "https://api.example.com",
      endpointPath: "/v1/chat/completions",
      httpMethod: "POST",
      authHeaderName: "Authorization",
      authScheme: "Bearer",
      customModelId: "my-model-v1",
      enabled: true,
      requestTemplate: "",
      responseJsonPath: "choices[0].message.content",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("logs").doc("log_1");
  await assertSucceeds(
    logRef.set({
      userId: ALICE_UID,
      chatId: "chat_1",
      provider: "deepmind",
      modelId: "gemini-3.8-flash",
      status: "success",
      latencyMs: 420,
      summary: "Completed 256 tokens",
      createdAt: serverTimestamp(),
    })
  );
  // Logs are append-only: update must fail
  await assertFails(logRef.update({ status: "error" }));
});
