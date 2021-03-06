(ns me.lomin.piggybank.accounting.model-test
  (:require [clojure.test :refer :all]
            [me.lomin.piggybank.accounting.doc :refer [check]]
            [me.lomin.piggybank.accounting.model :as model]
            [me.lomin.piggybank.asserts :refer [=*]]
            [me.lomin.piggybank.model :refer [all
                                              always
                                              choose
                                              continue
                                              generate-incoming
                                              make-model
                                              multi-threaded
                                              then]]
            [me.lomin.piggybank.timeline :as timeline]))

(deftest ^:unit model-unit-test
  (is (= #{[[:process {:amount -1, :process-id 0}]
            [:accounting-read {:amount -1, :process-id 0}]]
           [[:process {:amount -1, :process-id 0}]
            [:process {:amount -1, :process-id 1}]]
           [[:process {:amount -1, :process-id 0}] [:process {:amount 1, :process-id 1}]]
           [[:process {:amount 1, :process-id 0}]
            [:accounting-read {:amount 1, :process-id 0}]]
           [[:process {:amount 1, :process-id 0}] [:process {:amount -1, :process-id 1}]]
           [[:process {:amount 1, :process-id 0}] [:process {:amount 1, :process-id 1}]]}
         (timeline/all-timelines-of-length 2 model/multi-threaded-simple-model)))

  (is (= #{[[:process {:amount -1, :process-id 0}]
            [:accounting-read {:amount -1, :process-id 0}]]
           [[:process {:amount 1, :process-id 0}]
            [:accounting-read {:amount 1, :process-id 0}]]}
         (timeline/all-timelines-of-length 2 model/single-threaded-simple-model))))

(deftest ^:unit choose-test
  (let [choose-model (partial make-model
                              {::model/always   (all (generate-incoming multi-threaded
                                                                        [:process {:amount 1}]
                                                                        [:process {:amount -1}])
                                                     (always [:stuttering]))
                               :process         (choose (then :accounting-read)
                                                        (then :balance-write))
                               :accounting-read (continue)
                               :balance-write   (continue)})
        choose-timelines (timeline/all-timelines-of-length 3
                                                           choose-model)]
    (is (= nil
           (seq (filter (fn [[_ a b]]
                          (and (= a :accounting-read)
                               (= b :balance-write)))
                        choose-timelines))))

    (is (= nil
           (seq (filter (fn [[_ a b]]
                          (and (= a :balance-write)
                               (= b :accounting-read)))
                        choose-timelines))))))

