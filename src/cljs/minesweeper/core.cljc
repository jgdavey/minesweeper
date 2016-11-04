(ns minesweeper.core
  (:require [clojure.set :as set]
            [clojure.string :as str]))

;                           w  h  b
(def levels {:beginner     [9  9  10]
             :intermediate [16 16 40]
             :expert       [24 20 99]})

(declare propogated-coordinates)

(def ^:private bomb? (partial = :bomb))

(defn random-tuples [x y]
  (repeatedly #(vector (rand-int x) (rand-int y))))

(defn add-bombs [board c]
  (let [w (count board)
        h (count (first board))
        coords (take c (distinct (random-tuples w h)))]
    (reduce (fn [b coord]
              (assoc-in b coord :bomb)) board coords)))

(defn neighbors [board i j]
  (remove nil?
          (for [x [-1 0 1]
                y [-1 0 1]
                :when (not= [x y] [0 0])
                :let [v (get-in board [(+ i x) (+ j y)])]]
            v)))

(defn neighbor-bombs [board i j]
  (->> (neighbors board i j)
       (filter bomb?)
       count))

(defn annotate [board]
  (mapv (fn [row r]
          (mapv (fn [space c]
                  (let [b? (bomb? space)]
                    {:bomb? b?
                     :revealed? false
                     :count (when-not b? (neighbor-bombs board r c))
                     :coords [r c]}))
                row (range))) board (range)))

(defn generate-board
  ([]
   (generate-board 10 10 10))
  ([level]
   (let [settings (levels level)]
     (apply generate-board settings)))
  ([w h bombs]
   (let [base (mapv (fn [x]
                      (mapv (fn [y] nil) (range w)))
                    (range h))]
     (-> (add-bombs base bombs)
         annotate))))


(defn spaces [board]
  (apply concat board))

(defn won? [board]
  (let [s (spaces board)]
    (or
     (->> s
          (filter :bomb?)
          (every? :flagged?))
     (->> s
          (remove :bomb?)
          (every? :revealed?)))))

(defn- count-attr [board attr]
  (->> board spaces
       (filter attr)
       count))

(defn bomb-count [board]
  (count-attr board :bomb?))

(defn flagged-count [board]
  (count-attr board :flagged?))

(defn revealed-count [board]
  (count-attr board :revealed?))

(defn remaining-count [board]
  (- (bomb-count board) (flagged-count board)))

(defn move-count [board]
  (+ (revealed-count board) (flagged-count board)))

(defn space->str [space]
  (if (:bomb? space)
    "*"
    (str (:count space))))

(defn reveal-all-bombs [board]
  (mapv (fn [row] (mapv (fn [s]
                          (if (or (:bomb? s) (:flagged? s))
                            (assoc s :revealed? true)
                            s)) row)) board))

(defn print-board [board]
  (str/join "\n" (map #(apply str (map space->str %)) board)))

(defn reveal-coords [board coords]
  (let [propogated (propogated-coordinates board coords)]
    (reduce (fn [b coord]
              (assoc-in b (conj coord :revealed?) true))
            board propogated)))

(defn propogated-coordinates [board coords]
  (let [space (get-in board coords)]
    (loop [all #{coords}
           queue []
           space space]
      (if space
        (if (zero? (:count space))
          (let [new-neighbors (remove (comp all :coords) (apply neighbors board (:coords space)))
                next-queue (vec (if (seq queue)
                                  (concat queue new-neighbors)
                                  new-neighbors))
                popped (if (seq next-queue) (pop next-queue) [])
                next-space (when (seq next-queue) (peek next-queue))]
            (recur (into all (map :coords new-neighbors)) popped next-space))
          (recur (conj all (:coords space)) (when (seq queue) (pop queue)) (peek queue)))
        all))))

(comment

(println (print-board (generate-board 5 7 10)))

)
