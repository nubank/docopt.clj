(ns docopt.core-test
  (:require [clojure.data.json :as json])
  (:require [clojure.string    :as s])  
  (:require [docopt.core       :as d])
  (:require [docopt.match      :as m])
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
  (into [] (mapcat (fn [[_ doc tests]]
                   (map (fn [[_ args result]]
                          [doc (into [] (filter seq (s/split (or args "") #"\s+"))) (json/read-str result)])
                        (re-seq test-block-regex tests)))
                 (re-seq doc-block-regex (s/replace (slurp path) #"#.*" "")))))

(defn test-case-error-report
  "Returns a report of all failed test cases"
  [doc in out]
  (let [docinfo (try (d/parse doc) 
                  (catch Exception e (.getMessage e)))]
    (if (string? docinfo)
      (str "\n" (s/trim-newline doc) "\n" docinfo)
      (let [result (or (m/match-argv docinfo in) "user-error")]
        (if (not= result out)
          (str "\n" (s/trim-newline doc) "\n$ prog " (s/join " " in) 
               "\nexpected: " out "\nobtained: " result "\n\n"))))))

(defn valid?
  "Validates all test cases found in the file named 'test-cases-file-name'."
  [test-cases-file-name]
  (let [test-cases (load-test-cases test-cases-file-name)]
    (when-let [eseq (seq (remove nil? (map (partial apply test-case-error-report) test-cases)))]
      (println "Failed" (count eseq) "/" (count test-cases) "tests loaded from '" test-cases-file-name "'.\n")
      (throw (Exception. (apply str eseq))))
    (println "Successfully passed" (count test-cases) "tests loaded from '" test-cases-file-name "'.\n")
    true))

(deftest docopt
  (is (valid? "https://raw.github.com/docopt/docopt/511d1c57b59cd2ed663a9f9e181b5160ce97e728/testcases.docopt")))
