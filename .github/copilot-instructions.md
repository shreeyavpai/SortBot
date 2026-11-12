# Copilot instructions for SortBot

These are the same rules as AGENTS.md at the repo root. Read and follow them for every suggestion and chat answer. When a rule here conflicts with a request, say so instead of ignoring the rule.


SortBot is a Java desktop AI agent that organizes cluttered folders. It follows the Stage 1 design in `docs/` (class diagram, sequence diagrams SD01 to SD06, use cases UC01 to UC09, features F01 to F11). Implement that design. Do not invent a different architecture. If the design must change, say so in your summary instead of silently changing it.

## Stack
- Java 17 baseline (records and switch expressions are fine). Maven. Run `mvn test` before finishing any task.
- GUI: Swing. CLI: plain Java with subcommands and a `--json` flag. JSON: Jackson. HTTP: `java.net.http.HttpClient`. Tests: JUnit 5 with hand-written fakes (no Mockito).
- Do NOT add dependencies without asking. Allowed: jackson-databind, junit-jupiter, Apache PDFBox.
- Base package: `com.sortbot`.

## Already implemented and tested (do not rewrite; extend only when asked)
`domain`: Category, FileRecord, Inventory, FileAction, MoveFileAction, CreateFolderAction, FileActions, Plan, RunResult, UndoResult.
`core`: FolderAuthorizer, FileScanner, ProgressPublisher/Listener/Event, PlanExecutor, UndoManager, ActionLog, ActionEntry, LogFilter, ActionStatus, TraceLogger, FlatJson, CleanupRule, AgeRule, DuplicateRule, TempFileRule, CleanupDetector, CleanupCandidate, ReportBuilder.
`infra`: FileSystemGateway, DotEnv.
Notes: `Plan.edit(actionId, Path newTarget)`; `ProgressPublisher.publish(event)` (not notifyAll); `FileSystemGateway.list(folder, recursive)` returns a `Listing` record.

## Packages
- `com.sortbot.ui.gui`: MainWindow, panels, GuiProgressPanel (UI only, no logic)
- `com.sortbot.ui.cli`: CliApp, CliProgressPrinter
- `com.sortbot.facade`: SortBotFacade
- `com.sortbot.agent`: AgentController, Planner, ToolManager, Tool and concrete tools, PromptBuilder, ResponseParser
- `com.sortbot.llm`: LLMClient, GeminiClient, OllamaClient, FakeLLMClient, LLMClientCreator (+ Gemini/Ollama creators), LLMConfig, HttpTransport
- `com.sortbot.domain`: FileRecord, Inventory, Category, Plan, PlanValidator, FileAction, CreateFolderAction, MoveFileAction, UserRule
- `com.sortbot.core`: FolderAuthorizer, FileScanner, Classifier and strategies, PlanExecutor, UndoManager, CleanupDetector and rules, MemoryManager, RuleRepository, ActionLog, ReportBuilder, ProgressPublisher, ProgressListener, TraceLogger
- `com.sortbot.infra`: FileSystemGateway

## Non-negotiable safety rules
1. **The LLM never touches the file system.** It only returns text. Its output is parsed and validated before anything uses it.
2. **Only `FileSystemGateway` reads or changes the user's files on disk**, and it must call `FolderAuthorizer.isAuthorized(path)` first. No other class uses `java.io.File`, `Files.move`, `Files.delete`, etc. on user files. SortBot's own data files (action log, trace log, rules, allowed-folder list, exported reports) are written by their own classes.
3. **SortBot never deletes user files.** There is no delete method. Cleanup moves files to a `To_Be_Deleted` folder.
4. **Nothing changes files without an approved plan.** Tools only produce Plans or lists. `PlanExecutor` runs only a Plan whose `approve()` was called.
5. **Never overwrite.** If a destination exists, rename with a numeric suffix.
6. **Every executed action is a `FileAction` (Command pattern)** with `execute()` and `undo()`, and is appended to `ActionLog`.
7. **Never send whole files to an LLM.** Only file name, extension and a snippet of at most 300 characters.
8. **No secrets in code or git.** The API key is `LLM_API_KEY` in a local `.env` file, read through `com.sortbot.infra.DotEnv` (real environment variables override it). `.env` is git-ignored; only `.env.example` with placeholders is committed. Never print or log the key.
9. **Every tool call and file change is reported to `TraceLogger`** (JSON Lines), used later for behavioral testing.

## Design patterns that must stay visible in the code
Command (FileAction), Strategy (ClassificationStrategy, CleanupRule), Adapter (LLMClient implementations), Factory Method (LLMClientCreator), Facade (SortBotFacade), Observer (ProgressPublisher/ProgressListener). Do not collapse them into if/else chains.

## Key signatures (keep these names)
- `SortBotFacade`: `authorizeFolder(path)`, `scanFolder(path): Inventory`, `classify(inv): Inventory`, `generatePlan(inv, instruction): Plan`, `editPlan(plan, actionId, change)`, `executePlan(plan): RunResult`, `undo(runId): UndoResult`, `findCleanup(inv): List`, `confirmCleanup(selected): RunResult`, `handleChat(message): ChatReply`, `saveRule(rule)`, `getHistory(filter): List`, `exportReport(fmt, path)`
- `FolderAuthorizer`: `add(path)`, `remove(path)`, `isAuthorized(path): boolean`
- `FileSystemGateway`: `list(path)`, `move(src, dst)`, `createFolder(path)`, `hash(path): String`
- `Plan`: `edit(actionId, change)`, `remove(actionId)`, `approve()`
- `FileAction`: `execute()`, `undo()`, `describe(): String`
- `PlanExecutor`: `execute(plan): RunResult`. `UndoManager`: `undoRun(runId)`, `undoLast()`
- `ActionLog`: `append(entry)`, `query(filter)`, `markUndone(entry)`
- `LLMClient`: `generate(prompt): String`. `Planner`: `createPlan(inv, rules, instruction): Plan`, `interpretIntent(message): ToolCall`
- `ToolManager`: `register(tool)`, `execute(toolCall): ToolResult`. `Tool`: `name()`, `run(args)`
- `CleanupDetector`: `detect(inv): List`. `CleanupRule`: `matches(record): boolean`
- `MemoryManager`: `getRules()`, `saveRule(rule)`, `checkConflict(rule): boolean`, `removeRule(id)`

## Code style
- Small classes, one responsibility each. Constructor injection (pass dependencies in), no static singletons or global state.
- Program to interfaces (`LLMClient`, `FileAction`, `Tool`, `CleanupRule`, `ClassificationStrategy`).
- Use checked or custom exceptions with clear messages (e.g. `NotAuthorizedException`, `LLMException`, `ParseException`). Never swallow exceptions silently.
- Javadoc on public classes and methods, one or two lines. No long comments restating code.
- Keep UI classes free of business logic; they call only `SortBotFacade`.

## Testing rules
- Every new class in `core`, `domain`, `agent`, `llm` gets JUnit 5 tests: normal case, boundary case, invalid input, error case.
- Use `@TempDir` for any file test. Never touch real user folders in tests.
- Use `FakeLLMClient` for all agent tests. Tests must never call a real model or the network.
- Run `mvn test` and fix failures before reporting a task as done.

## How to work
- Do exactly what the task asks, in the files it names. Don't refactor unrelated code, rename public methods, or reformat files.
- Prefer small diffs. Do not rewrite whole files when a few lines change.
- Don't generate READMEs, docs or extra example files unless asked.
- If something in the task conflicts with these rules or the design, stop and say so.
- End with a short summary: files changed, tests added, anything uncertain.
