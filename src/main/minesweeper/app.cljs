(ns minesweeper.app
  (:require
   [minesweeper.engine :as mine]
   [re-frame.core :as rf]
   [reagent.dom :as dom]
   [reagent.core :as reagent]))

(defn board-button-classes [{:space/keys [mine? revealed? flagged? exploded? mine-count]}]
  (str (cond
         (and revealed? mine? exploded?) "exploded depressed bomb"
         (and revealed? flagged? (not mine?)) "wrong depressed bomb"
         (and revealed? mine?) "depressed bomb"
         revealed? "depressed"
         flagged? "flagged"
         :else "normal")
       " space-" mine-count
   " space"))

(defn board [state]
  [:div.board
   (map-indexed
    (fn [i row]
      ^{:key i}
      [:div.row
       (map-indexed
        (fn [j {:space/keys [revealed? flagged? text] :as space}]
          (let [path [i j]]
            [:div.space {:key path
                         :class (board-button-classes space)
                         :on-click #(when-not revealed?
                                      (rf/dispatch [:reveal-space path]))}
             [:span (if revealed? text "")]]))
        row)])
    state)])

(defn player [state]
  (let [{:player/keys [health max-health dexterity strength]} @state]
    [:table.player
     [:tbody
      [:tr
       [:td.attribute "❤"]
       [:td.amount (str health "/" max-health)]]
      [:tr
       [:td.attribute "🏃"]
       [:td.amount dexterity]]
      [:tr
       [:td.attribute "⚔"]
       [:td.amount strength]]]]))

(defmulti show-turn :turn/actor)

(defmethod show-turn :player [{:turn/keys [attack block]}]
  [:div.turn.player-turn
   [:p "Your turn"]
   [:table
    [:tbody
     [:tr
      [:td attack]
      [:td "clicks left"]]
     [:tr
      [:td block]
      [:td "block"]]]]])

(defn turn [state]
  [show-turn @state])

(defmulti inner-card :card/type)

(defmethod inner-card :default [c]
  (pr-str c))

(defmethod inner-card :attack [c]
  [:div.innercard.attack "Attack"])

(defmethod inner-card :defend [c]
  [:div.innercard.defend "Defend"])

(defmethod inner-card :reveal [{:card/keys [amount]}]
  [:div.innercard.reveal (str "Reveal " amount " random spaces")])

(defn card-styles [total i]
  (let [factor (- i (/ (dec total) 2.0))
        deg (* factor 5)
        x (* factor 78)
        s (str "rotate(" deg "deg) translate(" x "px, 0)")]
    {:transform s}))


(defn cards [hand]
  (let [total (count hand)]
    [:div.cards
     (map (fn [i c]
            [:div.card {:key i
                        :on-click #(rf/dispatch [:play-card c])
                        :style (card-styles total i)}
             [inner-card c]])
          (range)
          hand)]))

(defn stack [c]
  [:div.card.stack (count c)])

(defn deck [state]
  (let [{:deck/keys [draw discard hand full]} @state]
    [:div.deck
     [:div.draw
      [stack draw]]
     [:div.hand
      [cards hand]]
     [:div.discard
      [stack discard]]]))

(rf/reg-event-db
 :reveal-space
 (fn [game [_ path]]
   (mine/reveal-space game path)))

(rf/reg-event-db
 :play-card
 (fn [game [_ card]]
   (js/console.log card)
   (mine/play-card game card)))

(rf/reg-event-db
 :initialize
 (fn [game _]
   (.log js/console "Initializing new game...")
   (mine/make-game)))

(rf/reg-sub
 ::board
 (fn [db _]
   (get-in db [:minesweeper/board])))

(rf/reg-sub
 ::deck
 (fn [db _]
   (get-in db [:minesweeper/deck])))

(rf/reg-sub
 ::player
 (fn [db _]
   (get-in db [:minesweeper/player])))

(rf/reg-sub
 ::turn
 (fn [db _]
   (get-in db [:minesweeper/turn])))

(defn root-view
  "Render the page"
  []
  (let [board-state (rf/subscribe [::board])
        deck-state (rf/subscribe [::deck])
        player-state (rf/subscribe [::player])
        turn-state (rf/subscribe [::turn])]
    (fn []
      [:div
       [board @board-state]
       [player player-state]
       [turn turn-state]
       [deck deck-state]])))

(defn ^:export render []
  (dom/render [root-view]
              (js/document.getElementById "content")))

(defn ^:export main []
  (rf/dispatch-sync [:initialize])

  (render))
