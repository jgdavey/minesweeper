(ns minesweeper.engine
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]))

(defn dbg [obj]
  #?(:cljs (js/console.log obj)
     :clj (prn obj))
  obj)

;; Board / Coords

(s/def :space/mine? boolean?)
(s/def :space/flagged? boolean?)
(s/def :space/revealed? boolean?)

(s/def :space/mine-count (s/int-in 1 9))

(s/def :space/text string?)

(s/def :board/space
  (s/keys :req [:space/mine?
                :space/text]
          :opt [:space/mine-count
                :space/revealed?
                :space/flagged?]))

(s/def :minesweeper/board
  (s/with-gen
    (s/coll-of
     (s/coll-of :board/space
                :kind vector?
                :min-count 1)
     :kind vector?
     :min-count 1)
    #(gen/bind (gen/tuple (gen/choose 2 12) (gen/choose 2 12))
               (fn [[width height]]
                 (gen/vector (gen/vector (s/gen :board/space) width)
                             height)))))

(def max-width 99)

(s/def :board/coord (s/int-in 0 (inc max-width)))

(s/def :board/coords (s/tuple :board/coord :board/coord))

;; Cards

(s/def :card/type keyword?)
(s/def :card/amount nat-int?)

(defmulti card :card/type)

(defmethod card :attack [_]
  (s/keys :req [:card/type]))

(defmethod card :reveal [_]
  (s/keys :req [:card/type
                :card/amount]))

(defmethod card :strengthen [_]
  (s/keys :req [:card/type
                :card/amount]))

(defmethod card :defend [_]
  (s/keys :req [:card/type]))

(s/def :minesweeper/card
  (s/multi-spec card :card/type))

;; Actions

(s/def :action/type keyword?)

(s/def :action.card/args (s/tuple :minesweeper/card))

(s/def :action.attack/args :board/coords)

(defmulti action-args :type)

(defmethod action-args :action/play-card [_]
  (s/keys :req-un [:action/type
                   :action.card/args]))

(defmethod action-args :action/attack-space [_]
  (s/keys :req-un [:action/type
                   :action.attack/args]))

(s/def :minesweeper/action+args (s/multi-spec action-args :type))

