# Clojure Concurrent Task Board

A lock-free concurrent task board written in Clojure. A single atom holds every task, ten threads add, update and complete tasks at the same time, and a reader thread takes consistent snapshots while they work — with no locks and no lost updates.

The project demonstrates Clojure's separation of **identity** (a stable reference, the atom) from **state** (an immutable value that the atom points to).

![Output of ./run.sh](images/output.png)

## Features

- **One atom, one map** — `board` holds a map of task id → task, e.g. `{1 {:title "..." :status :todo}}`
- **Lock-free updates** — every change goes through `swap!` with a pure function, so concurrent updates are retried instead of lost
- **10 concurrent worker threads** — 4 adders, 3 updaters and 3 completers, each started with `future`
- **Consistent reader** — a reader thread dereferences `@board` every 15 ms; each snapshot is immutable, so its counts always add up
- **Validator** — rejects any state with an invalid status (demonstrated with `:banana`)
- **Watcher** — counts every successful change without modifying the board

## How it works

For each update, `swap!`:

1. reads the current map held by the atom;
2. applies a pure function (`add-task`, `update-task`, `complete-task`) to get a new map;
3. saves the new map only if the atom still holds the map from step 1 (compare-and-set);
4. if another thread changed it first, retries from step 1 with the latest map.

Because the update functions have no side effects, a retry is always safe. New task ids are computed inside `add-task` from the map itself, so two threads can never create the same id.

## Results

Every run finishes with all 100 tasks present and no lost updates:

```
Expected tasks : 100
Actual tasks   : 100
Ids 1..100 with no gaps or duplicates: true
RESULT: no lost updates - swap! kept every add.
```

Every reader snapshot reports `consistent=true` (todo + in-progress + done = total), even while the threads are still running.

## Project structure

```
taskboard/
├── project.clj            # Leiningen project (Clojure 1.11.4)
├── run.sh                 # Prints Java/Leiningen versions, then runs the app
└── src/taskboard/
    ├── core.clj           # Atom, validator, watcher, worker threads, reader, main
    └── tasks.clj          # Pure functions on the task map
```

## Requirements

- Linux (tested on Ubuntu 22.04 LTS in VirtualBox)
- Java JDK 11+ (tested with OpenJDK 17)
- Leiningen 2.9+

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk leiningen
```

## Run

```bash
git clone https://github.com/<your-username>/clojure-concurrent-task-board.git
cd clojure-concurrent-task-board
chmod +x run.sh
./run.sh
```

The first run downloads Clojure, so it needs an internet connection.

## Concepts demonstrated

| Concept | Where |
| --- | --- |
| Identity vs state | `board` atom holding an immutable map |
| Pure functions | `add-task`, `update-task`, `complete-task`, `summarise` |
| Immutable data | `assoc` / `assoc-in` return new maps; old values are never changed |
| Higher-order functions | `swap!`, `map`, `filter`, `group-by`, `frequencies` |
| Lock-free concurrency | `swap!` with compare-and-set retries |

## Background

Developed as part of the KT44502 Distributed Systems course at Universiti Malaysia Sabah (UMS), 2026.
