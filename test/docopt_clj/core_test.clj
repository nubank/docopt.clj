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

(defn- make-argv [args]
  (let [args (s/trim args)]
    (into ["prog"]
          (when (not= "" args)
            (s/split args #"\s+")))))

(defn load-test-cases [path] 
  (apply merge-with merge 
         (map (fn [[_ doc tests]]
                {doc (into {} (map (fn [[_ args result]]
                                     [(make-argv args) (json/read-str result)])
                                   (re-seq test-block-regex tests)))})
              (re-seq doc-block-regex 
                      (s/replace (slurp path) #"#.*" "")))))

(def test-cases (load-test-cases "testcases.docopt"))

(eval `(deftest doc-specs  
         ~@(map (fn [doc] 
                  `(is (function? (docopt-clj.core/parsefn ~doc))))
                (keys test-cases))))

#_(eval `(deftest results-match
         ~@(mapcat (fn [[doc tests]]
                     (map (fn [[argv expected-result]]
                            `(is (= ~expected-result ((docopt-clj.core/parsefn ~doc) ~argv))))
                          tests))
                   test-cases)))
     

(run-tests 'docopt-clj.core-test)


