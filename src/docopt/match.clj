(ns docopt.match
  (:require [clojure.set       :as set])
  (:require [clojure.string    :as s])
  (:require [docopt.usageblock :as u])
  (:use      docopt.util))

(specialize {::token     [::option ::word]
             ::option    [::long   ::short]})

;; tokenize

(defmultimethods expand 
  "Expands command line token using provided sequence of options." 
  [[tag name arg :as token] options options-with-re] 
  tag
  ::token     [token]
  ::long      (let [[option-with-re] (filter #(re-find (re-pattern (str "^" (:long-re %) "$")) name) options-with-re)
                    [option]         (filter #(= (:long option-with-re) (:long %)) options)]
                [[::option (or option name) arg]])
  ::short     (let [options (map (fn [name]
                                   (let [[option] (filter #(= name (:short %)) options)]
                                     (or option name)))
                                 (map str name))]
                  (concat (map #(vector ::option %) (butlast options))
                          [[::option (last options) arg]])))

(defn option-re
  "Generates regex pattern to unambiguously match name within names."
  [name names]
  (loop [n 1] 
    (if (= n (count name))
      name
      (if (seq (rest (filter #(= (subs name 0 n) (subs % 0 n)) (filter #(<= n (count %)) names))))
        (recur (inc n))
        (str (subs name 0 n) (s/replace (subs name n) #"(.)" "$1?"))))))


(defn tokenize-command-line 
  "Generates sequence of tokens of the form [tag str/option] using the provided sequence of options."
  [line options]
  (let [long-names      (filter identity (map :long options))
        options-with-re (map #(if (:long %) (assoc % :long-re (option-re (:long %) long-names)) %) options)]
    (mapcat #(expand % options options-with-re)  
            (tokenize line (concat (arg-option-pairs options-with-re ::long ::short)
                                   [[(re-tok "(--?)")    ::word]
                                    [(re-tok "--(\\S+)") ::long]
                                    [(re-tok "-(\\S+)")  ::short]
                                    [(re-tok "(\\S+)")   ::word]])))))

;; match

(defn push-acc 
  "Returns accumulator with value 'v' added at key 'k', or nil in case of failure."
  [acc k v]
  (if-let [k ((into #{} (keys acc)) k)]
    (let [to-val (acc k)]
      (cond 
        (number? to-val) (if (nil? v) (assoc acc k (inc to-val)))
        (= false to-val) (if (nil? v) (assoc acc k true))
        (vector? to-val) (if (not (nil? v)) (assoc acc k (conj to-val v)))
        (nil? to-val)    (if (not (nil? v)) (assoc acc k v))))))


(defn option-move
  "Returns accumulators '[to from]' with option 'o' moved from 'from' to 'to', or nil in case of failure."
  [o to from]
  (let [fv (from o)
        from (dissoc from o)]
    (case fv 
      ([] 0 false nil) nil
      (cond 
        (vector? fv)           [(push-acc to o (first fv)) (if (seq (rest fv)) (assoc from o (into [] (rest fv))) from)]
        (number? fv)           [(push-acc to o nil)        (if (< 0 (dec fv))  (assoc from o (dec fv))            from)]
        (= true fv)            [(push-acc to o nil)        from]
        true                   [(push-acc to o fv)         from]))))

(defmultimethods consume 
  "If command line state matches tree node, update accumulator, else return nil."
  [[type key :as pattern] [acc remaining [word & more-words :as cmdseq] :as state]]
  type
  :docopt.usageblock/argument (if-let [new-acc (push-acc acc key word)]
                                [new-acc remaining more-words])
  :docopt.usageblock/command  (if (= key word)
                                (if-let [new-acc (push-acc acc key nil)]
                                  [new-acc remaining more-words]))
  :docopt.usageblock/option   (let [[to from] (option-move key acc remaining)]
                                (if (and to from) 
                                  [to from cmdseq])))
    
(defmultimethods matches 
  "If command line state matches tree node, update accumulator, else return nil."
  [states [type & children :as pattern]]
  type
  nil                         states
  :docopt.usageblock/token    (into #{} (filter identity (map (partial consume pattern) states)))
  :docopt.usageblock/choice   (apply set/union (map (partial matches states) children))
  :docopt.usageblock/optional (reduce #(into %1 (matches %1 %2)) states children)
  :docopt.usageblock/required (reduce matches states children)
  :docopt.usageblock/repeat   (let [new-states (matches states (first children))]
                                (if (= states new-states)
                                  states                 
                                  (into new-states (matches new-states pattern)))))

;;

(defn accumulate-option-arg 
  [acc [_ option arg]]
  (if (and acc (not (string? option))
           (= (:takes-arg option) (not (nil? arg))))
    (push-acc acc (dissoc option :long-re) arg)))

(defn  accumulate-option-default 
  [acc [option val]]
  (let [default (:default-value option)
        new-acc (if (and acc (:takes-arg option) (or (nil? val) (= [] val)))
                  (assoc acc option (if (and default (= [] val) (not (vector? default))) [default] default))
                  acc)]
    (case (new-acc option)
      (0 [] false nil) (dissoc new-acc option)
      new-acc)))

(defn match-argv 
  "Match command-line arguments with usage patterns."
  [{:keys [acc tree]} argv]
  (let [options        (remove string? (keys acc))
        options-acc    (into {} (filter (comp not string? key) acc))
        args           (s/join " " (filter seq (if (string? argv) (s/split argv #"(?:\s|\n)+") argv)))
        [before after] (s/split (or args "") #" -- ") 
        tokens         (concat (tokenize-command-line (or before "") options)
                               (map #(vector ::word %) (if (seq after) (s/split after " "))))
        options-acc    (reduce accumulate-option-arg     options-acc (filter #(isa? (first %) ::option) tokens))
        remaining      (reduce accumulate-option-default options-acc options-acc)]    
    (when remaining
      (let [all-matches (matches #{[acc remaining (map second (filter #(isa? (first %) ::word) tokens))]} tree)
            match (ffirst (filter #(and (empty? (% 1)) (empty? (% 2))) all-matches))]
        (when match
          (into {} (map #(if (string? (key %)) % (vector (option->string (key %)) (val %))) match)))))))
