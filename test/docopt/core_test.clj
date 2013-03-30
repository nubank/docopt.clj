(ns docopt.core-test
  (:require [clojure.data.json :as json])
  (:require [clojure.string    :as s])  
  (:require [docopt.core       :as d])
  (:use     clojure.test))

(def doc-block-regex
  (let [doc-begin  "r\\\"{3}"
        doc-body   "((?:\\\"{0,2}[^\\\"]+)*)"
        separator  "\\\"{3}\n+"
        tests      "((?:[^r]|r(?!\\\"{3}))*)"]
    (re-pattern (str doc-begin doc-body separator tests))))

(def test-block-regex
  (let [input-begin "(?:\\A|\\n+)\\s*\\$\\s*prog"
        input-body  "(.*)"
        separator   "\\n"
        tests       "((?:.+\\n)*)"]
    (re-pattern (str input-begin input-body separator tests))))

(defn load-test-cases
  "Loads language-agnostic docopt tests from file (such as testcases.docopt)."
  [path] 
  (into (array-map) 
        (map (fn [[_ doc tests]]
               [doc (into (array-map) 
                          (map (fn [[_ args result]]
                                 [(filter seq (s/split (or args "") #"\s+")) (json/read-str result)])
                               (re-seq test-block-regex tests)))])
             (re-seq doc-block-regex (s/replace (slurp path) #"#.*" "")))))

(defn validation-report
  "Produces an error log of all failed tests, or nil on success."
  [test-cases]
  (reduce (fn [e [doc in out]]
            (let [result (or (d/match doc in) "user-error")]
              (if (= result out)
                e
                (str e "\n" (s/trim-newline doc) "\n$ prog " (s/join " " in) 
                     "\nexpected: " out "\nobtained: " result "\n\n"))))
          nil
          (mapcat (fn [[doc tests]]
                    (map #(into [doc] %) tests))
                  test-cases)))  

(defn valid? [test-cases-path]
  (if-let [e (validation-report (load-test-cases test-cases-path))]
    (throw (Exception. e))
    true))

(deftest docopt
  (is (valid? "testcases.docopt")))

