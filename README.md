# docopt.clj

Clojure implementation of the [docopt](http://docopt.org/) language, version 0.6, 
under a [MIT license](http://github.com/docopt/docopt.clj/blob/master/LICENSE).

## Usage

Add `[docopt "0.6.0"]` to your dependencies in `project.clj`, and import `docopt.core` in your clojure code. 
This namespace contains the public API:

- A macro `docopt` wich takes up to two arguments, a docstring and an `args` sequence.  
The docstring is optional; if omitted, the macro will try to use the docstring of `-main`. The docstring is parsed 
at compile-time, and the `args` are matched at run-time. The `args` should be a sequence of command-line arguments like
 those passed to `-main` or `public static void main(String[] args);`.

- A function `match`, basically the run-time equivalent of the `docopt` macro, mainly used for testing. 
More precisely, the return values of `(match doc args)` and `(docopt doc args)` are identical.

- A function `parse`, which takes a docstring as argument and returns all the information extracted from it.
This function is called by both `docopt` and `match`.

Here's a full-fledged example:
``` clojure
(ns example
  (:use [docopt.core :only [docopt]]) ;; import the docopt macro from docopt.core
  (:gen-class))

(defn #^{:version "Naval Fate, version 1.2.3." 
         :doc "Naval Fate.

Usage:
  naval_fate ship new <name>...
  naval_fate ship <name> move <x> <y> [--speed=<kn>]
  naval_fate ship shoot <x> <y>
  naval_fate mine (set|remove) <x> <y> [--moored|--drifting]
  naval_fate -h | --help
  naval_fate --version

Options:
  -h --help     Show this screen.
  --version     Show version.
  --speed=<kn>  Speed in knots [default: 10].
  --moored      Moored (anchored) mine.
  --drifting    Drifting mine."}
  -main [& args]
  (let [arg-map (docopt args)]
    (cond 
      (or (nil? arg-map)
          (arg-map "--help")) (println (:doc     (meta #'-main)))
      (arg-map "--version")   (println (:version (meta #'-main)))
      (arg-map "mine")        (println (if (arg-map "set") "Set" "Remove") 
                                       (cond 
                                         (arg-map "--moored")   "moored" 
                                         (arg-map "--drifting") "drifting")
                                       "mine at (" (arg-map "<x>") ", " (arg-map "<y>") ").")
      (arg-map "new")         (println "Created new" 
                                       (let [[name & more-names :as names] (arg-map "<name>")]
                                         (if (seq more-names) 
                                           (str "ships " (clojure.string/join ", " names))
                                           (str "ship " name)))
                                       ".")
      (arg-map "shoot")       (println "Shoot at (" (arg-map "<x>") "," (arg-map "<y>") ").")
      (arg-map "move")        (println "Move" (first (arg-map "<name>")) 
                                       "to (" (arg-map "<x>") "," (arg-map "<y>")
                                       (if-let [speed (arg-map "--speed")]
                                         (str ") at " speed " knots.")
                                         ")."))
      true                    (throw (Exception. "This case never occurs, thanks docopt!")))))
```

## Tests

Run `lein test` to validate all language-agnostic tests in `testcases.docopt`. As of now, test coverage is complete.

## To-do list

- Reduce the number of LOCs further while improving clarity at the same time. 
A lot of minor changes were accumulated through getting all the tests to pass. 
I'm also a bit new to Clojure, so there's certainly room for improvement.
