(ns docopt.core
  (:require [clojure.string :as s]
            [docopt.match :as m]
            [docopt.optionsblock :as o]
            [docopt.usageblock :as u]))

(defn parse
  "Parses doc string."
  [doc]
  {:pre [(string? doc)]}
  (letfn [(sec-re   [name]          (re-pattern (str "(?:^|\\n)(?!\\s).*(?i)" name ":\\s*(.*(?:\\n(?=[ \\t]).+)*)")))
          (section  [name splitfn]  (map s/trim (mapcat (comp splitfn second) (re-seq (sec-re name) doc))))
          (osplitfn [options-block] (re-seq #"(?<=^|\n)\s*-.*(?:\s+[^- \t\n].*)*" options-block))]
    (u/parse (section "usage" s/split-lines) (o/parse (section "options" osplitfn)))))

(defn docopt
  "Parse `argv` based on command-line interface described in `doc`.

  `docopt` creates your command-line interface based on its
  description that you pass as `doc`. Such description can contain
  --options, <positional-argument>, commands, which could be
  [optional], (required), (mutually | exclusive) or repeated...

  This function has multiple arities. The default one is the 2-arity
  one.

  `result-fn` is a function that will be called with the resulting
  map after parsing `doc`+`argv`. By default in the 2 arity version
  of this function, this will be set to `identity` function, i.e.:
  will return the map without changes.

  `usage-fn` is a function that will be called in case of issues
  while parsing, and will receive the `doc` as argument. By default
  in the 2/3 arity version of this function, this will be set to
  `println`, i.e.: it will print the usage of the program and return
  `nil`."
  ([doc args]
   (docopt doc args identity))
  ([doc args result-fn]
   (docopt doc args result-fn println))
  ([doc args result-fn usage-fn]
   (if-let [arg-map (-> doc parse (m/match-argv args))]
     (result-fn arg-map)
     (usage-fn doc))))
