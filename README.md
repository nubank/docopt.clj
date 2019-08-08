# docopt.clj

Clojure implementation of the [docopt](http://docopt.org/) language, version 0.6, under a [MIT license](http://github.com/docopt/docopt.clj/blob/master/LICENSE).

## Usage

Save the following script and make it executable with `chmod +x`:

``` clojure
#!/bin/sh
#_(
  DEPS='
   {:deps {docopt
             {:git/url "https://github.com/FelipeCortez/docopt.clj"
               :sha    "5191b7ef3ef3f80b4e19c1cd4800333c7ad2513f"}}}
   '

  OPTS='
  -J-Xms256m -J-Xmx256m
  -J-client
  -J-Dclojure.spec.skip-macros=true
  '

  exec clojure $OPTS -Sdeps "$DEPS" -i "$0" -m docopt.example "$@"
)

(ns docopt.example
  (:require [docopt.core :as docopt]))

(def usage "Test application.

Usage: test-script [options]

Options:
  --an-arg <something>  An argument")
(defn -main [& args]
  (docopt/docopt usage args
                 (fn [arg-map]
                   (println arg-map)
                   (println (arg-map "--an-arg")))))
```

```bash
$ chmod +x test-script
$ ./test-script --an-arg test
{--an-arg test}
test
$ ./test-script # displays the help text
Test application.

Usage: testapp [options]

Options:
  --an-arg <something>  An argument
```

## Tests

Run `lein test` to validate all tests.
The tests are automatically downloaded from the language-agnostic
`testcases.docopt` file in the reference implementation, master branch commit 
[511d1c57b5](https://github.com/docopt/docopt/tree/511d1c57b59cd2ed663a9f9e181b5160ce97e728).
Please feel free to (re)open an issue in case this implementation falls behind.

