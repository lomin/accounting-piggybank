(ns me.lomin.piggybank.doc-test
  (:require [clojure.test :refer :all]
            [com.rpl.specter :as s]
            [me.lomin.piggybank.accounting.doc :as doc]
            [me.lomin.piggybank.accounting.document-db.core :as db]
            [me.lomin.piggybank.accounting.interpreter.core :as intp]
            [me.lomin.piggybank.accounting.interpreter.spec :as spec]
            [me.lomin.piggybank.accounting.model :as model]
            [me.lomin.piggybank.checker :as checker]
            [ubergraph.alg :as ualg]
            [ubergraph.core :as ugraph]))

(def example-state
  {0                  {:last-document {:test "0"}}
   1                  {:last-document {:test "1"}}
   :accounting        {[:cash-up 0]     {[:document 0] {:next      {:cash-up-id 0, :document-id 1}
                                                        :self      {:cash-up-id 0, :document-id 0}
                                                        :transfers [[#{0} 1]
                                                                    [#{1} -1]]}}
                       [:cash-up :meta] {[:document :meta] {[:cash-up 0]      {:cash-up-id  0
                                                                               :document-id 0}
                                                            [:cash-up :start] {:cash-up-id  0
                                                                               :document-id 0}}}}
   :timeline          [[:stuttering]
                       [:process {:amount 1, :process-id 0}]
                       [:stuttering]
                       [:stuttering]
                       [:accounting-read {:amount 1, :process-id 0}]
                       [:accounting-write {:amount 1, :process-id 1}]
                       [:accounting-write {:amount 1, :process-id 0}]
                       [:accounting-link-to-new-document
                        {:amount 1, :process-id 0}]
                       [:balance-write {:amount 1, :process-id 0}]]
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
                                  [:balance-write {:amount 1, :process-id 0}]]}})

(deftest make-state-space-test
  (let [state-space (doc/make-state-space {:model       model/single-threaded-simple-model
                                           :length      5
                                           :keys        keys
                                           :interpreter intp/interpret-timeline
                                           :universe    spec/empty-universe
                                           :partitions  5})
        multi-state-space (doc/make-state-space {:model       model/multi-threaded-simple-model
                                                 :length      2
                                                 :keys        keys
                                                 :interpreter intp/interpret-timeline
                                                 :universe    spec/empty-universe
                                                 :partitions  5})
        g0 (last state-space)
        gm (last multi-state-space)]
    (is (= [0 1] (doc/get-all-process-ids example-state)))
    (is (= {0 {:last-document {:test "0"}}, 1 {:last-document {:test "1"}}}
           (doc/get-all-local-vars example-state)))
    (is (= {0 {:test "0"}, 1 {:test "1"}}
           (doc/get-all-local-documents example-state)))
    (is (=* {:accounting          {0 {}, 'db {}}
             :balance             {0 0, 'db 0}
             :completed-transfers []}
            (ugraph/attrs g0 (nth (ugraph/nodes g0) 3))))
    (is (= "3 [shape=record, label=\"{{accounting|{process|db|0}|{document|\\{\\}|\\{\\}}}|{balance|{process|db|0}|{amount|0|0}}|{completed transfers|}}\"];"
           (let [node (nth (ugraph/nodes g0) 3)]
             (doc/make-dot-str g0 node))))

    (is (= "start ->  5[label=\"[+1€ pid=0]\"];"
           (let [node (nth (ugraph/edges g0) 1)]
             (doc/make-label-dot-str g0 node))))

    (is (= nil
           (doc/write-dot-file g0 "/tmp/del.me")))

    (is (= nil
           (doc/write-dot-file gm "/tmp/del2.me")))

    (is (= 2
           (count (doc/find-all-leafs g0))))

    (is (= '("start" 7 6)
           (doc/find-path g0 :invalid-timeline)))

    (comment (= :there-must-be-no-lost-updates
                (let [g (last (doc/make-state-space {:model       model/multi-threaded-simple-model
                                                     :length      9
                                                     :keys        keys
                                                     :interpreter intp/interpret-timeline
                                                     :universe    spec/empty-universe
                                                     :partitions  5}))]
                  (doc/write-dot-file (doc/remove-all-nodes g (doc/find-path g :property-violated))
                                      "/tmp/del3.me"))))))