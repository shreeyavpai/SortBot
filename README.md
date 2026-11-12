# SortBot

SortBot is an AI agent that organizes cluttered personal folders. The language model only **proposes** a plan; deterministic code validates it, **you approve it**, and a separate executor carries it out with a full action log and undo. SortBot never deletes your files: cleanup candidates are moved to a `To_Be_Deleted` folder.

EECS3311 course project. Design documents are in `docs/`.

## Prerequisites
- JDK 17 or newer (`javac -version` must work)
- Apache Maven 3.9 or newer (`mvn -version` must work)
- Optional: a Google AI Studio key (Gemini) or a local Ollama install, for the AI features

## Setup
1. Clone the repository and open a terminal in its folder.
2. Copy the example configuration and add your own key (the real `.env` is git-ignored and must never be committed):
   - Windows: `copy .env.example .env`
   - macOS/Linux: `cp .env.example .env`
3. Edit `.env` and set `LLM_API_KEY`, `LLM_PROVIDER` and `LLM_MODEL`.
4. Build and run the tests: `mvn test`

## Running
GUI and CLI launch commands will be added here as each is completed. (Status table below.)

## Project status
| Area | Status |
|---|---|
| Folder authorization, scanning, plans, execution, undo, action log, cleanup rules, reports | Implemented and unit tested |
| LLM clients, classifier, planner, tools, agent controller | In progress |
| CLI | In progress |
| GUI | In progress |

## Safety design
- Only `FileSystemGateway` touches user files, and only inside folders you authorized.
- There is no delete operation. Cleanup moves files to `To_Be_Deleted`.
- Nothing changes without an approved plan, and every action is logged and undoable.
- Only file names and short text snippets are sent to a language model, never whole files.
