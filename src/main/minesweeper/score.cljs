(ns minesweeper.score)

(defn load-db []
  (if-let [s (.getItem js/localStorage "scores")]
    (js->clj (.parse js/JSON s) :keywordize-keys true)
    {}))

(defn store-db [db]
  (.setItem js/localStorage "scores"
            (.stringify js/JSON (clj->js db))))

(defn new-high-score? [skill-level time]
  (let [db (load-db)]
    (< time (get-in db [skill-level :time] 9999))))

(defn save-score [skill-level name t]
  (let [db (load-db)]
    (store-db (assoc db skill-level {:name name :time t}))))
