# docopt.clj

Clojure implementation of the [docopt](http://docopt.org/) language, version 0.6, 
under a [MIT license](http://github.com/docopt/docopt.clj/blob/master/LICENSE).

## Usage

Add `[docopt "0.6.1"]` to your dependencies in `project.clj`, and import `docopt.core` in your clojure code. 
This namespace contains the public API:

- A macro `docopt` wich takes up to two arguments, a docstring and an `args` sequence.  
The docstring is optional; if omitted, the macro will try to use the docstring of `-main`. The docstring is parsed 
at compile-time, and the `args` are matched at run-time. The `args` should be a sequence of command-line arguments like
 those passed to `-main` or `public static void main(String[] args);`.

- A function `-docopt` which is the run-time equivalent of the `docopt` macro provided for Java interoperability.

- A function `parse` which takes a docstring as argument and returns all the information extracted from it.
This function is called by both `docopt` and `-docopt`.

## Example - Clojure

``` clojure
(ns example.core
  (:use [docopt.core :only [docopt]]) ;; import the docopt macro from docopt.core
  (:gen-class))

(defn #^{:doc "Naval Fate.

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
  --drifting    Drifting mine."
:version "Naval Fate, version 1.2.3." }
  -main [& args]
  (let [arg-map (docopt args)] ;; with only one argument, docopt parses -main's docstring.
    (cond 
      (or (nil? arg-map)
          (arg-map "--help")) (println (:doc     (meta #'-main)))
      (arg-map "--version")   (println (:version (meta #'-main)))
      (arg-map "mine")        (println (if (arg-map "set") "Set" "Remove") 
                                       (cond 
                                         (arg-map "--moored")   "moored" 
                                         (arg-map "--drifting") "drifting")
                                       "mine at (" (arg-map "<x>") ", " (arg-map "<y>") ").")
      (arg-map "new")         (println "Create new" 
                                       (let [[name & more-names :as names] (arg-map "<name>")]
                                         (if (seq more-names) 
                                           (str "ships " (clojure.string/join ", " names))
                                           (str "ship " name)))
                                       ".")
      (arg-map "shoot")       (println "Shoot at (" (arg-map "<x>") "," (arg-map "<y>") ").")
      (arg-map "move")        (println "Move" (first (arg-map "<name>")) 
                                       "to (" (arg-map "<x>") "," (arg-map "<y>")
                                       (if-let [speed (arg-map "--speed")]
                                         (str " ) at " speed " knots.")
                                         " )."))
      true                    (throw (Exception. "This ought to never happen.")))))
```

## Example - Java interoperability

Add the [clojars](https://clojars.org) repository to Maven.

``` xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>
```

Tell Maven about docopt.clj

``` xml
<dependency>
  <groupId>docopt</groupId>
  <artifactId>docopt</artifactId>
  <version>0.6.1</version>
</dependency>
```

Import `org.docopt.clj` in your code and call the static method `clj.docopt(String, String[]);`

``` java
import java.util.HashMap;
import org.docopt.clj;

public class Main {
  
  public static void main(String[] args) {
  
    String docstring = "Usage: prog [options]\n\nOptions:\n-h, --help  Print help.";
    HashMap<String, Object> result = clj.docopt(docstring, args);
    
    if (result.get("--help").toString() == "true") {
      System.out.println(docstring);
    }
    else {
      System.out.println("You don't need help.");
    }
  }
}
```

## Tests

Run `lein test` to validate all language-agnostic tests in `testcases.docopt`. As of now, test coverage is complete.
