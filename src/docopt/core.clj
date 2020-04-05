(ns docopt.core
  (:require [clojure.string      :as s]
            [docopt.match        :as m]
            [docopt.optionsblock :as o]
            [docopt.usageblock   :as u]))

(defn parse
  "Parses doc string."
  [doc]
  {:pre [(string? doc)]}
  (letfn [(sec-re   [name]          (re-pattern (str "(?:^|\\n)(?!\\s).*(?i)" name ":\\s*(.*(?:\\n(?=[ \\t]).+)*)")))
          (section  [name splitfn]  (map s/trim (mapcat (comp splitfn second) (re-seq (sec-re name) doc))))
          (osplitfn [options-block] (re-seq #"(?<=^|\n)\s*-.*(?:\s+[^- \t\n].*)*" options-block))]
    (u/parse (section "usage" s/split-lines) (o/parse (section "options" osplitfn)))))

(defn docopt
  [doc args f]
  (if-let [arg-map (-> doc parse (m/match-argv args))]
    (f arg-map)
    (println doc)))
