# B5: What should happen when the AgentExecutor throws during a follow-up turn?

**Sources checked:**
- A2A spec repo `a2a-protocol` @ `98279d6` (main, `v1.0.1-89`, 2026-10-20). Baseline is tag `v1.0.1`, read via `git show v1.0.1:<path>`. The `v1.0.1..main` diff of `docs/specification.md` and `specification/a2a.proto` has no change relevant here (only link and example fixes).
- A2A Python SDK `a2a-python` @ `79a6f45` (main, `v1.2.3-5`, 2026-10-21). Also compared with tag `v1.1.0`, the version Koog's Python test server pins (`a2a/test-python-a2a-server/pyproject.toml`: `a2a-sdk[http-server]==1.1.0`).
- Koog working tree on branch `eugenethedev/a2a-1.0` (`65b063414`).
- TCK: not examined. The question did not ask for it.

**Confidence:** high on what the spec says (it says nothing specific) and on Python's behavior (code and tests). Medium on the recommendation, which is a design judgement.

## Answer

**The spec doesn't say what an executor exception during a follow-up should do to the task.**
- Nothing in the v1.0.1 text or proto says when a task must move to FAILED or REJECTED, as opposed to the request just getting an error.
- The only state-related error rule is that messages to a task that is already terminal get `UnsupportedOperationError`.
- `ContentTypeNotSupportedError` is listed as a request-level error of SendMessage. Validation errors "MUST" be checked "before processing".
- FAILED means "finished with an error". REJECTED means "the agent decided not to perform the task... during initial task creation or later". Both are terminal.

**Failure reasons.** The only place for a reason is `TaskStatus.message`, an optional `Message`. `TaskStatusUpdateEvent.metadata` and `Task.metadata` are generic `Struct`s with no defined error semantics. There is no guidance on what to put in the message for FAILED or REJECTED. There is no rule against leaking internals in it, beyond general security rules about logs and sensitive data.

**The Python reference does both.** For any non-cancellation exception from `AgentExecutor.execute`, on a new task or a follow-up:
- it persists the task as `TASK_STATE_FAILED` with **no status message**;
- it **also returns the exception to the requester as an error**:
  - an `A2AError` keeps its own code;
  - any other exception becomes `-32603` with `message=str(e)`.
- Streaming clients get an SSE `error` event, not a FAILED status update.

**Koog differs.** It persists FAILED with a generic agent message and returns the FAILED task (or a FAILED status update on a stream) as a success. The executor's exception never reaches the requester.

**Recommendation: option C.**
- Treat an `A2AException` thrown by a follow-up's `execute` before the agent's first event of the turn as a request error. The task keeps its state, and the requester gets the error code.
- Anything else stays "executor crash → FAILED".
- Fix the KDoc either way.

## Requirements

| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| R1 | Messages sent to a task in a terminal state (COMPLETED, FAILED, CANCELED, REJECTED) cannot be accepted; the error is `UnsupportedOperationError`. | Listed operation error (no RFC keyword in 3.1.1/3.1.2) | all | spec v1.0.1 `docs/specification.md:175`, `:200` (§3.1.1, §3.1.2) |
| R2 | `ContentTypeNotSupportedError` is a SendMessage/SendStreamingMessage operation error ("A Media Type provided in the request's message parts is not supported"). It doesn't say whether the task state changes. | Listed operation error | all; JSON-RPC `-32005` | `docs/specification.md:174`, `:201` (§3.1.1/§3.1.2); `:559` (§3.3.2); `:1186` (§5.4) |
| R3 | Servers MUST return appropriate errors and SHOULD give actionable information. | MUST / SHOULD | all | `docs/specification.md:500` (§3.3.2) |
| R4 | Validation errors: servers MUST validate all input parameters **before processing**, and SHOULD say which parameter failed and why. "unsupported content type" is listed as an example validation error, mapped to JSON-RPC `-32602`. | MUST / SHOULD | all | `docs/specification.md:518-523` (§3.3.2) |
| R5 | System errors ("Internal agent failures"): SHOULD distinguish temporary from permanent failures, MAY give retry guidance, SHOULD log. The example JSON-RPC code is `-32603`. | SHOULD / MAY | all | `docs/specification.md:531-536` (§3.3.2) |
| R6 | Every error response MUST carry a code and a human-readable message; details are optional (`google.rpc` types SHOULD be used). | MUST | all; JSON-RPC: `error.code`/`message`/`data` | `docs/specification.md:540-549` (§3.3.2), `:2433-2437` (§9.5) |
| R7 | FAILED = "finished with an error", terminal. REJECTED = "agent has decided to not perform the task... during initial task creation or later once an agent has determined it can't or won't proceed", terminal. | Definition (proto comment, no keyword) | all | `specification/a2a.proto:196-205` @v1.0.1 |
| R8 | `TaskStatus.message` (optional `Message`, "A message associated with the status") is the only defined per-status explanation field. `TaskStatusUpdateEvent.metadata` / `Task.metadata` are free-form `Struct`s. | Optional field | all | `a2a.proto:211-219`, `:296-305`, `:182-183` @v1.0.1 |
| R9 | "Status Messages: Agents attach Messages to status update events to inform clients about task progress, request additional input, or provide informational updates." Status messages are not reliable delivery for critical information. | Explanatory / MUST NOT (on clients) | all | `docs/specification.md:755`, `:761` (§3.7) |
| R10 | Blocking SendMessage MUST wait until terminal or interrupted, then return the latest task state. | MUST | all | `docs/specification.md:446` (§3.2.2) |
| R11 | Logs MUST NOT include sensitive information. This is the nearest rule to "don't leak internals"; nothing covers error messages or status messages. | MUST (logs only) | n/a | `docs/specification.md` §13.4, line 3197 |
| R12 | Agents MAY accept more messages for non-terminal tasks (multi-turn). | MAY | all | `docs/specification.md:567` (§3.3.3) |