(deftest ^:model multi-threaded-model-test
  (is (= {:accounting {[:cash-up 0] {[:document 0] {:next {:cash-up-id 0, :document-id 0}
                                                    :self {:cash-up-id 0, :document-id 0}
                                                    :transfers [[#{1} 1]]}}
                       [:cash-up :meta] {[:document :meta] {[:cash-up 0] {:cash-up-id 0
                                                                          :document-id 0}
                                                            [:cash-up :start] {:cash-up-id 0
                                                                               :document-id 0}}}}
          :balance {:amount 1, :processes #{0}}
          :check-count 20481
          :max-check-count 97524
          :property-violated {:name :there-must-be-no-lost-updates
                              :timeline [[:process {:amount 1, :process-id 0}]
                                         [:process {:amount 1, :process-id 1}]
                                         [:accounting-read {:amount 1, :process-id 0}]
                                         [:accounting-read {:amount 1, :process-id 1}]
                                         [:accounting-write {:amount 1, :process-id 0}]
                                         [:balance-write {:amount 1, :process-id 0}]
                                         [:accounting-write {:amount 1, :process-id 1}]]}}
         (check model/multi-threaded-simple-model 7 [:check-count :property-violated :accounting :max-check-count :balance]))))

(deftest ^:model single-threaded-model-test
  (is (= {:check-count 80 :max-check-count 80}
         (check model/single-threaded-simple-model 10 [:check-count :max-check-count :property-violated]))))

(deftest ^:model single-threaded+pagination-model-test
  (is (=* {:accounting        {[:cash-up 0]     {[:document 0] {:next      {:cash-up-id 0, :document-id 1}
                                                                :self      {:cash-up-id 0, :document-id 0}
                                                                :transfers [[#{0} 1]]}}
                               [:cash-up :meta] {[:document :meta] {[:cash-up 0]      {:cash-up-id  0
                                                                                       :document-id 0}
                                                                    [:cash-up :start] {:cash-up-id  0
                                                                                       :document-id 0}}}}
           :check-count       38
           :property-violated {:name     :all-links-must-point-to-an-existing-document
                               :timeline [[:stuttering]
                                          [:process {:amount 1, :process-id 0}]
                                          [:stuttering]
                                          [:stuttering]
                                          [:accounting-read {:amount 1, :process-id 0}]
                                          [:accounting-write {:amount 1, :process-id 0}]
                                          [:accounting-link-to-new-document
                                           {:amount 1, :process-id 0}]
                                          [:balance-write {:amount 1, :process-id 0}]]}}
          (check model/single-threaded+pagination-model 8 [:check-count :property-violated :accounting]))))

(deftest ^:model single-threaded+safe-pagination-model-test
  (is (= {:check-count 52374}
         (check model/single-threaded+safe-pagination-model 6 [:check-count :property-violated]))))

(deftest ^:model single-threaded+safe-pagination+gc-model-test
  (testing "proves that the garbage collection algorithm is flawed"
    (is (= {:accounting {[:cash-up 0] {[:document 0] {:next {:cash-up-id 0, :document-id 0}
                                                      :self {:cash-up-id 0, :document-id 0}
                                                      :transfers [[#{0} 1] [#{1} -1]]}
                                       [:document "0-init"] {:next {:cash-up-id 0
                                                                    :document-id "0-init"}
                                                             :self {:cash-up-id 0
                                                                    :document-id "0-init"}
                                                             :transfers [[#{0} 1]]}}
                         [:cash-up :meta] {[:document :meta] {[:cash-up 0] {:cash-up-id 0
                                                                            :document-id "0-init"}
                                                              [:cash-up :start] {:cash-up-id 0
                                                                                 :document-id "0-init"}}}}
            :check-count 15509
            :property-violated {:name :there-must-be-no-lost-updates
                                :timeline [[:process {:amount 1, :process-id 0}]
                                           [:accounting-read {:amount 1, :process-id 0}]
                                           [:accounting-write {:amount 1, :process-id 0}]
                                           [:balance-write {:amount 1, :process-id 0}]
                                           [:accounting-gc-new-branch
                                            {:amount 1, :process-id 0}]
                                           [:process {:amount -1, :process-id 1}]
                                           [:accounting-read {:amount -1, :process-id 1}]
                                           [:accounting-gc-link-to-new-branch
                                            {:amount 1, :process-id 0}]
                                           [:accounting-write {:amount -1, :process-id 1}]
                                           [:balance-write {:amount -1, :process-id 1}]]}}
           (check model/model+safe-pagination+gc-strict 10 [:check-count :property-violated :accounting])))))

(deftest ^:model single-threaded-inmemory-db-model-test
  (is (= {:check-count 27131
          :max-check-count 81120
          :property-violated {:name :accounting-balance-must-always-be>=0
                              :timeline [[:process {:amount 1, :process-id 0}]
                                         [:accounting-read {:amount 1, :process-id 0}]
                                         [:accounting-write {:amount 1, :process-id 0}]
                                         [:balance-write {:amount 1, :process-id 0}]
                                         [:process {:amount -1, :process-id 1}]
                                         [:accounting-read {:amount -1, :process-id 1}]
                                         [:accounting-write {:amount -1, :process-id 1}]
                                         [:balance-write {:amount -1, :process-id 1}]
                                         [:restart {:past 3}]
                                         [:process {:amount -1, :process-id 2}]
                                         [:accounting-read {:amount -1, :process-id 2}]
                                         [:accounting-write {:amount -1, :process-id 2}]]}}
         (check model/single-threaded+inmemory-balance+eventually-consistent-accounting-model 12 [:check-count :property-violated :max-check-count]))))