(s/def :minesweeper/action
  (s/with-gen
    (s/and (s/cat :type keyword?
                  :args (s/* any?))
           :minesweeper/action+args)
    #(gen/fmap (fn [{:keys [type args]}] (into [type] args))
               (s/gen :minesweeper/action+args))))

;; Board settings and generation

(s/def :board/width (s/int-in 1 max-width))

(s/def :board/height (s/int-in 1 max-width))

(s/def :board/mine-count (s/int-in 0 (* max-width max-width)))

(defn- settings-gen []
  (gen/fmap
   (fn [[w h b]]
     {:board/width w
      :board/height h
      :board/mine-count b})
   (gen/bind (gen/tuple (s/gen :board/width)
                        (s/gen :board/height))
             (fn [[w h]]
               (gen/tuple (gen/return w)
                          (gen/return h)
                          (s/gen (s/int-in 0 (* w h))))))))

(s/def :board/settings
  (s/with-gen
    (s/keys :req [:board/width
                  :board/height
                  :board/mine-count])
    settings-gen))

(defn neighbor-coords [x y]
  (->> (for [i [-1 0 1]
             j [-1 0 1]
             :when  (not= [i j] [0 0])]
         [(+ i x) (+ j y)])
       (remove #(some neg? % ))))

(defn- generate-blank-board
  [{:board/keys [width height mine-count]}]
  (let [total-squares (* width height)
        non-mines-squares (- total-squares mine-count)
        base (concat (repeat mine-count true)
                     (repeat non-mines-squares false))
        ->space (fn [mine?]
                  #:space{:mine? mine?
                          :revealed? false})]
    (into []
          (comp
           (map ->space)
           (partition-all width))
          (shuffle base))))

(def with-coords
  (map-indexed
   (fn [i row]
     (into []
           (map-indexed
            (fn [j space]
              [space [i j]]))
           row))))

(defn xform-with-coords
  [xform]
  (comp
   with-coords
   cat
   xform))

(defn transform-board
  [board xform]
  (let [width (count (first board))]
    (into [] (comp with-coords
                   cat
                   xform
                   (map first)
                   (partition-all width))
          board)))

(defn map-spaces
  "f is a fn that takes [space [x y]] as an arg, and must return only the space itself"
  [f board]
  (transform-board board (map #(vector (f %) (peek %)))))

(defn revealable? [space])

(defn annotate-counts [board]
  (map-spaces
   (fn [[space [x y]]]
     (assoc space :space/mine-count
            (->>
             (neighbor-coords x y)
             (map #(get-in board %))
             (filter :space/mine?)
             count)))
   board))

(defn annotate-text [board]
  (map-spaces
   (fn [[{:space/keys [mine-count] :as space} _]]
     (assoc space :space/text (if (zero? mine-count) " " (str mine-count))))
   board))

(s/fdef generate-board
  :args (s/cat :settings :board/settings)
  :ret :minesweeper/board)

(defn generate-board
  [settings]
  (->
   (generate-blank-board settings)
   annotate-counts
   annotate-text))

(defn propogatable [board path]
  (if-let [{:space/keys [mine-count]} (get-in board path)]
    (if (and mine-count (zero? mine-count))
      (filter #(get-in board %)
       (apply neighbor-coords path))
      [path])
    []))

(defn propogated-coordinates [board coords]
  (loop [all #{coords}
         [path & more] [coords]]
    (if-not path
      all
      (let [neighbs (propogatable board path)]
        (recur (into all neighbs)
               (concat more (remove all neighbs)))))))

(defn reveal-space [{:minesweeper/keys [board] :as game} path]
  (let [{:space/keys [revealed? mine?]} (get-in board path)
        paths (cond
                revealed? []
                mine? [path]
                :else (propogated-coordinates board path))
        new-board (reduce (fn [b path]
                            (assoc-in b (conj path :space/revealed?) true))
                          board
                          paths)]
    (-> game
        (assoc :minesweeper/board new-board)
        (update-in [:minesweeper/turn :turn/attack] dec))))

(defn remove-first
  ([pred]
   (fn [rf]
     (let [done (volatile! false)]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (if (and (not @done) (pred input))
            (do
              (vreset! done true)
              result)
            (rf result input))))))))

(defn discard [deck card]
  (-> deck
      (update :deck/hand #(into [] (remove-first #{card}) %))
      (update :deck/discard conj card)))

(defmulti do-card (fn [_ card] (:card/type card)))

(defmethod do-card :attack [game card]
  (let [{:player/keys [strength]} (:minesweeper/player game)]
    (update-in game [:minesweeper/turn :turn/attack] + strength)))

(defmethod do-card :defend [game card]
  (let [{:player/keys [dexterity]} (:minesweeper/player game)]
    (update-in game [:minesweeper/turn :turn/block] + dexterity)))

(defmethod do-card :reveal [game card]
  (let [{:card/keys [amount]} card
        possibles (into [] (comp with-coords
                                 cat
                                 (filter #(revealable? (first %)))
                                 (map peek))
                        (:minesweeper/board game))]
    (dbg {:possibles possibles})
    (reduce reveal-space game (take amount (shuffle possibles)))))

(defn play-card [game card]
  (-> game
      (do-card card)
      dbg
      (update :minesweeper/deck discard card)))

;; REPL summaries

(defn space->str [{:space/keys [mine? mine-count revealed?]}]
  (cond
    mine? '*
    (zero? mine-count) '_
    :else mine-count))

(defn board->summary [board]
  (mapv #(mapv space->str %) board))

(defn pr-board [board]
  (str/join "\n"
            (map
             #(str/join " " %)
             (board->summary board))))


(defn generate-deck []
  (concat
   (repeat 4 #:card{:type :attack})
   (repeat 4 #:card{:type :defend})
   (repeat 1 #:card{:type :reveal :amount 2})))

;; Game state

(defn make-game
  ([]
   (make-game #:settings{:board #:board{:width 5
                                        :height 5
                                        :mine-count 5}
                         :player #:player{:health 10
                                          :max-health 10
                                          :strength 5
                                          :dexterity 3}}))
  ([{:settings/keys [board player]}]
   (let [board (generate-board board)
         deck (shuffle (generate-deck))
         [hand draw] (split-at (:player/dexterity player) deck)]
     {:minesweeper/board board
      :minesweeper/deck {:deck/full deck
                         :deck/draw draw
                         :deck/discard []
                         :deck/hand hand}
      :minesweeper/player player
      :minesweeper/turn #:turn{:actor :player
                               :attack 1
                               :block 0}})))

(comment

  (remove-ns 'minesweeper.engine)

  (gen/generate
   (s/gen :minesweeper/action))

  (println
   (pr-board
    (generate-board )
      ))

  (gen/generate
   (s/gen :board/settings))


  (def game
    (make-game))
  
  )
