(ns minesweeper.app
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [day8.re-frame.undo :as undo :refer [undoable]]
            [minesweeper.engine :as mine]
            [minesweeper.score :as score])
  (:import [goog.string format]))

(defonce default-level :beginner)

(defn choose-setting [settings level]
  (let [settings (assoc settings :level level)]
    (if-let [opts (mine/levels level)]
      (merge settings (zipmap [:width :height :bombs] opts))
      settings)))

(defn new-timer []
  (let [now (js/Date.)]
    {:current now
     :started now}))

(defn null-game []
  {:won false
   :lost false
   :timer (new-timer)
   :grid []})

(defn new-game [{:keys [width height bombs] :as settings}]
  (assoc (null-game)
         :grid (mine/generate-board width height bombs)))

(defn reset-game [{:keys [settings] :as db}]
  (assoc db :game (new-game settings)))

(defn high-scores []
  (let [db (score/load-db)
        format-entry (fn [[k v]] (format "%-12s\t%-4d\t%s" (name k) (:time v) (:name v)))]
    (apply str (interpose "\n\n" (map format-entry db)))))

(def seconds-difference (comp #(/ % 1000) -))

(defn get-name []
  (js/prompt "Congratulations!\n\nYou have achieved a new high score. What is your name?"))

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

(defn board [grid]
  [:div#board
   (map-indexed
    (fn [i row]
      [:div.row {:key i}
       (map
        (fn [space]
          [:div.space {:key (:path space)
                       :class (button-classes space)
                       :on-context-menu (fn [e]
                                        (.preventDefault e)
                                        (when-not (:revealed? space)
                                          (rf/dispatch [:space/flag! (:path space)])))
                       :on-click (fn [e]
                                  (.preventDefault e)
                                  (when-not (:flagged? space)
                                    (rf/dispatch [:space/reveal! (:path space)])))}
           [:span (button-label space)]])
        row)])
    grid)])

(defn settings []
  (let [settings-sub (rf/subscribe [:settings])]
    (fn []
      (let [{:keys [level] :as settings} @settings-sub]
        [:form#settings
         [:select {:value (name level)
                   :on-change (fn [e]
                                (let [level (keyword (.. e -target -value))]
                                  (rf/dispatch [:settings/set-level! level])))}
          [:option {:value "beginner"} "Beginner"]
          [:option {:value "intermediate"} "Intermediate"]
          [:option {:value "expert"} "Expert"]
          [:option {:value "custom"} "Custom"]]
         (when (= :custom level)
           [:dl
            (mapcat (fn [attr i]
                      [[:dt {:key (str "key-" i)}
                        [:label {:htmlFor (name attr)} (name attr)]]
                       [:dd {:key (str "val-" i)}
                        [:input {:on-change (fn [e]
                                              (rf/dispatch [:settings/set-attr! attr (int (.. e -target -value))]))
                                 :id (name attr)
                                 :name (name attr)
                                 :value (get settings attr)}]]])
                    [:width :height :bombs] (range))])]))))


(defn score [{:keys [grid timer lost won] :as game}]
  [:div#score
   [:span.remaining
    (format "%03d" (mine/remaining-count grid))]
   [:span.moves
    (format "%03d" (seconds-difference (:current timer) (:started timer)))]
   [:span {:class (str (cond
                         lost "lost"
                         won  "won"
                         :else "normal") " face")
           :on-click (fn [e]
                       (rf/dispatch [:game/start!]))}]])

(defn app-view []
  (let [game-sub (rf/subscribe [:game])
        show-high-scores (r/atom false)]
    (fn []
      (let [props @game-sub]
        [:div
         [:h1 "Minesweeper"]
         [settings]
         [:div#app
          [score props]
          [board (:grid props)]]
         [:p
          [:a {:href "#"
               :on-click (fn [e]
                           (.preventDefault e)
                           (swap! show-high-scores not))}
           "High Scores"]]
         (when @show-high-scores
           [:p
            (high-scores)])]))))

(rf/reg-sub
 :game
 (fn [db]
   (:game db)))

(rf/reg-sub
 :settings
 (fn [db]
   (:settings db)))

(defonce live-intervals (atom {}))

(defn clear-intervals [ids]
  (let [ids (or (seq ids) (keys @live-intervals))]
    (doseq [id ids]
      (js/clearInterval (get @live-intervals id))
      (swap! live-intervals dissoc id))))

(defn add-interval [{:keys [id event frequency]}]
  (when (get @live-intervals id)
    (clear-intervals [id]))
  (swap! live-intervals assoc id (js/setInterval #(rf/dispatch event) frequency)))

(rf/reg-fx
 :interval
 (fn [{:keys [action] :as params}]
   (case action
     :start (add-interval params)
     :cancel (clear-intervals [(:id params)]))))

(rf/reg-event-db
 :game/tick
 [(rf/path [:game :timer :current])]
 (fn [_ _]
   (js/Date.)))

(def check-for-win
  (re-frame.core/->interceptor
   :id     :check-for-win
   :after  (fn [context]
             (let [game (get-in context [:effects :db :game])
                   won (mine/won? (:grid game))
                   lost (:lost game)]
               (cond-> context
                 won (update-in
                      [:effects :db :game]
                      (fn [game]
                        (-> game
                            (assoc :won true)
                            (update :grid mine/mark-matching :bomb? :flagged?)
                            (update :grid mine/mark-matching #(not (:bomb? %)) :revealed?))))
                 lost (update-in
                       [:effects :db :game :grid]
                       mine/mark-matching #(or (:bomb? %) (:flagged? %)) :revealed?)
                 (or won lost) (update :effects
                                       assoc :interval {:action :cancel :id :timer}))))))

(rf/reg-event-db
 :space/reveal!
 [check-for-win]
 (fn [{:keys [game] :as db} [_ path]]
   (if (or (:lost game) (:won game))
     db
     (let [[row col] path
           {:keys [width height bombs]} (:settings db)
           ;; Ensure the first played space is not a bomb
           game (if (zero? (mine/revealed-count (:grid game)))
                  (assoc game :grid (mine/generate-board width height bombs path))
                  game)]
       (let [space (get-in game [:grid row col])]
         (if (:bomb? space)
           (assoc db :game
                  (-> game
                      (assoc :lost true)
                      (assoc-in [:grid row col :exploded?] true)))
           (update-in db [:game :grid] mine/reveal-coords path)))))))

(rf/reg-event-db
 :space/flag!
 [check-for-win (rf/path [:game])]
 (fn [game [_ path]]
   (if (or (:lost game) (:won game))
     game
     (update game :grid
             update-in
             (conj path :flagged?) not))))

(rf/reg-event-fx
 :game/start!
 (fn [{:keys [db]} _]
   {:db (reset-game db)
    :interval {:action :start
               :id :timer
               :frequency 500
               :event [:game/tick]}}))

(rf/reg-event-db
 :initialize
 (fn [db _]
   (assoc db :settings (choose-setting {} default-level))))

(rf/reg-event-db
 :settings/set-level!
 (fn [db [_ level]]
   (update db :settings choose-setting level)))

(rf/reg-event-db
 :settings/set-attr!
 (fn [db [_ attr value]]
   (assoc-in db [:settings attr] value)))

(defn render
  []
  (rd/render [app-view]
             (js/document.getElementById "content")))

(defn ^:dev/after-load clear-cache-and-render!
  []
  ;; (clear-intervals nil)
  (rf/clear-subscription-cache!)
  (render))

(defn ^:export main []
  (rf/dispatch-sync [:initialize])
  (rf/dispatch-sync [:game/start!])
  (day8.re-frame.undo/undo-config! {:max-undos 100})
  (render)
  (enable-console-print!))
