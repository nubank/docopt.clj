(ns docopt.util
  (:require [clojure.string :as s]))

;; macros

(defmacro err [err-clause type & err-strs]
  `(when ~err-clause
     (throw
      (ex-info (str "DOCOPT ERROR "
                    ~(case type :syntax "(syntax) " :parse "(parse) ")
                    "| " ~@err-strs)
               {:docopt.error/type    ~type
                :docopt.error/msg     (str ~@err-strs)}))))

(defmacro defmultimethods
  "Syntactic sugar for defmulti + multiple defmethods."
  [method-name docstring args dispatch-fn-body & body]
  `(do (defmulti ~method-name ~docstring (fn ~args (do ~dispatch-fn-body)))
       ~@(map (fn [[dispatched-key dispatched-body]]
                `(defmethod ~method-name ~dispatched-key ~args (do ~dispatched-body)))
              (apply array-map body))))
  
(defmacro specialize
  "Syntactic sugar for derive."
  [m]
  `(do ~@(mapcat (fn [[parent children]] (map (fn [child] `(derive ~child ~parent)) children)) m)))

;; tokenization

(def re-arg-str "(<[^<>]*>|[A-Z_0-9]*[A-Z_][A-Z_0-9]*)") ; argument pattern

(defn re-tok 
  "Generates tokenization regexp, bounded by whitespace or string beginning / end."
  [& patterns]
  (re-pattern (str "(?<=^| )" (apply str patterns) "(?=$| )")))

(defn tokenize
  "Repeatedly extracts tokens from string according to sequence of [re tag]; 
tokens are of the form [tag & groups] as captured by the corresponding regex."
  [string pairs]
  (letfn [(tokfn [[re tag] source]
                 (if (string? source)
                   (let [substrings (map s/trim (s/split (str " " (s/trim source) " ") re))
                         new-tokens (map #(into [tag] (when (vector? %) (filter seq (rest %))))
                                         (re-seq re source))]                           
                     (filter seq (interleave substrings (concat (when tag new-tokens) (repeat nil)))))
                   [source]))]
    (reduce #(mapcat (partial tokfn %2) %1) [string] pairs)))
