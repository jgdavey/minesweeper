(ns minesweeper.score
  (:require [cljs.reader :refer [read-string]]))

(defn load-db []
  (if-let [s (.getItem js/localStorage "scores")]
    (read-string s)
    {}))

(defn store-db [db]
  (.setItem js/localStorage "scores" (pr-str db)))

(defn new-high-score? [skill-level time]
  (let [db (load-db)]
    (< time (get-in db [skill-level :time] 9999))))

(defn save-score [skill-level name t]
  (let [db (load-db)]
    (store-db (assoc db skill-level {:name name :time t}))))
