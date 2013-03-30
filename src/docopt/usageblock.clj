(ns docopt.usageblock
  (:require [clojure.set    :as set])
  (:require [clojure.string :as s])
  (:use      docopt.util))

(specialize {::token     [::repeat ::options ::option ::word ::group ::choice]
             ::group     [::optional ::required]
             ::optional  [::end-optional]
             ::required  [::end-required]             
             ::word      [::command ::argument]
             ::command   [::separator ::stdin]
             ::option    [::long ::short]})

;; tokenize usage block

(defn tokenize-pattern
  "Extracts all tokens from usage pattern specification string;
'options' is used to appropriately tokenize all possibly ambiguous options."
  [string options]
  (tokenize string 
            (concat [[#"\.{3}"                              ::repeat]
                     [#"\|"                                 ::choice]
                     [#"\("                                 ::required]
                     [#"\)"                                 ::end-required]                   
                     [#"\[-\]"                              ::stdin]
                     [#"\[--\]"                             ::separator]
                     [#"\[options\]"                        ::options]
                     [#"\["                                 ::optional]
                     [#"\]"                                 ::end-optional]]
                    (arg-option-pairs options ::long ::short)
                    [[(re-tok "--([^= ]+)=(<[^<>]*>|\\S+)") ::long]
                     [(re-tok "--(\\S+)")                   ::long]
                     [(re-tok "-(\\S+)")                    ::short]
                     [(re-tok re-arg-str)                   ::argument]
                     [(re-tok "(\\S+)")                     ::command]])))

(defn find-option
  "Returns the corresponding option object in the 'options' sequence, or generates a new one."
  [name-key name arg lnum options]
  (let [takes-arg (not (empty? arg))
        [option] (filter #(= name (% name-key)) options)]
    (err (and option (not= takes-arg (:takes-arg option))) :parse
         "Usage line " lnum ": " (if (= name-key :short) "short" "long") " option '" (option name-key)
         "'already defined with" (if takes-arg "out") " argument.")
    [::option lnum (or option {name-key name :takes-arg takes-arg})]))

(defmultimethods expand 
  "Replaces a usage token with possibly several more, and also to:
- include line numbers after tag,
- replace option names with option objects,
- etc." 
  [[tag name arg :as token] lnum options] 
  tag
  ::token     [[tag lnum]]
  ::word      [[tag lnum name]]
  ::stdin     [[::optional lnum] [::command lnum "-"]  [::end-optional lnum]]
  ::separator [[::optional lnum] [::command lnum "--"] [::end-optional lnum]]
  ::long      [(find-option :long name arg lnum options)]
  ::short     (letfn [(new-short [arg c] (find-option :short (str c) arg lnum options))]
                (conj (into [] (map (partial new-short nil) (butlast name)))
                      (new-short arg (last name)))))

(defn tokenize-lines
  "Helper function for 'tokenize-patterns'."
  [lines options]
  (reduce #(concat %1 [[::choice]] %2) 
          (map-indexed (fn [line-number line]
                         (mapcat #(expand % (inc line-number) options)
                                 (tokenize-pattern (s/replace line #"\s+" " ") options)))
                       lines)))

(defn tokenize-patterns
  "Generates a sequence of tokens for a sequence of usage specification lines joined by ' | '." 
  [lines options-block-options]
  (let [tokens              (tokenize-lines lines options-block-options)
        usage-block-options (reduce conj #{} (map #(% 2) (filter #(= ::option (% 0)) tokens)))
        options-diff        (remove usage-block-options options-block-options)]
    (mapcat (fn [[tag lnum & more :as token]]
              (if (= tag ::options)
                (concat [[::optional lnum]] (map #(vector ::option lnum %) options-diff) [[::end-optional lnum]])
                [token]))
            tokens)))

;; generate syntax tree

(defn push-last [stack node]
  (conj (pop stack) (conj (pop (peek stack)) (conj (peek (peek stack)) node))))

(defn peek-last [stack]
  (peek (peek (peek stack))))

(defn pop-last [stack]
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

(specialize {::group-end [::end-optional ::end-required]})

(defmultimethods make-node
  "Generates syntax tree node from token and adds it to stack." 
  [stack [tag line-number data]]
  tag
  ::group     (conj stack [tag []])
  ::token     (push-last stack (conj [tag] data))
  ::repeat    (push-last (pop-last stack) [tag (peek-last stack)])                
  ::choice    (conj (pop stack) (conj (peek stack) []))
  ::group-end (let [[group-type & children :as group]   (peek stack)                    
                    [choice & more-choices :as choices] (make-choices group-type children)]
                (err (not (isa? tag group-type)) :parse
                     "Bad '" (if (= tag ::end-optional) \] \)) "'" (if (number? line-number) (str " in line " line-number)) ".")
                (if (seq more-choices) 
                  (push-last (pop stack) (into [::choice] choices))
                  (let [[head & [middle & tail :as more]] choice]
                    (if (seq more)  
                      (push-last (pop stack) (if (and (= head ::required) (empty? tail)) middle choice))
                      (pop stack))))))

(prefer-method make-node ::group-end ::group)
             
(defn syntax-tree
  "Generates syntax tree from token sequence." 
  [tokens]
  (let [[tree & more :as stack] (reduce make-node [[[]]] (concat [[::required]] tokens [[::end-required]]))]
    (err (seq more) :parse "Missing ')' or ']'.")
    (or (peek-last stack) []))) 

;; accumulation of options, commands, and arguments.
  
(defn collect-atoms
  "Collects all options, commands, and arguments referred to in usage patterns"
  [tokens]
  (let [token-groups (group-by first tokens)
        options (group-by last (token-groups ::option))
        long-names (filter identity (map :long options))] 
    (doseq [o (keys options)]
      (let [alt-o (assoc o :takes-arg (not (:takes-arg o)))
            linefn #(s/join ", " (sort (into #{} (map second (options %)))))]
        (err (seq (options alt-o)) :parse
             "Conflicting definitions of '" (option->string o) "': " 
             " takes " (if (:takes-arg alt-o) "no ") "argument on line(s) " (linefn o)
             " but takes " (if (:takes-arg o) "no ") "argument on line(s) " (linefn alt-o) ".")))
    [(into #{} (keys options))
     (into #{} (map last (token-groups ::command)))
     (into #{} (map last (token-groups ::argument)))]))
 
(defmultimethods occurs
  "Counts occurences of a particular element in a syntax subtree (none, one, or several)." 
  [element [type & [data & _ :as children] :as node]]
  type
  nil      0
  ::token  (if (= data element) 1 0)
  ::repeat (* 2 (occurs element data))
  ::group  (reduce +   0 (map (partial occurs element) children))
  ::choice (reduce max 0 (map (partial occurs element) children)))

(defn accumulator   
  "Generates an empty accumulator used for storing argv pattern matching results." 
  [tree [options commands arguments]]
  (letfn [(acc-base [no-acc acc v] 
                    [v (if (< 1 (occurs v tree)) acc no-acc)])]
    (into {} (concat (map (partial acc-base nil [])  (filter :takes-arg options))
                     (map (partial acc-base false 0) (filter (comp not :takes-arg) options))
                     (map (partial acc-base false 0) commands)
                     (map (partial acc-base nil [])  arguments)))))

;; 

(defn parse 
  "Parses usage block, with a sequence of options from the options block to resolve pattern ambiguities."
  [block options]  
  (let [lines      (map #(rest (re-matches #"\s*(\S+)\s*(.*)" %)) (s/split-lines block))
        prog-names (map first lines)]
    (err (apply not= prog-names) :parse
         "Inconsistent program name in usage patterns: " (s/join ", " (into #{} prog-names)) ".")
    (let [tokens     (tokenize-patterns (map second lines) options)
          tree       (syntax-tree tokens)]
      {:name (first prog-names)
       :tree tree
       :acc  (accumulator tree (collect-atoms tokens))})))
