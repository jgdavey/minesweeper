(ns minesweeper.app
    (:require-macros [cljs.core.async.macros :refer [go alt!]])
    (:require [goog.events :as events]
              [cljs.core.async :as async :refer [put! <! >! chan timeout]]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [minesweeper.core :as mine :refer [generate-board]]
              [minesweeper.score :as score])
    (:import [goog.string format]))

(defonce default-level :beginner)

(defn choose-setting [settings level]
  (let [settings (assoc settings :level level)]
    (if-let [opts (mine/levels level)]
      (merge settings (zipmap [:width :height :bombs] opts))
      settings)))

(defonce app-state
  (atom {:settings (choose-setting {} default-level)
         :game {:won? nil :lost? nil :board []}}))

(defn high-scores []
  (let [db (score/load-db)
        format-entry (fn [[k v]] (format "%-12s\t%-4d\t%s" (name k) (:time v) (:name v)))]
    (apply str (interpose "\n\n" (map format-entry db)))))

(defn reset-game [_]
  (let [{:keys [width height bombs]} (:settings @app-state)]
    {:lost? false
     :won? false
     :time 0
     :board (generate-board width height bombs)}))

(def seconds-difference (comp #(/ % 1000) -))

(defn game-timer []
  (let [then (js/Date.)
        kill (chan 1)]
    (go (loop []
          (let [[v c] (alts! [kill (timeout 1000)])]
            (when-not (identical? c kill)
              (swap! app-state assoc-in [:game :time] (seconds-difference (js/Date.) then))
              (recur)))))
    kill))

(defonce win-chan (chan 1))

(defn get-name []
  (js/prompt "Congratulations!\n\nYou have achieved a new high score. What is your name?"))

(defn start-game []
  (when-let [timer (get-in @app-state [:game :timer])]
    (put! timer :done))
  (let [timer (game-timer)]
    (swap! app-state update-in [:game] reset-game)
    (swap! app-state assoc-in [:game :timer] timer))
  (go (when-let [winner (<! win-chan)]
        (when (score/new-high-score? (:level winner) (:time winner))
          (score/save-score (:level winner) (get-name) (:time winner))))))

;; Om components

(defn button-label [{:keys [revealed? flagged? bomb? count]}]
  (if (or flagged? (not revealed?) bomb? (zero? count))
    ""
   (str count)))

(defn space-view [{:keys [bomb? revealed? flagged? exploded? count] :as space} owner]
  (reify
    om/IDisplayName
    (display-name [_] "Space")
    om/IRender
    (render [_]
      (dom/div #js {:className (str (cond
                                      (and revealed? bomb? exploded?) "exploded depressed bomb"
                                      (and revealed? flagged? (not bomb?)) "wrong depressed bomb"
                                      (and revealed? bomb?) "depressed bomb"
                                      revealed? "depressed"
                                      flagged? "flagged"
                                      :else "normal") " space")
                    :onContextMenu (fn [e]
                                     (.preventDefault e)
                                     (when-not revealed?
                                       (om/update! space :flagged? (not flagged?) :play)))
                    :onClick (fn [e]
                               (.preventDefault e)
                               (when-not flagged?
                                 (om/update! space :revealed? true :play)))}
               (dom/span nil (button-label space))))))

(defn board-view [board owner]
  (reify
    om/IDisplayName
    (display-name [_] "Board")
    om/IRender
    (render [_]
      (apply dom/div #js {:id "board"}
             (map #(apply dom/div #js {:className "row"}
                            (om/build-all space-view %)) board)))))

(defn setting-field [settings name]
  (let [attr (keyword name)]
    [(dom/dt nil
       (dom/label #js {:htmlFor name} name))
     (dom/dd nil
       (dom/input #js {:onChange (fn [e]
                                   (om/update! settings attr (int (.. e -target -value))))
                       :id name :name name :defaultValue (settings attr)}))]))

(defn settings-view [settings owner]
  (reify
    om/IDisplayName
    (display-name [_] "Settings")
    om/IRender
    (render [_]
      (dom/form #js {:id "settings"}
        (dom/select #js {:value (name (:level settings))
                         :onChange (fn [e]
                                     (let [level (keyword (.. e -target -value))]
                                       (om/transact! settings (fn [s] (choose-setting s level)))))}
          (dom/option #js {:value "beginner"} "Beginner")
          (dom/option #js {:value "intermediate"} "Intermediate")
          (dom/option #js {:value "expert"} "Expert")
          (dom/option #js {:value "custom"} "Custom"))
        (when (= :custom (:level settings))
          (apply dom/dl nil (mapcat (partial setting-field settings)
                                    ["width" "height" "bombs"])))))))

(defn score-view [state owner]
  (reify
    om/IDisplayName
    (display-name [_] "Score")
    om/IRender
    (render [_]
      (dom/div #js {:id "score"}
        (dom/span #js {:className "remaining"}
          (format "%03d" (mine/remaining-count (:board state))))
        (dom/span #js {:className "moves"}
          (format "%03d" (:time state)))
        (dom/span
          #js {:className (str (cond
                                 (:lost? state) "lost"
                                 (:won? state)  "won"
                                 :else "normal") " face")
               :onClick (fn [e] (start-game))} nil)))))

(defn app-view [app owner]
  (reify
    om/IDisplayName
    (display-name [_] "App")
    om/IRender
    (render [_]
      (dom/div nil
        (dom/h1 nil "Minesweeper")
        (om/build settings-view (:settings app))
        (dom/div #js {:id "app"}
          (om/build score-view (:game app))
          (om/build board-view (get-in app [:game :board])))
        (dom/p nil
          (dom/a #js {:href "#"
                      :onClick (fn [e] (js/alert (high-scores)))} "High Scores"))))))


(defn update-board-tx [tx-data root-cursor]
  (let [attr (peek (:path tx-data))
        value (:new-value tx-data)]
    (when (= (:tag tx-data) :play) ; user-initiated gameplay

      (when (mine/won? (get-in @root-cursor [:game :board]))
        (when-let [timer (get-in @root-cursor [:game :timer])]
          (put! win-chan {:level (get-in @root-cursor [:settings :level]) :time (get-in @root-cursor [:game :time])})
          (put! timer :done))
        (om/transact! root-cursor [:game] (fn [g] (assoc g :won? true))))

      (when (and (= attr :revealed?) value (not= (:old-value tx-data) value))
        (let [space-path (pop (:path tx-data))
              space (get-in @root-cursor space-path)]
          (if (:bomb? space)
            (do
              (when-let [timer (get-in @root-cursor [:game :timer])]
                (put! timer :done))
              (om/transact! root-cursor (fn [r]
                                          (-> r
                                              (update-in [:game :board] mine/reveal-all-bombs)
                                              (assoc-in [:game :lost?] true)
                                              (assoc-in (conj space-path :exploded?) true)))))

            (om/transact! root-cursor [:game :board] (fn [b]
                                                       (mine/reveal-coords b (:coords space))))))))))

(defn main []
  (when-not (seq (get-in @app-state [:game :board]))
    (start-game))
  (om/root app-view app-state
           {:target (.getElementById js/document "content")
            :tx-listen update-board-tx}))

(comment

(start-game)
(get-in @app-state [:settings])
(get-in @app-state [:game :won?])
(swap! app-state assoc-in [:settings] {:bombs 20, :width 36, :height 30})

)
