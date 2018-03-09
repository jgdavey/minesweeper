(ns minesweeper.app
  (:require [goog.events :as events]
            [cljs.core.async :as async
             :refer [put! <! >! chan timeout]
             :refer-macros [go alt!]]
            [cljsjs.react]
            [cljsjs.react.dom]
            [om.next :as om :refer-macros [defui]]
            [minesweeper.core :as mine :refer [generate-board]]
            [minesweeper.score :as score]
            [sablono.core :as html :refer [html]])
  (:import [goog.string format]))

(defonce default-level :beginner)

(defn choose-setting [settings level]
  (let [settings (assoc settings :level level)]
    (if-let [opts (mine/levels level)]
      (merge settings (zipmap [:width :height :bombs] opts))
      settings)))

(def null-state
  {:game/won false
   :game/lost false
   :game/time 0
   :game/grid []})

(defonce app-state
  (atom (assoc null-state
               :game/settings (choose-setting {} default-level))))

(defn high-scores []
  (let [db (score/load-db)
        format-entry (fn [[k v]] (format "%-12s\t%-4d\t%s" (name k) (:time v) (:name v)))]
    (apply str (interpose "\n\n" (map format-entry db)))))

(def seconds-difference (comp #(/ % 1000) -))

(defn game-timer [r]
  (let [then (js/Date.)
        kill (chan 1)]
    (go (loop []
          (let [[v c] (alts! [kill (timeout 1000)])]
            (when-not (identical? c kill)
              (om/merge! r {:game/time (seconds-difference (js/Date.) then)}
                         '[:game/time])
              (recur)))))
    kill))

(defn get-name []
  (js/prompt "Congratulations!\n\nYou have achieved a new high score. What is your name?"))

(defn reset-game [st]
  (let [{:keys [width height bombs]} (:game/settings st)]
    (merge st
           null-state
           {:game/grid (generate-board width height bombs)})))

;; Om components

(defn button-label [{:keys [revealed? flagged? bomb? count]}]
  (if (or flagged? (not revealed?) bomb? (zero? count))
    ""
    (str count)))

(defn button-classes [{:keys [bomb? revealed? flagged? exploded? count]}]
  (str (cond
         (and revealed? bomb? exploded?) "exploded depressed bomb"
         (and revealed? flagged? (not bomb?)) "wrong depressed bomb"
         (and revealed? bomb?) "depressed bomb"
         revealed? "depressed"
         flagged? "flagged"
         :else "normal")
       " space-" count
       " space"))

(defui Board
  Object
  (render [this]
    (let [board (om/props this)]
      (html
       [:div#board
        (map-indexed
         (fn [i row]
           [:div.row {:key i}
            (map
             (fn [space]
               [:div.space {:key (:path space)
                            :class (button-classes space)
                            :onContextMenu (fn [e]
                                             (.preventDefault e)
                                             (when-not (:revealed? space)
                                               (om/transact! this
                                                             `[(space/flag {:path ~(:path space)})])))
                            :onClick (fn [e]
                                       (.preventDefault e)
                                       (when-not (:flagged? space)
                                         (om/transact! this `[(space/reveal {:path ~(:path space)})])))}
                [:span (button-label space)]])
             row)])
         board)]))))

(def board-view (om/factory Board))

(defui Settings
  Object
  (render [this]
    (let [{:keys [level] :as settings} (om/props this)]
      (html
       [:form#settings
        [:select {:value (name (:level settings))
                  :onChange (fn [e]
                              (let [level (keyword (.. e -target -value))]
                                (om/transact! this
                                              `[(game/update-settings ~(choose-setting settings level))])))}
         [:option {:value "beginner"} "Beginner"]
         [:option {:value "intermediate"} "Intermediate"]
         [:option {:value "expert"} "Expert"]
         [:option {:value "custom"} "Custom"]]
        (when (= :custom (:level settings))
          [:dl
           (mapcat (fn [attr i]
                     [[:dt {:key (str "key-" i)}
                       [:label {:htmlFor (name attr)} (name attr)]]
                      [:dd {:key (str "val-" i)}
                       [:input {:onChange (fn [e]
                                            (om/transact! this
                                                          `[(game/update-settings ~{attr (int (.. e -target -value))})]))
                                :id (name attr)
                                :name (name attr)
                                :value (get settings attr)}]]])
                   [:width :height :bombs] (range))])]))))

