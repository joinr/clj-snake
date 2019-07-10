(ns snake.core
  (:require [quil.core :as q
              #?@(:cljs  [:include-macros true])]
            [quil.middleware :as qm]
            #?(:cljs  [cljs.reader])))

;;utils
(defn rand-coords
  ([xmin ymin xmax ymax]
   (let [w (- xmax xmin)
         h (- ymax ymin)]
     (repeatedly #(vector (+ xmin (rand-int w))
                          (+ ymin (rand-int h))))))
  ([xmax ymax] (rand-coords 0 0 xmax ymax)))

(defn rand-coord [width height]
  (first (rand-coords width height)))

(defn hit?
  ([x y xmin ymin xmax ymax]
   (and  (<= y ymax) (<= x xmax) (>= y ymin) (>= x xmin)))
  ([x y xmax ymax] (hit? x y xmax ymax xmax ymax)))

(defn some-hit?
  [x y coords]
  (reduce (fn [acc [x2 y2 :as p]]
            (when (hit? x y x2 y2) (reduced p)))
          nil coords))

(defn get-time []
  #?(:clj  (System/currentTimeMillis)
     :cljs (.getTime (js/Date.))))

(defn read-high-score
  [fname]
  #?(:cljs     (cljs.reader/read-string (or (.getItem (.-localStorage js/window) fname) "0"))
     :default  (clojure.edn/read-string (try (slurp fname) (catch Exception e "0")))))

(defn save-high-score
  [fname score]
  #?(:cljs     (.setItem (.-localStorage js/window) fname (str score))
     :default  (clojure.core/spit fname score)))

(defn overwrite-high-score!
  [fname score]
  (let [high-score (read-high-score fname)]
    (when (> score high-score)
      (save-high-score fname score))))

;;state
(defn ->settings [& {:keys [width height high-score-file size]
                     :or {width 500 height 500 init-apples 2
                          high-score-file "score.dat"
                          size 20}}]
  {:game-state  :beginning
   :direction   :left
   :num-apples       0
   :last-move-time   (get-time)
   :high-score-file  high-score-file
   :width            width
   :height           height
   :size           size
   :rows         (/ height size)
   :cols         (/ width  size)
   :fps 60})

(defn ->state [rows cols & {:keys [size] :or {size 20}}]
  (let [w  (* rows size)
        h  (* cols size)
        cx (quot rows 2)
        cy (quot cols 2)
        snek  [[cx cy] [(inc cx) cy]]
        snek? (set snek)]
   (merge (->settings :width (* rows size) :height (* cols size) :size size)
          {:apples (->> (rand-coords rows cols)
                        (filter (complement snek?))
                        (take 1)
                        vec);;xy coords of apples
           :snake  snek ;;xy coords of the snake
           :rows   rows
           :cols   cols})))

(defn check-collision
  [snake apples bounds]
  (let [ [[x y] & snake-body] snake
        [xmax ymax] bounds]
    (cond
      (not (hit? x y 0 0 (dec xmax) (dec ymax)))   :side-collision
      (some-hit? x y snake-body)                   :snake-collision
      (some-hit? x y apples)                       :apple-collision)))

(defn move-head
  [[x y] direction]
  (case direction
    :left  [(dec x) y]
    :right [(inc x) y]
    :up    [x  (inc y)]
    :down  [x  (dec y)]))

(defn move-snake
  [snake direction]
  (let [new-head (move-head (first snake) direction)]
    (when (not= new-head    (second snake)) ;; The snake is not allowed to backtrack.
      (into [new-head]      (butlast snake)))))

(defn expand-snake
  [snake direction]
  (-> (first snake) (move-head direction) vector (into snake)))

(defn setup
  ([{:keys [width height rows cols]}]
   (q/frame-rate    60)
   (q/stroke        0)
   (q/stroke-weight 0)
   (q/background 255 255 255)
   (->state rows cols :size (/ width cols)))
  ([] (setup {:width 500 :height 500})))


(defn new-game [{:keys [rows cols size width height]}]
  (->state  rows  cols :size size :width width :height height))

;;convenience layer for defining multiple keybinds.
(defn expanded-map [m]
  (reduce merge
          (for [[ks common] m]
            (zipmap   ks (repeat common)))))

(def +enter+ (keyword (str \newline)))
(def directions
  (expanded-map {[:ArrowLeft   :left  :a] :left 
                 [:ArrowRight  :right :d] :right
                 [:ArrowDown   :down  :s] :down 
                 [:ArrowUp     :up    :w] :up}))


(def actions (expanded-map {[:Enter :enter   :p +enter+]  :pause}))
(defn action-handler [s]
  (case     (:game-state s)
    (:beginning :paused) (assoc s :game-state :running)
    :running             (assoc s :game-state :paused)
    :over                (new-game s)
    s))

(defn handle-keys [{:keys [game-state] :as state}]
  (let [k   (q/key-as-keyword)
        dir (directions k)
        act (actions k)]
    (cond dir   (assoc state :new-direction dir)
          act   (action-handler state)
          :else state)))
    
