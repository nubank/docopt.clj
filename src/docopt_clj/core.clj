(ns docopt-clj.core
  (:require [clojure.string :as s])
  (:require [clojure.set :as set])
  (:use     [slingshot.slingshot :only [throw+ try+]]))

;;;; helpers

(defmacro defmultimethods [method-name args dispatch-fn-body & body]
  {:pre [(even? (count body)) (vector? args)]}
  `(do (defmulti ~method-name (fn ~args (do ~dispatch-fn-body)))
     ~@(map (fn [[dispatched-key dispatched-body]]
              `(defmethod ~method-name ~dispatched-key ~args (do ~dispatched-body)))
            (apply array-map body))))
 
(defmacro specialize [m]
  `(do ~@(mapcat (fn [[parent children]]
                   (map (fn [child] `(derive ~child ~parent)) children))
                 m)))

;;

(defmacro err [err-clause type & err-strs]
  `(when ~err-clause
     (throw+ {:type ~type :msg (str ~@err-strs)})))

(specialize {::error [::syntax ::parse]})

(defmultimethods err-print [{:keys [type msg]}]
  type
  ::error  (str "ERROR | " msg)
  ::syntax (str "ERROR (syntax) | " msg)
  ::parse  (str "ERROR (parse) | " msg))


;;;; tokenization

(defn tokenize-string [line pairs]
  (reduce (fn [tokenseq [re tag]]
            (mapcat (fn [maybe-string]
                      (if (string? maybe-string)
                        (let [substrings (map s/trim (s/split (str " " (s/trim maybe-string) " ") re))
                              new-tokens (map #(into [tag] (if (vector? %) (filter seq (rest %))))
                                              (re-seq re maybe-string))]                           
                          (filter seq (interleave substrings (concat (if tag new-tokens) (repeat nil)))))
                        [maybe-string]))
                    tokenseq))
          [line]
          pairs))


;;;; parse options block

(defn re-tok [& patterns]
  (re-pattern (str "(?<=^| )" (apply str patterns) "(?=$| )")))

(def re-arg-str "(<[^<>]*>|[A-Z_0-9]*[A-Z_][A-Z_0-9]*)")

(defn parse-option-key [option-key ]
  (tokenize-string (or option-key "")
                   [[#"(<[^<>]*>)" :arg]
                    [#"(?:=|,|\s+)" nil]
                    [(re-tok re-arg-str) :arg]
                    [(re-tok "--(\\S+)") :long]
                    [(re-tok "-(\\S+)")  :short]]))

(defn parse-default-values [option-description]
  (let [[default-string] (map second (re-seq #"\[(?i)default:\s*([^\]]*)\]" (or option-description "")))]
    (into [] (filter seq (s/split (or default-string "") #"\s+")))))

(defn parse-option-line [option-line]
  (let [[option-key option-description] (s/split option-line #"\s{2,}" 2)
        tokens (parse-option-key option-key)
        default-values (parse-default-values option-description)]
    (err (seq (filter string? tokens)) ::syntax
         "Badly-formed option definition in '" option-key "'")
    (let [{:keys [short long arg] :as option} (reduce conj {} tokens)]
      (conj {:takes-arg (boolean arg)} 
            (if short [:short short])
            (if long  [:long long])
            (if arg [:default-value (if (seq (rest default-values)) default-values (first default-values))])
            (if (seq option-description) [:description option-description])))))

(defn option-string [{:keys [short long]}]
  (if long (str "--" long) (str "-" short)))

(defn find-repeated [seq] 
  (ffirst (filter #(< 1 (val %)) (frequencies seq))))

(defn parse-options-block [options-block]
  (let [reducefn #(if (= \- (first %2)) (conj %1 %2) (conj (pop %1) (str (peek %1) %2)))
        options (map parse-option-line (rest (reduce reducefn [] (map s/trim (s/split-lines (str "-\n" options-block))))))
        redefined (or (find-repeated (filter identity (map :short options)))
                      (find-repeated (filter identity (map :long options))))]
    (err redefined ::syntax
         "In options descriptions, multiple definitions of option '" (option-string redefined) "'.")    
    (into #{} options)))


;;;; parse usage lines into token seq
;; all tokens are of the form [tag & more] before expansion and [tag line-number & more] after expansion,
;; where tag derives from ::token

(specialize {::token     [::repeat ::options ::option ::word ::group ::choice]
             ::group     [::optional ::required]
             ::optional  [::end-optional]
             ::required  [::end-required]             
             ::word      [::command ::argument]
             ::command   [::separator ::stdin]
             ::option    [::long ::short]})

(defn arg-option-pairs [options]
  (let [options (filter :takes-arg options)]
    (concat (zipmap (map #(re-tok "--(" % ")(?:=| )(\\S+)") (map #(or (:long-re %) (:long %)) (filter :long  options)))  
                    (repeat ::long))
            (zipmap (map #(re-tok "-([^- " % "]*" % ") ?(\\S+)") (map :short (filter :short options))) 
                    (repeat ::short)))))

(defn parse-usage-line [usage-line options]
  (tokenize-string 
    usage-line
    (concat [[#"\.{3}" ::repeat]
             [#"\|" ::choice]
             [#"\(" ::required]
             [#"\)" ::end-required]                   
             [#"\[-\]" ::stdin]
             [#"\[--\]" ::separator]
             [#"\[options\]" ::options]
             [#"\[" ::optional]
             [#"\]" ::end-optional]]
            (arg-option-pairs options)
            [[(re-tok "--([^= ]+)=(<[^<>]*>|\\S+)") ::long]
             [(re-tok "--(\\S+)") ::long]
             [(re-tok "-(\\S+)") ::short]
             [(re-tok re-arg-str) ::argument]
             [(re-tok "(\\S+)") ::command]])))
  
(defn find-option [name-key name arg lnum options]
  (let [takes-arg (not (empty? arg))
        [option] (filter #(= name (% name-key)) options)]
    (err (and option (not= takes-arg (:takes-arg option))) ::parse
         "Usage line " lnum ": " (if (= name-key :short) "short" "long") " option '" (option name-key) 
         "'already defined with" (if takes-arg "out") " argument.")
    [::option lnum (or option {name-key name :takes-arg takes-arg})]))


(defmultimethods usage-token-expand 
  [[tag name arg :as token] line-number block-options] 
  tag
  ::token     [[tag line-number]]
  ::word      [[tag line-number name]]
  ::stdin     [[::optional line-number] [::command line-number "-"]  [::end-optional line-number]]
  ::separator [[::optional line-number] [::command line-number "--"] [::end-optional line-number]]
  ::long      [(find-option :long name arg line-number block-options)]
  ::options   (concat [[::optional line-number]] 
                      (map #(vector ::option line-number %) block-options) 
                      [[::end-optional line-number]])
  ::short     (letfn [(new-short [arg c] (find-option :short (str c) arg line-number block-options))]
                (conj (into [] (map (partial new-short nil) (butlast name)))
                      (new-short arg (last name)))))

(defn parse-usage-syntax [usage-lines block-options]
  (reduce #(concat %1 [[::choice]] %2) 
          (map-indexed (fn [line-number usage-line]
                         (mapcat #(usage-token-expand % (inc line-number) block-options)
                                 (parse-usage-line (s/replace usage-line #"\s+" " ") block-options)))
                       usage-lines)))


;;;; parse usage pattern 

(defn push-last [stack node]
  (conj (pop stack) (conj (pop (peek stack)) (conj (peek (peek stack)) node))))

(defn peek-last [stack]
  (peek (peek (peek stack))))

(defn pop-last [stack]
  (conj (pop stack) (conj (pop (peek stack)) (pop (peek (peek stack))))))

(defn make-choices [group-type children]
  (letfn [(tfn [[[head-tag] & more :as group-body]]
               (and (seq group-body)
                    (or (seq more)                                          
                        (not= head-tag group-type))))
          (mfn [group-body]
               (if (tfn group-body)
                 (into [group-type] group-body)
                 (first group-body)))
          (rfn [choices child]
               (if (seq (filter #(= % child) choices))
                 choices
                 (conj choices child)))]
    (reduce rfn [] (map mfn children))))

(specialize {::group-end [::end-optional ::end-required]})

(defmultimethods token-tree
  [stack [tag line-number data]]
  tag
  ::group     (conj stack [tag []])
  ::word      (push-last stack (conj [tag] data))
  ::repeat    (push-last (pop-last stack) [tag (peek-last stack)])                
  ::choice    (conj (pop stack) (conj (peek stack) []))
  ::group-end (let [[group-type & children :as group]   (peek stack)                    
                    [choice & more-choices :as choices] (make-choices group-type children)]
                (err (not (isa? tag group-type)) ::parse
                     "Bad '" (if (= tag ::end-optional) \] \)) "'" (if (number? line-number) (str " in line " line-number)) ".")
                (if (seq more-choices) 
                  (push-last (pop stack) (into [::choice] choices))
                  (let [[head & [middle & tail :as more]] choice]
                    (if (seq more)  
                      (push-last (pop stack) (if (and (= head ::required) (empty? tail)) middle choice))
                      (pop stack))))))

(prefer-method token-tree ::group-end ::group)
             
(defn parse-usage-pattern [tokens]
  (let [[tree & more :as stack] (reduce token-tree [[[]]] (concat [[::required]] tokens [[::end-required]]))]
    (err (seq more) ::parse "Missing ')' or ']'.")
    (or (peek-last stack) []))) 

;;;; parse & accumulate usage variables
  

(defn parse-usage-variables [tokens]
  (let [token-groups (group-by first tokens)
        options (group-by last (token-groups ::option))
        long-names (filter identity (map :long options))] 
    (doseq [o (keys options)]
      (let [alt-o (assoc o :takes-arg (not (:takes-arg o)))
            linefn #(s/join ", " (sort (into #{} (map second (options %)))))]
        (err (seq (options alt-o)) ::parse
             "Conflicting definitions of '" (option-string o) "': " 
             " takes " (if (:takes-arg alt-o) "no ") "argument on line(s) " (linefn o)
             " but takes " (if (:takes-arg o) "no ") "argument on line(s) " (linefn alt-o) ".")))
    {::option   (into #{} (keys options))
     ::command  (into #{} (map last (token-groups ::command)))
     ::argument (into #{} (map last (token-groups ::argument)))}))
 
(defmultimethods occurs
  [element [type & [data & _ :as children] :as node]]
  type
  nil      0
  ::token  (if (= data element) 1 0)
  ::repeat (* 2 (occurs element data))
  ::group  (reduce + 0 (map (partial occurs element) children))
  ::choice (reduce max 0 (map (partial occurs element) children)))

(defn accumulator [tree variables]
  (letfn [(acc-base [no-acc acc v] [v (if (< 1 (occurs v tree)) acc no-acc)])]
    (into {} (concat (map (partial acc-base nil [])  (filter :takes-arg (variables ::option)))
                     (map (partial acc-base false 0) (filter (comp not :takes-arg) (variables ::option)))
                     (map (partial acc-base false 0) (variables ::command))
                     (map (partial acc-base nil [])  (variables ::argument))))))

;;;; parse doc

(defn split-doc-blocks [doc]
  (let [usage-split (s/split doc #"(?i)usage:\s*")]
    (err (not= 2 (count usage-split)) ::syntax
         (count usage-split) " occurences of the 'usage:' keyword, 1 expected.")
    (s/split (second usage-split) #"\n\s*\n" 2)))

(defn option-re [name names]
  (loop [n 1] 
    (if (= n (count name))
      name
      (if (seq (rest (filter #(= (subs name 0 n) (subs % 0 n)) (filter #(<= n (count %)) names))))
        (recur (inc n))
        (str (subs name 0 n) (s/replace (subs name n) #"(.)" "$1?"))))))  

(defn parse-doc [doc]
  (let [[usage-block options-block] (split-doc-blocks doc)]
    (err (= "" usage-block) ::syntax
         "No usage patterns, at least 1 expected.")      
    (let [usage-lines    (map #(rest (re-matches #"\s*(\S+)\s*(.*)" %)) (s/split-lines usage-block))
          prog-names     (map first usage-lines)]
      (err (apply not= prog-names) ::parse
           "Inconsistent program name in usage patterns: " (s/join ", " (keys (group-by identity prog-names))) ".")
      (let [tokens     (parse-usage-syntax (map second usage-lines) (parse-options-block options-block))
            variables  (parse-usage-variables tokens)
            long-names (filter identity (map :long (::option variables))) 
            tree       (parse-usage-pattern tokens)]
        {:name       (first prog-names)
         :pattern    tree
         :options    (map #(if (:long %) (assoc % :long-re (option-re (:long %) long-names)) %) (::option variables))
         :acc        (accumulator tree variables)}))))

;;;; parse command line

(defmultimethods command-token-expand 
  [[tag name arg :as token] options] 
  tag
  ::token     [token]
  ::long      (let [[option] (filter #(re-find (re-pattern (str "^" (:long-re %) "$")) name) options)]
                [[::option (or option name) arg]])
  ::short     (let [options (map (fn [name]
                                   (let [[option] (filter #(= name (:short %)) options)]
                                     (or option name)))
                                 (map str name))]
                  (concat (map #(vector ::option %) (butlast options))
                          [[::option (last options) arg]])))

(defn tokenize-command-line [line options]
  (mapcat #(command-token-expand % options)  
          (tokenize-string line 
                           (concat (arg-option-pairs options)
                                   [[(re-tok "(--?)") ::word]
                                    [(re-tok "--(\\S+)") ::long]
                                    [(re-tok "-(\\S+)") ::short]
                                    [(re-tok "(\\S+)") ::word]]))))

;; match

(defn push-acc [acc k v]
  (if-let [k ((into #{} (keys acc)) k)]
    (let [to-val (acc k)]
      (cond 
        (number? to-val) (if (nil? v) (assoc acc k (inc to-val)))
        (= false to-val) (if (nil? v) (assoc acc k true))
        (vector? to-val) (if (not (nil? v)) (assoc acc k (conj to-val v)))
        (nil? to-val)    (if (not (nil? v)) (assoc acc k v))))))

(defn option-move [o to from]
  (let [fv (from o)
        from (dissoc from o)]
    (case fv 
      ([] 0 false nil) nil
      (cond 
        (vector? fv)           [(push-acc to o (first fv)) (if (seq (rest fv)) (assoc from o (into [] (rest fv))) from)]
        (number? fv)           [(push-acc to o nil)        (if (< 0 (dec fv))  (assoc from o (dec fv))            from)]
        (= true fv)            [(push-acc to o nil)        from]
        true                   [(push-acc to o fv)         from]))))

(defmultimethods consume [[type key :as pattern] [acc remaining [word & more-words :as cmdseq] :as state]]
  type
  ::argument (if-let [new-acc (push-acc acc key word)]
               [new-acc remaining more-words])
  ::command  (if (= key word)
               (if-let [new-acc (push-acc acc key nil)]
                 [new-acc remaining more-words]))
  ::option   (let [[to from] (option-move key acc remaining)]
               (if (and to from) 
                 [to from cmdseq])))
    
(defmultimethods matches [states [type & children :as pattern]]
  type
  nil        states
  ::token    (into #{} (filter identity (map (partial consume pattern) states)))
  ::choice   (apply set/union (map (partial matches states) children))
  ::optional (reduce #(into %1 (matches %1 %2)) states children)
  ::required (reduce matches states children)
  ::repeat   (let [new-states (matches states (first children))]
               (if (= states new-states)
                 states                 
                 (into new-states (matches new-states pattern)))))

;;

(defn accumulate-option-arg [acc [_ option arg]]
  (if (and acc (not (string? option))
           (= (:takes-arg option) (not (nil? arg))))
    (push-acc acc (dissoc option :long-re) arg)))

(defn  accumulate-option-default [acc [option val]]
  (let [default (:default-value option)
        new-acc (if (and acc (:takes-arg option) (or (nil? val) (= [] val)))
                  (assoc acc option (if (and default (= [] val) (not (vector? default))) [default] default))
                  acc)]
    (case (new-acc option)
      (0 [] false nil) (dissoc new-acc option)
      new-acc)))

(defn parse-command-line [{:keys [acc pattern options]} argv]
  (let [options-acc    (into {} (filter (comp not string? key) acc))
        [before after] (s/split (or (s/join " " argv) "") #" -- ") 
        tokens         (concat (tokenize-command-line (or before "") options) 
                               (map #(vector ::word %) (if (seq after) (s/split after " ")))) 
        options-acc    (reduce accumulate-option-arg     options-acc (filter #(isa? (first %) ::option) tokens))
        remaining      (reduce accumulate-option-default options-acc options-acc)]
    (when remaining
      (let [all-matches (matches #{[acc remaining (map second (filter #(isa? (first %) ::word) tokens))]} pattern)
            match (ffirst (filter #(and (empty? (% 1)) (empty? (% 2))) all-matches))]
        (when match
          (into {} (map #(if (string? (key %)) % (vector (option-string (key %)) (val %))) match)))))))

;;;;

(defn parsefn [doc]
  (let [parsed-doc (try+ (parse-doc doc)
                     (catch [:type ::error] error
                       (err-print error)))]
    (if (string? parsed-doc)
      (constantly nil)
      (fn [argv]
        (parse-command-line parsed-doc argv)))))