(def settings-view (om/factory Settings))

(defui Score
  Object
  (render [this]
    (let [{:game/keys [grid time lost won] :as props} (om/props this)]
      (html
       [:div#score
        [:span.remaining
         (format "%03d" (mine/remaining-count grid))]
        [:span.moves
         (format "%03d" time)]
        [:span {:class (str (cond
                              lost "lost"
                              won  "won"
                              :else "normal") " face")
                :onClick (fn [e]
                           (om/transact! this '[(game/start)]))}]]))))

(def score-view (om/factory Score))

(defui App
  static om/IQuery
  (query [_]
    '[:game/settings
      :game/won
      :game/lost
      :game/time
      :game/timer
      :game/win-chan
      :game/grid])
  Object
  (render [this]
    (let [props (om/props this)]
      (html
       [:div
        [:h1 "Minesweeper"]
        (settings-view (:game/settings props))
        [:div#app
         (score-view props)
         (board-view (:game/grid props))]
        [:p
         [:a {:href "#"
              :onClick (fn [e] (js/alert (high-scores)))}
          "High Scores"]]]))))


(defmulti read om/dispatch)
(defmulti mutate om/dispatch)

(defonce parser (om/parser {:read read :mutate mutate}))

(defmethod read :default
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:value :not-found})))

(defmethod mutate :default [_ _ _])

(defn checking-for-win [state f & args]
  (let [st (apply f state args)
        board (get-in st [:game/grid])
        won (mine/won? board)]
    (when won
      (when-let [{:keys [game/win-chan game/timer]} st]
        (put! win-chan {:level (get-in st [:game/settings :level])
                        :time (get-in st [:game/time])})

        (put! timer :done)))
    (assoc st :game/won won)))

(defmethod mutate 'space/reveal
  [{:keys [state] :as env} key params]
  {:value {:keys [:game/grid]}
   :action (fn []
             (let [[row col :as path] (:path params)
                   {:keys [width height bombs]} (:game/settings @state)]
               (when (zero? (mine/revealed-count (get-in @state [:game/grid])))
                 (swap! state assoc :game/grid (generate-board width height bombs path)))
               (let [st @state
                     space (get-in st [:game/grid row col])]
                 (when-not (or (:game/lost st) (:game/won st))
                   (if (:bomb? space)
                     (do
                       (when-let [timer (:game/timer st)]
                         (put! timer :done))
                       (swap! state (fn [st]
                                      (-> st
                                          (update-in [:game/grid] mine/reveal-all-bombs)
                                          (assoc :game/lost true)
                                          (assoc-in [:game/grid row col :exploded?] true)))))
                     (swap! state checking-for-win
                            update :game/grid
                            mine/reveal-coords path))))))})

(defmethod mutate 'space/flag
  [{:keys [state] :as env} key params]
  {:value {:keys [:game/grid]}
   :action (fn []
             (let [[row col :as path] (:path params)]
               (swap! state checking-for-win
                      update-in [:game/grid row col :flagged?] not)))})

(defmethod mutate 'game/start
  [{:keys [state reconciler] :as env} key params]
  {:action
   (fn []
     (let [st @state]
       (when-let [timer (get st :game/timer)]
         (put! timer :done))
       (let [win-chan (chan 1)
             timer (game-timer reconciler)]
         (go (when-let [winner (<! win-chan)]
               (when (score/new-high-score? (:level winner) (:time winner))
                 (score/save-score (:level winner) (get-name) (:time winner)))))
         (swap! state (fn [st]
                        (-> st
                            reset-game
                            (assoc :game/timer timer
                                   :game/win-chan win-chan)))))))})

(defmethod mutate 'game/update-settings
  [{:keys [state] :as env} key params]
  {:value {:keys [:game/settings]}
   :action #(swap! state update :game/settings merge params)})

(def reconciler
  (om/reconciler
   {:state app-state
    :parser parser}))

(defn ^:export main []
  (enable-console-print!)
  (om/add-root!
   reconciler
   App
   (.getElementById js/document "content")))
