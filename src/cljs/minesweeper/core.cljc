(ns minesweeper.core
  (:require [clojure.set :as set]
            [clojure.string :as str]))

;                           w  h  b
(def levels {:beginner     [9  9  10]
             :intermediate [16 16 40]
             :expert       [24 20 99]})

(declare propogated-coordinates)

(defn neighbors [board i j]
  (for [x [-1 0 1]
        y [-1 0 1]
        :when (not= [x y] [0 0])
        :let [v (get-in board [(+ i x) (+ j y)])]
        :when v]
    v))

(defn neighbor-bombs [board i j]
  (->> (neighbors board i j)
       (filter :bomb?)
       count))

;; Board generation

(defn random-tuples [x y]
  (repeatedly #(vector (rand-int x) (rand-int y))))

(defn add-bombs [board c]
  (let [w (count board)
        h (count (first board))
        coords (take c (distinct (random-tuples w h)))]
    (reduce (fn [b coord]
              (assoc-in b (conj coord :bomb?) true)) board coords)))

(defn annotate [board]
  (mapv (fn [row]
          (mapv (fn [{:keys [bomb? path] :as space}]
                  (assoc space
                         :revealed? false
                         :count (when-not bomb?
                                  (apply neighbor-bombs board path))))
                row))
        board))

(defn generate-base [w h]
  (mapv (fn [x]
          (mapv (fn [y] {:path [x y]}) (range w)))
        (range h)))

(defn generate-board
  ([]
   (generate-board 10 10 10))
  ([level]
   (let [settings (levels level)]
     (apply generate-board settings)))
  ([w h bombs]
   (-> (generate-base w h)
       (add-bombs bombs)
       annotate)))

(defn spaces [board]
  (into [] cat board))

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

(defn reveal-all-bombs [board]
  (mapv (fn [row] (mapv (fn [s]
                          (if (or (:bomb? s) (:flagged? s))
                            (assoc s :revealed? true)
                            s)) row)) board))
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
        (if (and (:count space)
                 (zero? (:count space)))
          (let [new-neighbors (remove (comp all :path) (apply neighbors board (:path space)))
                next-queue (vec (if (seq queue)
                                  (concat queue new-neighbors)
                                  new-neighbors))
                popped (if (seq next-queue) (pop next-queue) [])
                next-space (when (seq next-queue) (peek next-queue))]
            (recur (into all (map :path new-neighbors)) popped next-space))
          (recur (conj all (:path space)) (when (seq queue) (pop queue)) (peek queue)))
        all))))

;; Visual rep
(def bomb-marker '*)

(defn space->str [space]
  (if (:bomb? space)
    bomb-marker
    (:count space)))

(defn board->summary [board]
  (mapv #(mapv space->str %) board))

(defn summary->board [summary]
  (mapv
   (fn [row i]
     (mapv
      (fn [space j]
        (if (= bomb-marker space)
          {:bomb? true,  :path [i j], :count nil}
          {:bomb? false, :path [i j], :count space}))
      row
      (range)))
   summary
   (range)))

(defn print-board [board]
  (pr-str (board->summary board)))

(comment

(summary->board (board->summary (generate-board 7 7 10)))

)
