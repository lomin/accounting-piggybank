(ns me.lomin.piggybank.accounting.doc
  (:require [me.lomin.piggybank.accounting.interpreter.core :as intp]
            [me.lomin.piggybank.accounting.interpreter.spec :as spec]
            [me.lomin.piggybank.accounting.model :as model]
            [me.lomin.piggybank.checker :as checker]
            [me.lomin.piggybank.doc :refer [print-check print-data print-source]]
            [me.lomin.piggybank.model :refer [all
                                              always
                                              choose
                                              continue
                                              generate-incoming
                                              make-model
                                              multi-threaded
                                              then]]
            [me.lomin.piggybank.timeline :as timeline]))

(defn check
  ([model length]
   (check model length nil))
  ([model length keys]
   (checker/check {:model       model
                   :length      length
                   :keys        keys
                   :interpreter intp/interpret-timeline
                   :universe    spec/empty-universe
                   :partitions  5})))

(def check* (memoize check))

(defn example-accounting-events []
  [:process {:amount 1, :process-id 0} :doc "Eine Anfrage mit der Prozess-Id 0 zur Einzahlung von 1 Euro wurde gestartet."]
  [:process {:amount -1, :process-id 1} :doc "Eine Anfrage mit der Prozess-Id 1 zur Abhebung von 1 Euro wurde gestartet."]
  [:accounting-read {:amount 1, :process-id 0} :doc "Die notwendigen Daten aus dem Buchhaltungssystem wurden abgefragt. Das Ergebnis der Abfrage wurde abgelegt. Ausgelöst wurde dieses Event durch den Prozess mit der Prozess-Id 0."]
  [:accounting-write {:amount 1, :process-id 0} :doc "Die Transaktion wurde im Buchhaltungssystem festgehalten."]
  [:balance-write {:amount 1, :process-id 0} :doc "Das Saldo des Sparschweins wurde aktualisiert."])

(defn print-accounting-events []
  (print-source me.lomin.piggybank.accounting.doc/example-accounting-events))

(def accounting-subsystem* {:accounting {:transfers {0 1, 1 -1}}})
(def balance-subsystem* {:balance {:amount 0}})
(def universe-0* (merge accounting-subsystem* balance-subsystem*))
(def event-0* [:accounting-write {:amount 1, :process-id 5}])

(defn accounting-subsystem []
  (print-data accounting-subsystem*))
(defn balance-subsystem []
  (print-data balance-subsystem*))
(defn event-0 []
  (print-data event-0*))
(defn universe-0 []
  (print-data universe-0*))

(defn universe-after-update []
  (print-data
   (let [[_ {:keys [amount process-id]}] event-0*]
     (-> universe-0*
         (update-in [:accounting :transfers] #(assoc % process-id amount))
         (update-in [:balance :amount] #(+ % amount))))))

(defn example-timeline-0 []
  (print-data (first (seq (timeline/all-timelines-of-length 3 model/example-model-0)))))

(defn example-timeline-1 []
  (print-data (nth (seq (timeline/all-timelines-of-length 4 model/example-model-1))
                   14)))

(defn example-timeline-2 []
  (print-data (nth (seq (timeline/all-timelines-of-length 4 model/example-model-1))
                   9)))

(defn accounting-db-down-timeline []
  (print-data (nth (seq (timeline/all-timelines-of-length 3 model/multi-threaded-simple-model))
                   23)))

(defn multi-threaded-simple-model []
  (print-source me.lomin.piggybank.accounting.model/multi-threaded-simple-model))

(defn single-threaded-simple-model []
  (print-source me.lomin.piggybank.accounting.model/single-threaded-simple-model))

(defn reduced-multi-threaded-simple-model []
  (print-source me.lomin.piggybank.accounting.model/reduced-multi-threaded-simple-model))

(defn reduced-single-threaded-simple-model []
  (print-source me.lomin.piggybank.accounting.model/reduced-single-threaded-simple-model))

(defn timelines-reduced-multi-threaded-simple-model* [length]
  (timeline/all-timelines-of-length length
                                    model/reduced-multi-threaded-simple-model))

(defn timelines-reduced-multi-threaded-simple-model [length]
  (print-data (timelines-reduced-multi-threaded-simple-model* length)))

(defn count-timelines-reduced-multi-threaded-simple-model [length]
  (count (timelines-reduced-multi-threaded-simple-model* length)))

(defn timelines-reduced-single-threaded-simple-model* [length]
  (timeline/all-timelines-of-length 3
                                    model/reduced-single-threaded-simple-model))

(defn timelines-reduced-single-threaded-simple-model [length]
  (print-data (timelines-reduced-single-threaded-simple-model* length)))

(defn count-timelines-reduced-single-threaded-simple-model [length]
  (count (timelines-reduced-single-threaded-simple-model* length)))

(defn valid-sample-timeline-reduced-multi-threaded-simple-model [length]
  (print-data (last (seq (timelines-reduced-multi-threaded-simple-model* length)))))

(defn invalid-sample-timeline-reduced-multi-threaded-simple-model []
  [[:process {:amount 1, :process-id 0}]
   [:process {:amount 1, :process-id 1}]
   [:accounting-read {:amount 1, :process-id 0}]
   [:accounting-read {:amount 1, :process-id 0}]])

(defn print-invalid-sample-timeline-reduced-multi-threaded-simple-model []
  (print-source me.lomin.piggybank.accounting.doc/invalid-sample-timeline-reduced-multi-threaded-simple-model))

(def simple-model-length 7)

(defn check-multi-threaded-simple-model []
  (print-check #(-> (check* model/multi-threaded-simple-model
                            simple-model-length
                            [:check-count :max-check-count :property-violated :timeline :accounting :balance])
                    (assoc :accounting {:transfers {1 1}}))))

(defn check-single-threaded-simple-model []
  (print-check #(check* model/single-threaded-simple-model
                        simple-model-length
                        [:check-count :max-check-count :property-violated])))

(defn number-of-possible-timelines []
  (/ (:max-check-count (check* model/multi-threaded-simple-model simple-model-length nil))
     simple-model-length))

(defn number-of-checked-time-slots []
  (:check-count (check* model/multi-threaded-simple-model simple-model-length nil)))

(defn lost-updates? []
  (print-source me.lomin.piggybank.accounting.interpreter.properties/lost-updates?))

(defn single-threaded+inmemory-balance+eventually-consistent-accounting-model []
  (print-source me.lomin.piggybank.accounting.model/single-threaded+inmemory-balance+eventually-consistent-accounting-model))

(defn check-single-threaded+inmemory-balance+eventually-consistent-accounting-model []
  (print-check #(check* model/single-threaded+inmemory-balance+eventually-consistent-accounting-model
                        12
                        [:check-count :max-check-count :property-violated :timeline])))