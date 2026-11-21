(ns taskboard.tasks)

(def valid-statuses #{:todo :in-progress :done})

(defn next-id [tasks]
  (inc (reduce max 0 (keys tasks))))

(defn make-task [title]
  {:title title :status :todo})

(defn add-task [tasks title]
  (assoc tasks (next-id tasks) (make-task title)))

(defn update-task [tasks id status]
  (if (and (contains? tasks id)
           (contains? valid-statuses status)
           (not= :done (get-in tasks [id :status])))
    (assoc-in tasks [id :status] status)
    tasks))

(defn complete-task [tasks id]
  (if (contains? tasks id)
    (assoc-in tasks [id :status] :done)
    tasks))

(defn update-where [tasks pred? f]
  (reduce-kv (fn [acc id task]
               (assoc acc id (if (pred? task) (f task) task)))
             {}
             tasks))

(defn complete-all [tasks]
  (update-where tasks
                #(= :in-progress (:status %))
                #(assoc % :status :done)))

(defn tasks-with-status [tasks status]
  (filter (fn [[_id task]] (= status (:status task))) tasks))

(defn group-by-status [tasks]
  (into {}
        (map (fn [[status pairs]] [status (sort (map first pairs))])
             (group-by (comp :status val) tasks))))

(defn titles [tasks]
  (map (comp :title val) tasks))

(defn summarise [tasks]
  (merge (zipmap valid-statuses (repeat 0))
         (frequencies (map :status (vals tasks)))))

(defn progress [tasks]
  (let [counts (summarise tasks)
        total  (reduce + (vals counts))]
    (if (zero? total)
      0
      (Math/round (* 100.0 (/ (:done counts) total))))))
