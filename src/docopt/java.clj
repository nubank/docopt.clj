(ns docopt.java
  (:require [:docopt.core :as docopt])
  (:import java.util.HashMap)
  (:gen-class
    :name org.docopt.clj
    :methods [^{:static true} [docopt [String String[]] java.util.HashMap]]))

(defn docopt [doc args]
  (if-let [cljmap (docopt/match doc (into [] args))]
    (let [javamap (HashMap. (count cljmap))]
      (doseq [[k v] cljmap]
        (.put javamap k (if (vector? v) (to-array v) v)))
      javamap)))
        
        




