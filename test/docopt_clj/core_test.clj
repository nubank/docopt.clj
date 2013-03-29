(ns docopt-clj.core-test
  (:require [clojure.data.json :as json])
  (:require [clojure.string :as s])  
  (:use clojure.test)
  (:require docopt-clj.core))

(defn- re-concat [& more]
  (re-pattern (apply str (map (fn [re] 
                                (.pattern re)) 
                              more))))

(def doc-block-regex
  (let [doc-begin  #"r\"{3}"
        doc-body   #"((?:\"{0,2}[^\"]+)*)"
        separator  #"\"{3}\n+"
        tests      #"((?:[^r]|r(?!\"{3}))*)"]
    (re-concat doc-begin doc-body separator tests)))

(def test-block-regex
  (let [input-begin #"(?:\A|\n+)\s*\$\s*prog"
        input-body  #"(.*)"
        separator   #"\n"
        tests       #"((?:.+\n)*)"]
    (re-concat input-begin input-body separator tests)))

(defn load-test-cases [path] 
  (into (array-map) 
        (map (fn [[_ doc tests]]
               [doc (into (array-map) 
                          (map (fn [[_ args result]]
                                 [(filter seq (s/split (or args "") #"\s+")) (json/read-str result)])
                               (re-seq test-block-regex tests)))])
             (re-seq doc-block-regex 
                     (s/replace (slurp path) #"#.*" "")))))

(doseq [[doc tests] (load-test-cases "testcases.docopt")]  
  (let [parser (docopt-clj.core/parsefn doc)]
    (doseq [[in out] tests]     
      (let [test (str "\n" (s/trim-newline doc) "\n$ prog " (s/join " " in) "\nexpected: " out)
            result (or (parser in) "user-error")]
        (when (not= result out)
          (println (str test "\nobtained: " result "\n")))))))
  