Nothing normative covers:
- when an error in processing a follow-up must or should move the task to FAILED or REJECTED, or leave it unchanged;
- whether an error response and a state change may both happen;
- what a FAILED status message should contain.

## Details

### 1. What the spec says about errors on an existing task

**What exists**
- **§3.1.1 Errors** (`:172-176`) lists three errors: ContentTypeNotSupported, UnsupportedOperation (terminal task), and TaskNotFound. All are request-level. None of them says anything about the task's state.
- **§3.3.2** (`:498-563`) sorts errors into categories (validation, resource, system, and so on). "Validate all input parameters before processing" (`:519`) implies that validation errors are found before the agent processes anything. On that reading, a validation failure is a request error and the task is untouched. This is an inference: the spec never says "the task state must not change".
- **§3.4.2** (`:614`) and **§3.4.3** (`:628`) define request-level rejections of follow-ups (TaskNotFound; a contextId that doesn't match the task). They are not state transitions.
- **Task lifecycle.** The proto comments (R7) are the only definitions of FAILED and REJECTED. REJECTED is explicitly allowed "later", after creation, so in principle an agent could reject an INPUT_REQUIRED task. It is still terminal, so the conversation ends just as it does with FAILED.
- **`docs/topics/life-of-a-task.md:83`** (explanatory) says a terminal task "cannot restart". It doesn't cover errors.

**Classification per error type** (the spec gives none of these explicitly; this is interpretation)

| Error | Spec placement | Natural reading for an existing, non-terminal task |
|---|---|---|
| `ContentTypeNotSupportedError` (-32005) | §3.1.1 request error and §3.3.2 validation example | Request error; nothing suggests the task should change. |
| `InvalidParamsError` (-32602) | §3.3.2 validation, "before processing" | Request error; nothing suggests the task should change. |
| `UnsupportedOperationError` (-32004) | §3.3.2 table: "operation or a specific aspect of it is not supported" | Request error. |
| Internal error (-32603) | §3.3.2 system errors, "internal agent failures" | Undefined. Either a request error, or FAILED ("finished with an error"), is defensible. |

### 2. Failure reason on a FAILED or REJECTED status

**Where a reason can go**
- `TaskStatus.message` (`a2a.proto:215`) is the field for it. In ProtoJSON: `{"state":"TASK_STATE_FAILED","message":{"messageId":"…","role":"ROLE_AGENT","parts":[{"text":"…"}]},"timestamp":"…"}`.
- `TaskStatusUpdateEvent.metadata` (`:304`) could carry structured data by convention or through an extension (§4.6, `:1083` mentions extending TaskStatus messages). The spec doesn't define it for errors.

**Guidance**
- The only "MUST include a TaskStatus message" rule is for AUTH_REQUIRED (`docs/specification.md` §7.6.1, item 3, line 1927). Nothing like it exists for FAILED or REJECTED.
- There is no guidance on content or on leaking internals. The leak rules that do exist (`:513`, `:527`, and §13.1 at `:3098`) are about authorization and resource existence. §13.4 (`:3197`) covers logs.

### 3. Python SDK behavior (main `79a6f45`, default handler `DefaultRequestHandlerV2`)

`DefaultRequestHandler = DefaultRequestHandlerV2` (`src/a2a/server/request_handlers/__init__.py:46`). The same alias is at `:46` at v1.1.0.

**Sequence for an executor that raises** (new task or follow-up, no special case for follow-ups):

1. **Pre-checks happen before any task change** (`default_request_handler_v2.py:330-394`):
   - The `historyLength` check, then the opt-in `validate_input_modes`, which raises `ContentTypeNotSupportedError` (`:337-338`; off by default, `:119`).
   - Then TaskNotFound (`:347-348`) and the contextId mismatch → `InvalidParamsError` (`:351-358`).
   - The terminal-task check raises `UnsupportedOperationError` in `ActiveTask.start` (`active_task.py:560-564`).
   - All of these fail the request and leave the task as it was.
2. **The producer catches any `Exception` from `execute`** (`active_task.py:665-684`).
   - It loads or creates the task (`ensure_task_id`). If the task isn't terminal, it sets `status.state = TASK_STATE_FAILED` with **no status message** and persists it (`:676-679`).
   - It then enqueues the exception (`:684`).
   - `A2AError` subclasses are not treated differently.
3. **The consumer re-raises the dequeued exception** (`:221-222`).
   - In its `except`, `_write_failed_status` tries to write FAILED again. It is a no-op if the task is already terminal (`:162-182`).
   - It then enqueues the **exception** to subscribers (`:144-160`). The FAILED write is not fanned out as a status-update event to subscribers.
4. **The subscriber raises the exception into the request handler** (`active_task.py:770-777`).
   - `on_message_send` deliberately keeps consuming after a FAILED task, so a producer exception still surfaces (`default_request_handler_v2.py:427-436`).
5. **JSON-RPC mapping** (`src/a2a/server/routes/jsonrpc_dispatcher.py`):
   - `A2AError` → its own code (`:331-332`, `:345-346`);
   - any other exception → `InternalError(message=str(e))`, i.e. `-32603` with the raw exception text (`:347-351`, `:184-186`);
   - streaming: the same mapping, sent as an SSE `error` event (`:406-407`, `:588-596`).

**Net effect**
- A blocking SendMessage on an INPUT_REQUIRED task whose executor raises `ContentTypeNotSupportedError` returns JSON-RPC `-32005`.
- The task is persisted as FAILED with an empty status message, so a later GetTask shows FAILED, and further messages get `UnsupportedOperationError`.
- Python sends the raw text of non-A2A exceptions to clients.

**Tests**
- `tests/server/request_handlers/test_default_request_handler_v2.py:1519-1560`: an early producer exception leaves the stored task FAILED.
- `:1603-1626`: an agent sends FAILED and then raises → the request raises the original exception.
- `:1628-1650`: an agent-declared FAILED without an exception → the FAILED task is returned.
- `:1381-1405`: an error doesn't hang the request.
- Each test sends a single message. No test raises on a follow-up specifically, but the code path doesn't depend on whether the message is a follow-up.

**v1.1.0 (the pinned test server)**
- The producer doesn't persist FAILED itself. The consumer writes FAILED and then enqueues the exception (`v1.1.0:active_task.py:139-154`, `:549-561`), so a plain raise still gives FAILED plus an error.
- If the agent had already sent a FAILED status and then raised, v1.1.0 returned the FAILED task as a success. `bc32d7e` (#1229, first in v1.1.4) fixed that.
- Agent-declared failure helpers take an optional message: `TaskUpdater.failed(message)` and `reject(message)` (`src/a2a/server/tasks/task_updater.py:164-176`).

### 4. Options

**A. Keep the current behavior; change only the KDoc.**
- The KDoc would say: on a follow-up, report bad input with a status update (for example, stay INPUT_REQUIRED with an explanatory message, or REJECTED/FAILED with a message) rather than throwing, because a throw ends the task.
- Pros:
  - no code change;
  - compliant (the spec is silent);
  - a single, simple rule ("a throw on an existing task = FAILED");
  - no leak.
- Cons:
  - the error code never reaches the requester, against the spirit of R3/R6 for validation-type errors;
  - new tasks and follow-ups behave differently for the same exception;
  - diverges from Python, which returns the error.

**B. Python parity: mark FAILED and return the error to the requester.**
- `A2AException` keeps its code; anything else becomes -32603.
- Pros:
  - matches the reference;
  - the requester gets the code;
  - task state is consistent for observers.
- Cons:
  - a validation-type exception still permanently kills an INPUT_REQUIRED conversation;
  - streaming semantics change (the requester's stream would end with an error, not a FAILED update, while other subscribers get FAILED);
  - more paths in `emitTurnEvents`.
- Koog keeps sending the generic `-32603` message (Python sends `str(e)`), so there is no leak.

**C. An `A2AException` before the first event of a follow-up turn = request error, task unchanged** (review option b).
- Every other failure stays FAILED (current behavior).
- Pros:
  - matches the spec's model that validation happens "before processing" (R4), and that ContentTypeNotSupported/InvalidParams are request errors (R2);
  - symmetric with the new-task path, where the same exception already reaches the client as its code (`A2AServer.kt:555-558`);
  - keeps INPUT_REQUIRED conversations alive;
  - fits the existing rule that the user message is persisted only when the agent sends its first event (`AgentExecutor.kt:35-41`), so nothing has to be rolled back;
  - the agent author chooses the outcome by choosing the exception type.
- Cons:
  - diverges from Python, which marks FAILED for any exception;
  - the boundary is implicit: an `A2AException` thrown after the first event still means FAILED, and an executor may already have side effects before it throws. Both need clear KDoc.
- Internal-error exceptions (`A2AInternalErrorException`) might arguably still mean FAILED. The decision is whether "A2AException" means every subtype or only the request-error ones.

**D. A separate validation hook before the turn starts.**
- Examples: a `validate(context)` method on `AgentExecutor`, or a documented override of `onSendMessageCommon`, together with an opt-in input-mode check like Python's `validate_input_modes`.
- Errors from that stage are always request errors; any exception from `execute` = FAILED.
- Pros:
  - the cleanest semantics and the closest match to R4;
  - Python has the same split (pre-checks vs. execute);
  - no heuristics.
- Cons:
  - new public API, which is an architecture decision under `a2a/CLAUDE.md`;
  - agents that validate inside `execute` still get FAILED;
  - can be combined with A or C.

**E. Put the failure reason into the FAILED status message.**
- For example, use the `A2AException` message, which the agent author wrote, and keep the generic text for other exceptions.
- This is orthogonal to A–D.
- Pros: the requester learns why the task failed.
- Cons:
  - reverses a recorded decision;
  - author-written messages may still contain internals;
  - the spec gives no guidance, and Python leaves the message empty.

**Recommendation: C, plus the KDoc rewrite from A.**
- With C, an `A2AException` thrown before the first event of a turn is a request error, whether the turn is a new task or a follow-up. That is one rule, consistent with §3.3.2 (R3/R4) and with the existing new-task behavior.
- A crash in the middle of processing stays FAILED with the generic message. This is compliant, and Koog's no-leak stance is stricter than Python's `str(e)`.
- Consider D later if agent authors need a clear validation stage.
- Report the divergence from Python (it would mark FAILED and return the code) in the KDoc or release notes rather than copying it. Copying it would keep the conversation-killing behavior that motivated B5.

## Koog status

**Follow-up failure path**
- `TaskEventStream.endTurn` calls `markFailed` for any non-cancellation failure (`a2a/a2a-server/src/commonMain/kotlin/ai/koog/a2a/server/turn/TaskEventStream.kt:259-267`).
- `markFailed` writes FAILED with the generic agent message `"Task failed due to an internal error"` (`:91`, `:269-295`), fans it out to every subscriber and closes them all. This happens only if the task exists and is non-terminal.

**Response shaping**
- `emitTurnEvents` stops at the terminal FAILED event and emits it (`A2AServer.kt:532-553`).
- It calls `awaitAgent` (which rethrows the executor's exception) only if the response is not complete, or if the event was rejected by the server (`:555-558`, `:654-676`).
- So an arbitrary `A2AException` on a follow-up is swallowed. Blocking SendMessage returns the stored FAILED task (`:705-731`). Streaming yields the pre-turn snapshot `Task` and then the FAILED `statusUpdate`.

**New task with no events**
- No task exists, so `markFailed` is a no-op. The flow ends with no events, and `awaitAgent` rethrows. An `A2AException` reaches the client with its own code.
- A new task whose agent sent a `Task` and then threw is handled like a follow-up (FAILED task returned).

**KDoc**
- `AgentExecutor.kt:31-33` states the FAILED behavior accurately.
- `:94-96` recommends throwing `A2AContentTypeNotSupportedException` / `A2AUnsupportedOperationException`, which on a follow-up silently becomes FAILED.

**Server-side pre-checks**
- `onSendMessageCommon` (`A2AServer.kt:451-479`) checks empty parts, `historyLength`, TaskNotFound, contextId mismatch and terminal state.
- It has no input-mode check, unlike Python's opt-in `validate_input_modes`.

**JSON-RPC mapping**
- `JSONRPCServerTransport.toJSONRPCErrorResponse` maps non-A2A exceptions to `A2AInternalErrorException("Internal error: ${e.message}")` (`a2a/a2a-transport/a2a-transport-core-jsonrpc/src/commonMain/kotlin/ai/koog/a2a/transport/jsonrpc/JSONRPCServerTransport.kt:326-334`).
- That puts the raw exception text in the response on the new-task path, the same as Python. See Open questions.

**Divergences from Python**
1. Koog doesn't return the error on a follow-up (or after any event); Python does.
2. Koog's FAILED status carries a generic message; Python's has none.
3. A Koog stream gets a FAILED status update; a Python stream gets an SSE error event.

Neither SDK is non-compliant. The spec doesn't define this behavior.

## Testability notes

- **Server unit tests** (`a2a-server` jvmTest; follow-up fixture: an agent that goes INPUT_REQUIRED, then throws on the next message):
  - **Under C:**
    - an `A2AContentTypeNotSupportedException` thrown before any event → `onSendMessage` throws that exception;
    - `taskStorage.get` still shows INPUT_REQUIRED with the previous status message intact, and the history has no new user message;
    - a later valid follow-up still works.
  - **Mid-turn A2A exception, and non-A2A exceptions** (C keeps them FAILED):
    - an `A2AException` thrown after a WORKING event → FAILED with the generic message, which is what happens today;
    - a non-A2A exception before any event → FAILED.
  - **Streaming:**
    - the requester's flow throws before or after the snapshot (decide whether the snapshot is emitted first; today it is emitted lazily on the first event, `A2AServer.kt:540-545`, so nothing has been emitted yet);
    - a concurrent `SubscribeToTask` stream receives nothing and stays open.
- **Transport-level:** a JSON-RPC test that a follow-up with an executor throwing `A2AContentTypeNotSupportedException` yields `error.code == -32005`.
- **Python interop:** Koog's client tests run against Python 1.1.0. Under that version, a raising Python executor gives FAILED plus an error response. This could be checked from Koog's client side but isn't needed for B5.
- **TCK:** not examined. The TCK's SUT agent probably doesn't raise on follow-ups (hypothesis), so B5 is likely not covered there.

## Discrepancies

- Python vs. Koog: see "Koog status", items 1–3. This is a difference in implementation choices, not a conflict with the spec.
- Within the spec:
  - §3.3.2 maps "unsupported content type" to the Validation category with example code `-32602` (`docs/specification.md:522-523`).
  - §5.4 and §3.1.1 give `ContentTypeNotSupportedError` its own code, `-32005` (`:1186`).
  - Both list an error response, not a task transition. The §3.3.2 code is only an example, and the A2A-specific code should win.
- Python marks a **new** task FAILED (it creates the task through `ensure_task_id`) even when the executor raised before sending anything (`active_task.py:672-679`; test `:1519-1560`). Koog creates no task in that case. The spec doesn't settle this either.

## Open questions

1. **Leak via JSON-RPC internal error.** Koog's `toJSONRPCErrorResponse` puts `e.message` of non-A2A exceptions into the `-32603` message (`JSONRPCServerTransport.kt:333`). That contradicts the "no details" stance behind the generic FAILED message. Python does the same (`jsonrpc_dispatcher.py:350`). Route to whoever owns the transport error mapping.
2. **Which `A2AException` subtypes count under C.** Should `A2AInternalErrorException` thrown by the agent count as a request error or as FAILED?
3. **Should the requester also get the error after a FAILED transition** (Python's option B behavior) for non-A2A crashes? This is separate from C.
4. **TCK coverage of executor failure on follow-ups.** Not examined; route to a TCK check if needed.