(defn update-direction [{:keys [snake direction new-direction] :as state}]
  (let [state (assoc state :new-direction nil)]
    (if-let [snek (and new-direction
                       (move-snake snake new-direction))]
      (assoc state  :snake snek :direction new-direction)
      (assoc state  :snake (move-snake snake direction)))))

(defn remove-apple [{:keys [snake rows cols apples] :as state}]
  (let [xy (first snake)]
    (update state :apples (fn [xs] (vec (keep #(not= % xy) xs))))))

(defn push-apple [{:keys [snake rows cols num-apples] :as state}]
  (let [coords     (set snake)
        new-apple  (->> (rand-coords cols rows)
                        (filter (complement coords))
                        first)]
    (assoc state :apples [new-apple] :num-apples (inc num-apples))))

(defn eat-apple [{:keys [snake direction] :as state}]
  (let [[x y] (first snake)]
    (-> state
        remove-apple
        (assoc :snake (expand-snake snake direction))
        push-apple)))

(defn end-game [{:keys [high-score-file num-apples] :as state}]
  (do (overwrite-high-score! high-score-file num-apples)
      (assoc state :game-state :over)))
      
(defn handle-collision [{:keys [snake apples rows cols] :as state}] 
  (case (check-collision snake apples [rows cols])
    :apple-collision  (let [[x y] (first snake)]
                        (eat-apple state))
    (:snake-collision :side-collision) (end-game state)
    state))

(defn update-time [{:keys [last-move-time delay] :as state
                    :or {delay 60}}]
  (let [t (get-time)]
    (if (> (- t last-move-time) delay)
      (-> state
          (assoc :last-move-time t :moving true))
      state)))

(defn update-game [{:keys [game-state direction] :as state}]
  (let [state (update-time state)]
    (case [game-state (:moving state)]   
      [:running true] (-> state                    
                          update-direction
                          handle-collision
                          (assoc :moving nil))
      state)))

;;rendering 
(def colors {:snake [0 0 0 25] :apples [255 0 0] :board [255 255 255]})

(defn state->tiles [{:keys [rows cols apples snake]}]
  (let [filled? (set (concat apples snake))]
    (for [x (range rows)
          y (range cols)
          :when (not (filled? [x y]))]
      [x y])))

(defn render-rects [color voffset size coords]
  (apply q/fill color)
  (doseq [[x y]  coords]
    (q/rect (* x size) (* (- voffset y 1) size) size size)))

(defn render-board [{:keys [size snake apples rows cols] :as state}]
  (q/stroke 0)
  (q/stroke-weight 1)
  (render-rects (:board  colors) rows size (state->tiles state))
  (render-rects (:snake  colors) rows size snake)
  (render-rects (:apples colors) rows size apples))

(defn show-pause-menu! [{:keys [width height apples high-score-file]}]
  (q/background 173 216 230)
  (q/stroke 0 0 0)
  (q/fill 0 0 0)
  (q/text-size 20)
  (q/text-align :center)
  (q/text "SNAKE" (/ width 2) 50)
  (q/text "MOUSE CLICK OR ENTER: PLAY/PAUSE" (/ width 2) 90)
  (q/text (str "APPLES: " (count apples)) (/ width 2) 120)
  (q/text (str "HIGH SCORE: " (read-high-score high-score-file)) (/ width 2) 150))

(defn show-game-over-screen! [{:keys [width height num-apples high-score-file]}]
  (q/background 0 0 0)
  (q/stroke 255 255 255)
  (q/fill 255 255 255)
  (q/text-size 20)
  (q/text-align :center)
  (q/text "GAME OVER" (/ width 2) 50)
  (q/text (str "APPLES: " num-apples) (/ width 2) 90)
  (q/text (str "HIGH SCORE: " (read-high-score high-score-file)) (/ width 2) 150)
  (q/text "MOUSE CLICK OR ENTER: PLAY AGAIN" (/ width 2) 180))

(defn show-game! [{:keys [game-state num-apples high-score-file] :as state}]
  (case game-state
    :over                    (show-game-over-screen! state)                        
    (:beginning :paused)     (show-pause-menu! state)
    (render-board state)))

(def +defaults+ {:rows 20 :cols 20 :width 500 :height 500 :exit? false :host "snake"})

(defn play [& {:keys [rows cols width height exit? host] :as settings}]
  (let [config (merge +defaults+ settings)
        {:keys [rows cols width height exit?]} config]
    (q/defsketch snake
      :title "Snake"
      :host (:host config)
      :size [width height]
      :settings #(q/smooth 2)
      :renderer #?(:clj  :java2d
                   :clj  :p2d) ;:opengl
      :setup         #(setup config)
      :draw          show-game!
      :update        update-game
      :key-pressed   (fn [s e] (handle-keys s))
      :mouse-clicked (fn [s _] (action-handler s))                             
      :features      (when  exit? [:exit-on-close])
      :middleware    [qm/fun-mode])))
