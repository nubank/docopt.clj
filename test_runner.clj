#!/usr/bin/env bb

(ns test-runner
  (:require [clojure.test :as t]
            [clojure.string :as string]
            [babashka.classpath :as cp]
            [babashka.fs :as fs]))

(cp/add-classpath "src:test")

(defn test-file->test-ns
  [file]
  (as-> file $
        (fs/components $)
        (drop 1 $)
        (mapv str $)
        (string/join "." $)
        (string/replace $ #"_" "-")
        (string/replace $ #".clj$" "")
        (symbol $)))

(def test-namespaces
  (->> (fs/glob "test" "**/*_test.clj")
       (mapv test-file->test-ns)))

(apply require test-namespaces)

(defn run-tests!
  []
  (let [{:keys [fail error]} (apply t/run-tests test-namespaces)]
    (System/exit (+ fail error))))

(when (= *file* (System/getProperty "babashka.file"))
    (run-tests!))
