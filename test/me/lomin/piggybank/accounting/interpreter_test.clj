(ns me.lomin.piggybank.accounting.interpreter-test
  (:require [clojure.test :refer :all]
            [me.lomin.piggybank.accounting.interpreter.core :as intp]
            [me.lomin.piggybank.accounting.interpreter.spec :as spec]
            [me.lomin.piggybank.asserts :refer [=*]]))

(def test-state spec/example-universe)

(deftest ^:unit interpret-event-test
  (testing "unknown events are ignored"
    (is (= test-state
           (intp/interpret-event test-state [:unknown]))))

  (testing "last document is saved in universe under process-id"
    (is (=* {7 {:last-document {:next      {:cash-up-id 1, :document-id 1}
                                :self      {:cash-up-id 1, :document-id 1}
                                :transfers [[#{:some-id-1} -1]]}}}
            (intp/interpret-event test-state [:accounting-read {:process-id 7}]))))

  (testing "update is appended to last document"
    (is (=* {:accounting {[:cash-up 1] {[:document 1] {:next      {:cash-up-id 1, :document-id 1}
                                                       :self      {:cash-up-id 1, :document-id 1}
                                                       :transfers [[#{:some-id-1} -1]
                                                                   [#{7} 5]]}}}}
            (-> test-state
                (intp/interpret-event [:accounting-read {:process-id 7}])
                (intp/interpret-event [:accounting-write {:process-id 7 :amount 5}])))))

  (testing "state is negative, hence no updates to db"
    (is (=* test-state
            (-> test-state
                (intp/interpret-event [:process {:process-id 7 :amount -10}])
                (intp/interpret-event [:accounting-read {:process-id 7}])
                (intp/interpret-event [:accounting-write {:process-id 7 :amount -10}]))))))

(deftest ^:unit interpret-timeline-test
  (let [test-timeline [[:stuttering]
                       [:process {:process-id 6, :amount 1}]
                       [:process {:process-id 59, :amount -1}]
                       [:accounting-read {:process-id 59, :amount -1}]
                       [:process {:process-id 835, :amount 0}]
                       [:accounting-read {:process-id 835, :amount 0}]
                       [:accounting-write {:process-id 835, :amount 0}]]]
    (is (=* {59           {:last-document {:next      {:cash-up-id 1, :document-id 1}
                                           :self      {:cash-up-id 1, :document-id 1}
                                           :transfers [[#{:some-id-1} -1]]}}
             835          {:last-document {:next      {:cash-up-id 1, :document-id 1}
                                           :self      {:cash-up-id 1, :document-id 1}
                                           :transfers [[#{:some-id-1} -1] [#{835} 0]]}}
             :accounting  {[:cash-up 1] {[:document 0] {:next      {:cash-up-id 1, :document-id 1}
                                                        :self      {:cash-up-id 1, :document-id 0}
                                                        :transfers [[#{:some-id-0} 2]]}
                                         [:document 1] {:next      {:cash-up-id 1, :document-id 1}
                                                        :self      {:cash-up-id 1, :document-id 1}
                                                        :transfers [[#{:some-id-1} -1]
                                                                    [#{835} 0]]}}}
             :check-count 7}
            (intp/interpret-timeline test-state
                                     test-timeline)))))

(deftest ^:unit interpret-timeline-with-property-violations-test
  (let [test-timeline [[:stuttering]
                       [:process {:process-id 0, :amount -1}]
                       [:process {:process-id 1, :amount -1}]
                       [:accounting-read {:process-id 0, :amount -1}]
                       [:accounting-write {:process-id 0, :amount -1}]
                       [:accounting-read {:process-id 1, :amount -1}]
                       [:accounting-write {:process-id 1, :amount -1}]
                       [:balance-write {:process-id 0, :amount -1}]
                       [:balance-write {:process-id 1, :amount -1}]]]
    (is (= {:property-violated {:name     :accounting-balance-must-always-be>=0
                                :timeline [[:stuttering]
                                           [:process {:process-id 0, :amount -1}]
                                           [:process {:process-id 1, :amount -1}]
                                           [:accounting-read {:process-id 0, :amount -1}]
                                           [:accounting-write {:process-id 0, :amount -1}]
                                           [:accounting-read {:process-id 1, :amount -1}]
                                           [:accounting-write {:process-id 1, :amount -1}]
                                           [:balance-write {:process-id 0, :amount -1}]
                                           [:balance-write {:process-id 1, :amount -1}]]}}
           (-> test-state
               (intp/interpret-timeline test-timeline)
               (select-keys [:property-violated]))))))

(deftest ^:unit interpret-pagination-test
  (testing "document 7 gets created but not linked"
    (is (=* {:accounting {[:cash-up 1] {[:document 1] {:next {:cash-up-id 1, :document-id 1}}
                                        [:document 8] {:next      {:cash-up-id 1, :document-id 8}
                                                       :self      {:cash-up-id 1, :document-id 8}
                                                       :transfers []}}}}
            (intp/interpret-timeline test-state [[:process {:process-id 7, :amount 1}]
                                                 [:accounting-read {:process-id 7, :amount 1}]
                                                 [:accounting-add-new-document {:process-id 7, :amount 1}]]))))

  (testing "document 1 now links to document 7"
    (is (=* {:accounting {[:cash-up 1] {[:document 1] {:next {:cash-up-id 1, :document-id 8}}
                                        [:document 8] {:next      {:cash-up-id 1, :document-id 8}
                                                       :self      {:cash-up-id 1, :document-id 8}
                                                       :transfers []}}}}
            (intp/interpret-timeline test-state [[:process {:process-id 7, :amount 1}]
                                                 [:accounting-read {:process-id 7, :amount 1}]
                                                 [:accounting-add-new-document {:process-id 7, :amount 1}]
                                                 [:accounting-link-to-new-document {:process-id 7, :amount 1}]])))))

(defn interpret-timeline [state timeline]
  (dissoc (intp/interpret-timeline state timeline)
          :history
          :check-count))

(deftest ^:unit restarting-a-system-without-strong-consistency-guarantees-test
  (testing "restarting a system with an inmemory-db and an eventually consistent database"
    (is (=* test-state
            (interpret-timeline test-state [[:restart {:past 0}]])))
    (is (= (interpret-timeline test-state [[:accounting-read {:process-id 7 :amount 5}]
                                           [:accounting-write {:process-id 7 :amount 5}]
                                           [:balance-write {:process-id 7 :amount 5}]])
           (interpret-timeline test-state [[:accounting-read {:process-id 7 :amount 5}]
                                           [:accounting-write {:process-id 7 :amount 5}]
                                           [:balance-write {:process-id 7 :amount 5}]
                                           [:restart {:past 0}]])))

    (is (= (interpret-timeline test-state [[:accounting-read {:process-id 7 :amount 5}]
                                           [:accounting-write {:process-id 7 :amount 5}]
                                           [:balance-write {:process-id 7 :amount 5}]])
           (interpret-timeline test-state [[:accounting-read {:process-id 7 :amount 5}]
                                           [:accounting-write {:process-id 7 :amount 5}]
                                           [:balance-write {:process-id 7 :amount 5}]
                                           [:restart {:past 1}]])))

    (is (= (interpret-timeline test-state [[:accounting-read {:process-id 7 :amount 5}]
                                           [:accounting-write {:process-id 7 :amount 5}]])
           (interpret-timeline test-state [[:accounting-read {:process-id 7 :amount 5}]
                                           [:accounting-write {:process-id 7 :amount 5}]
                                           [:balance-write {:process-id 7 :amount 5}]
                                           [:restart {:past 2}]])
           (interpret-timeline test-state [[:accounting-read {:process-id 7 :amount 5}]
                                           [:accounting-write {:process-id 7 :amount 5}]
                                           [:balance-write {:process-id 7 :amount 5}]
                                           [:restart {:past 3}]])))))