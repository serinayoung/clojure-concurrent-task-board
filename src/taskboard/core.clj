(ns taskboard.core
  (:require [taskboard.tasks :as t])
  (:import (java.util.concurrent.atomic AtomicLong))
  (:gen-class))

;; IDENTITY vs STATE
;; `board` is the IDENTITY: one stable reference that never changes.
;; The map inside it is the STATE: an immutable value.
;; swap! replaces the whole value with a new one; old values are never edited.

;; VALIDATOR: runs before every new value is accepted.
;; If it returns false, the change is rejected and the atom keeps its old value.
(defn valid-board? [tasks]
  (every? (fn [[_ task]] (contains? t/valid-statuses (:status task)))
          tasks))

(def board (atom {} :validator valid-board?))

;; WATCHER: called after every successful change with the old and new value.
;; It only counts changes; it never changes the atom itself.
(def change-count (AtomicLong. 0))

(add-watch board :counter
           (fn [_key _ref old-state new-state]
             (when (not= old-state new-state)
               (.incrementAndGet change-count))))

;; ---------------- Settings ----------------
(def adder-threads      4)
(def tasks-per-adder   25)     ; 4 x 25 = 100 tasks expected
(def updater-threads    3)
(def completer-threads  3)
(def ops-per-worker    40)
(def expected-total (* adder-threads tasks-per-adder))

(defn pause [] (Thread/sleep (long (rand-int 4))))   ; mix the threads up

;; ---------------- Worker threads ----------------
;; Each worker is a `future`, so it runs on its own thread.
;; ALL changes go through swap! + a pure function from tasks.clj.
;; swap! reads the current map, applies the function, and only saves the
;; result if no other thread changed the atom in the meantime.
;; If one did, swap! retries with the fresh map, so no update is lost.

(defn start-adder [n]
  (future
    (dotimes [i tasks-per-adder]
      (swap! board t/add-task (str "Task from adder-" n " #" (inc i)))
      (pause))))

(defn start-updater [_n]
  (future
    (dotimes [_ ops-per-worker]
      (swap! board t/update-task (inc (rand-int expected-total)) :in-progress)
      (pause))))

(defn start-completer [_n]
  (future
    (dotimes [_ ops-per-worker]
      (swap! board t/complete-task (inc (rand-int expected-total)))
      (pause))))

;; ---------------- Reader ----------------
;; @board gives ONE immutable snapshot. Even while other threads keep
;; writing, this value cannot change under us, so the counts per status
;; always add up to the number of tasks in that snapshot.
(defn print-snapshot [label]
  (let [snapshot @board                        ; one consistent value
        counts   (t/summarise snapshot)
        total    (count snapshot)
        sum      (reduce + (vals counts))]
    (println (format "%s total=%3d  todo=%3d  in-progress=%3d  done=%3d  consistent=%s"
                     label total (:todo counts) (:in-progress counts)
                     (:done counts) (= total sum)))))

(defn start-reader [running?]
  (future
    (loop [i 1]
      (when @running?
        (print-snapshot (format "[reader #%02d]" i))
        (Thread/sleep 15)
        (recur (inc i))))))

;; ---------------- Validator demo ----------------
;; Try to put an invalid status in. The validator rejects it
;; and the board keeps its old value.
(defn validator-demo []
  (println "\n=== VALIDATOR DEMO: setting task 1 to :banana ===")
  (try
    (swap! board assoc-in [1 :status] :banana)
    (println "Accepted (this should not happen!)")
    (catch IllegalStateException _
      (println "Rejected by validator. Task 1 is still:"
               (get-in @board [1 :status])))))

;; ---------------- Main ----------------
(defn -main [& _args]
  (println "Starting" adder-threads "adders," updater-threads "updaters,"
           completer-threads "completers and 1 reader...\n")
  (let [running? (atom true)                   ; stop flag for the reader loop
        reader   (start-reader running?)
        workers  (doall (concat (map start-adder     (range 1 (inc adder-threads)))
                                (map start-updater   (range 1 (inc updater-threads)))
                                (map start-completer (range 1 (inc completer-threads)))))]
    (run! deref workers)                       ; wait for every worker to finish
    (reset! running? false)                    ; tell the reader to stop
    @reader)

  (println "\n=== FINAL SNAPSHOT ===")
  (print-snapshot "[final]    ")
  (let [final @board
        ids   (sort (keys final))]
    (println "Expected tasks :" expected-total)
    (println "Actual tasks   :" (count final))
    (println "Ids 1..100 with no gaps or duplicates:"
             (= ids (range 1 (inc expected-total))))
    (println "Changes seen by watcher:" (.get change-count))
    (println "Grouped by status:" (t/group-by-status final))
    (println "Progress:" (t/progress final) "% done")
    (println (if (= expected-total (count final))
               "RESULT: no lost updates - swap! kept every add."
               "RESULT: some updates were LOST!")))

  (validator-demo)
  (shutdown-agents))
