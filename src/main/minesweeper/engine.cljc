(ns minesweeper.engine
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

(defn add-bombs
  ([board bomb-count first-space]
   (let [w (count board)
         h (count (first board))
         coords  (->> (random-tuples w h)
                      (remove #(= first-space %))
                      distinct
                      (take bomb-count))]
     (reduce (fn [b coord]
               (assoc-in b (conj coord :bomb?) true)) board coords))))

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
   (generate-board w h bombs [0 0]))
  ([w h bombs first-space]
   (-> (generate-base w h)
       (add-bombs bombs first-space)
       annotate)))

(defn spaces [board]
  (into [] cat board))

(defn won? [board]
  (let [s (spaces board)]
    (or
     (= (into #{} (filter :flagged?) s)
        (into #{} (filter :bomb?) s))
     (->> s
          (remove :bomb?)
          (every? :revealed?)))))

(defn lost? [board]
  (let [s (spaces board)]
    (->> s
         (filter :bomb?)
         (some :revealed?))))

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

(defn mark-matching [board match-fn attr]
  (mapv (fn [row]
          (mapv #(cond-> %
                   (match-fn %) (assoc attr true))
                row))
        board))

(defn reveal-coords [board coords]
  (let [propogated (propogated-coordinates board coords)]
    (reduce (fn [b coord]
              (assoc-in b (conj coord :revealed?) true))
            board propogated)))

(defn propogate? [board path]
  (when-let [{:keys [count]} (get-in board path)]
    (and count (zero? count))))

(defn propogated-coordinates [board coords]
  (loop [all #{coords}
         [path & more] [coords]]
    (if-not path
      all
      (let [neighbs (if (propogate? board path)
                      (map :path (apply neighbors board path))
                      [path])]
        (recur (into all neighbs)
               (concat more (remove all neighbs)))))))

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
