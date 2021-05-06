(ns docopt.usageblock
  (:require [clojure.string :as s]
            [docopt.util :refer [defmultimethods err re-arg-str re-tok specialize tokenize]]))

(specialize {::token [::repeat ::options ::option ::command ::argument ::group ::choice ::end-group]
             ::group [::required ::optional]})
            
;; ambiguities

(defn partial-long-re-str 
  "Creates partial match pattern for long option name."
  [names re-str [c & more-c]]
  (let [[_match & more-matches :as matches] (filter #(= c (first %)) names)]
    (if (or (empty? more-matches) (empty? more-c))
      (apply str re-str c (interleave more-c (repeat \?)))
      (recur (filter seq (map rest matches)) (str re-str c) more-c))))

(defn compile-long-options-re 
  "Generates regexes to unambiguously capture long options, for usage pattern parsing or for argv matching."
  [long-options pattern-parsing?]
  (let [longs (map :long long-options)]
    (into [] (map #(re-tok "--(" (if pattern-parsing? %1 (partial-long-re-str longs "" %1)) ")" %2)
                  longs (map #(when (:takes-arg %) "(?:=| )(\\S+)") long-options)))))

(defn compile-short-options-re  
  "Generates regexes to unambiguously capture short options, for usage pattern parsing or for argv matching."
  [short-options pattern-parsing?]
  (let [no-arg-shorts (apply str (map :short (remove :takes-arg short-options)))]
    (into (if (or pattern-parsing? (= "" no-arg-shorts)) [] [(re-tok "-([" no-arg-shorts "]+)")])
          (map (comp #(re-tok "-(" % ") ?(\\S+)")
                     (cond
                       pattern-parsing?     #(str "[^- " % "]*" %)
                       (= "" no-arg-shorts) identity
                       true                 #(str "[" (s/replace no-arg-shorts % "") "]*" %)))
               (map :short (filter :takes-arg short-options))))))

;; tokenize usage block

(defn tokenize-pattern
  "Extracts all tokens from usage pattern specification string;
'shorts/longs-re' are used to appropriately tokenize all possibly ambiguous options."
  [string shorts-re longs-re]
  (tokenize string 
            (concat [[#"\.{3}"                             ::repeat]
                     [#"\|"                                ::choice]
                     [#"\[(?i)options\]"                   ::options]
                     [#"(\(|\[)"                           ::group]
                     [#"(\)|\])"                           ::end-group]]
                    (map vector longs-re            (repeat :long-option)) 
                    (map vector shorts-re           (repeat :short-options))
                    [[(re-tok "--([^= ]+)=(<[^<>]*>|\\S+)") :long-option]
                     [(re-tok "--(\\S+)")                   :long-option]
                     [(re-tok "-(?!-)(\\S+)")               :short-options]
                     [(re-tok re-arg-str)                  ::argument]
                     [(re-tok "(\\S+)")                    ::command]])))

(defn find-option
  "Returns the corresponding option object in the 'options' sequence, or generates a new one."
  [name-key name arg lnum options]
  (let [takes-arg (boolean (seq arg))
        [option] (filter #(= name (% name-key)) options)]
    (err (and option (not= takes-arg (:takes-arg option))) :parse
         "Usage line " lnum ": " (if (= name-key :short) "short" "long") " option '" (option name-key)
         "' already defined with" (when takes-arg "out") " argument.")
    [::option lnum (or option {name-key name :takes-arg takes-arg})]))

(defmultimethods expand 
  "Adds line number to usage token, and replaces option name(s) with option object(s)."
  [[tag name arg :as token] lnum options] 
  tag
  ::token         [(into [tag lnum] (rest token))]
  :long-option    [(find-option :long name arg lnum options)]
  :short-options  (letfn [(new-short [arg c] (find-option :short (str c) arg lnum options))]
                    (conj (into [] (map (partial new-short nil) (butlast name)))
                          (new-short arg (last name)))))

(defn tokenize-pattern-lines
  "Helper function for 'tokenize-patterns'."
  [lines options]
  (let [shorts-re (compile-short-options-re (filter :short options) true)
        longs-re  (compile-long-options-re  (filter :long  options) true)]
    (reduce #(concat %1 [[::choice]] %2) 
            (map-indexed (fn [line-number line]
                           (mapcat #(expand % (inc line-number) options)
                                   (tokenize-pattern (s/replace line #"\s+" " ") shorts-re longs-re)))
                         lines))))

(defn tokenize-patterns
  "Generates a sequence of tokens for a sequence of usage specification lines joined by ' | '." 
  [lines options-block-options]
  (let [tokens              (tokenize-pattern-lines lines options-block-options)
        usage-block-options (reduce conj #{} (map #(% 2) (filter #(= ::option (% 0)) tokens)))
        options-diff        (remove usage-block-options options-block-options)]
    (mapcat (fn [[tag lnum & _ :as token]]
              (if (= tag ::options)
                (concat [[::group lnum "["]] (map #(vector ::option lnum %) options-diff) [[::end-group lnum "]"]])
                [token]))
            tokens)))

;; generate syntax tree

(defn- push-last [stack node]
  (conj (pop stack) (conj (pop (peek stack)) (conj (peek (peek stack)) node))))

(defn- peek-last [stack]
  (peek (peek (peek stack))))

(defn- pop-last [stack]
  (conj (pop stack) (conj (pop (peek stack)) (pop (peek (peek stack))))))

(defn make-choices
  "Generates the children of a ::choice node, where 'group-type' is either ::optional or ::required."
  [group-type children]
  (letfn [(mfn [[[head-tag & _ :as head] & tail :as group-body]]
               (if (and (seq group-body)
                        (or (seq tail)                                          
                            (not= head-tag group-type)))
                 (into [group-type] group-body)
                 head))
          (rfn [choices child]
               (if (seq (filter #(= % child) choices))
                 choices
                 (conj choices child)))]
    (reduce rfn [] (map mfn children))))

(defn end-group 
  "Updates stack with a fully-formed group."
  [stack [choice & more-choices :as choices]]
  (if (seq more-choices)
    (push-last (pop stack) (into [::choice] choices))
    (let [[head & [middle & tail :as more]] choice]
      (if (seq more)  
        (push-last (pop stack) (if (and (= head ::required) (empty? tail)) middle choice))
        (pop stack)))))

(defmultimethods make-node
  "Generates syntax tree node from token and adds it to stack." 
  [stack [tag line-number data]]
  tag
  ::token     (push-last stack (conj [tag] data))
  ::repeat    (push-last (pop-last stack) [tag (peek-last stack)])                
  ::choice    (conj (pop stack) (conj (peek stack) []))
  ::group     (conj stack [(if (= data "[") ::optional ::required) []])
  ::end-group (let [[group-type & children :as group] (peek stack)]
                (err (not= data (if (= group-type ::optional) "]" ")")) :parse
                     "Bad '" data "'" (when (number? line-number) (str " in usage line " line-number)) ".")
                (end-group stack (make-choices group-type children))))

(defn syntax-tree
  "Generates syntax tree from token sequence." 
  [tokens]
  (let [[_tree & more :as stack] (reduce make-node [[[]]] (concat [[::group nil "("]] tokens [[::end-group nil ")"]]))]
    (err (seq more) :parse "Missing ')' or ']'.")
    (or (peek-last stack) [])))

;; accumulation of options, commands, and arguments.
  
(defn collect-atoms
  "Collects all options, commands, and arguments referred to in usage patterns"
  [tokens]
  (let [selectfn   #(map last (filter (comp (partial = %) first) tokens))
        options    (group-by identity (selectfn ::option))]
    (doseq [o (keys options)]
      (let [alt-o (assoc o :takes-arg (not (:takes-arg o)))
            linefn #(s/join ", " (sort (into #{} (map second (options %)))))]
        (err (seq (options alt-o)) :parse
             "Conflicting definitions of '" (str \- (if (:long o) (str \- (:long o)) (:short o))) "': " 
             " takes " (when (:takes-arg alt-o) "no ") "argument on usage line(s) " (linefn o)
             " but takes " (when (:takes-arg o) "no ") "argument on usage line(s) " (linefn alt-o) ".")))
    (map (partial into #{}) [(keys options) (selectfn ::command) (selectfn ::argument)])))
 
(defmultimethods occurs
  "Counts occurences of a particular element in a syntax subtree (none, one, or several)." 
  [element [type & [data & _ :as children] :as node]]
  type
  nil      0
  ::token  (if (= data element) 1 0)
  ::repeat (* 2 (occurs element data))
  ::group  (reduce +   0 (map (partial occurs element) children))
  ::choice (reduce max 0 (map (partial occurs element) children)))

(defn parse 
  "Parses usage block, with a sequence of options from the options block to resolve pattern ambiguities."
  [usage-lines options]  
  (let [lines      (map #(rest (re-matches #"\s*(\S+)\s*(.*)" %)) usage-lines)
        prog-names (map first lines)]
    (err (apply not= prog-names) :parse
         "Inconsistent program name in usage patterns: " (s/join ", " (into #{} prog-names)) ".")
    (let [tokens                       (tokenize-patterns (map second lines) options)
          tree                         (syntax-tree tokens)
          [options commands arguments] (collect-atoms tokens)
          accfn                        #(vector %3 (if (< 1 (occurs %3 tree)) %1 %2))]
      {:name       (first prog-names)
       :tree       tree
       :shorts-re  (compile-short-options-re (filter :short options) false)
       :longs-re   (compile-long-options-re  (filter :long  options) false)
       :acc        (into {} (concat (map (partial accfn [] nil)  (concat arguments (filter :takes-arg options)))
                                    (map (partial accfn 0 false) (concat commands  (remove :takes-arg options)))))})))
