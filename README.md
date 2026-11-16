# SortBot

SortBot is an AI agent that organizes cluttered folders. The language model only **proposes** a plan. Deterministic code checks it, **you approve it**, and a separate executor carries it out with a full action log and undo. SortBot never deletes your files: cleanup candidates are moved to a `To_Be_Deleted` folder.

EECS3311 course project. The Stage 1 design documents are in `docs/`.

## Features
| ID | Feature |
|---|---|
| F01 | Authorize and manage the folders SortBot may touch |
| F02 | Scan a folder and build a file inventory |
| F03 | Classify files by name rules, then by content with the AI |
| F04 | Generate an organization plan (the AI proposes folders) |
| F05 | Review, edit and approve the plan |
| F06 | Execute the plan with progress and a log |
| F07 | Undo any run, even after a restart |
| F08 | Find old, duplicate and temporary files and move them to `To_Be_Deleted` |
| F09 | Give instructions in plain language (chat) |
| F10 | Remember rules and preferences between sessions |
| F11 | History of every action and report export (CSV or Markdown) |

## Prerequisites
- JDK 17 or newer (`javac -version` must work)
- Apache Maven 3.9 or newer (`mvn -version` must work)
- For the AI features, either a Google AI Studio API key (Gemini) or a local Ollama install

## Setup
1. Clone the repository and open a terminal in its folder.
2. Install dependencies and run the tests: `mvn clean test`
3. Copy the example configuration. The real `.env` is ignored by git and must never be committed.
   - Windows: `copy .env.example .env`
   - macOS or Linux: `cp .env.example .env`
4. Open `.env` and fill in your own values:
   ```
   LLM_PROVIDER=gemini
   LLM_MODEL=<exact model id from Google AI Studio>
   LLM_API_KEY=<your key>
   ```
   To use a local model instead, set `LLM_PROVIDER=ollama`, `OLLAMA_URL` and `OLLAMA_MODEL`.
5. Compile once: `mvn -q compile`

SortBot reads `.env` from the folder you launch it in. Without a key, everything except the AI parts of classification, planning and chat still works.

## Running the GUI
Windows: `.\sortbot-gui.cmd`    macOS or Linux: `./sortbot-gui.sh`

Tabs: Folders, Scan, Plan, Cleanup, Chat, Rules, History. Nothing changes on disk until you approve a plan.

## Running the command line
Windows: `.\sortbot.cmd <command>`    macOS or Linux: `./sortbot.sh