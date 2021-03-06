(ns me.lomin.piggybank.interpreter)

(def inc-or-0 (fnil inc 0))

(defn inc-check-count [state progress-bar]
  (when progress-bar (progress-bar))
  (update state :check-count inc-or-0))

(defn add-previous-state [state previous-state]
  (update state :history (fnil conj (list)) (dissoc previous-state :history)))

(defn add-property-violation [state violation timeline]
  (assoc state :property-violated {:name violation :timeline timeline}))

(defn check-properties [universe {:keys [timeline any-property-violation progress-bar] :as context}]
  (if-let [violation (any-property-violation (assoc context :universe universe))]
    (reduced (add-property-violation universe violation timeline))
    (inc-check-count universe progress-bar)))

(defn interpret-timeline [{:keys [interpret-event universe timeline progress-bar] :as context}]
  (reduce (fn [universe* event]
            (let [successor-universe (interpret-event universe* event)]
              (if (:invalid-timeline successor-universe)
                (inc-check-count universe* progress-bar)
                (-> successor-universe
                    (add-previous-state universe*)
                    (check-properties (assoc context :event event))))))
          universe
          timeline))
