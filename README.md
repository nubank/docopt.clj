# docopt.clj

Clojure implementation of the [docopt](http://docopt.org/) description language.

Forked from [@signalpillar](https://github.com/signalpillar)'s [fork](https://github.com/signalpillar/docopt.clj) of docopt, [originally](https://github.com/docopt/docopt.clj/) by [@postamar](https://github.com/postamar).

## Usage

### babashka

In order to add `docopt.clj` to the classpath, you can either

- Use an environment variable
  ``` bash
  cd babashka
  export BABASHKA_CLASSPATH=$(clojure -Spath -Sdeps '{:deps {nubank/docopt {:git/url "https://github.com/nubank/docopt.clj" :sha "0c5b1c7645901affcda115fc280744f5f8dc802a"}}}')
  ./naval_fate_env.clj
  ```

- Dynamically include it with `(require '[babashka.classpath :refer [add-classpath])`
  ``` bash
  cd babashka
  ./naval_fate_dynamic.clj
  ```
  
`babashka/common.clj` contains an example of how to call docopt's entrypoint function.

In both cases, the output should be

``` bash
$ ./naval_fate_env.clj ship Unicorn move $'20°37\'42.0"N' $'70°52\'25.0"W' 
{"--drifting" false,
 "--help" false,
 "--moored" false,
 "--speed" "10",
 "--version" false,
 "<lat>" "20°37'42.0\"N",
 "<long>" "70°52'25.0\"W",
 "<name>" "Unicorn",
 "mine" false,
 "move" true,
 "new" false,
 "remove" false,
 "set" false,
 "ship" true}
```

### tools.deps

Save the following script as `test-script` and make it executable with `chmod +x`:

``` clojure
#!/bin/sh
#_(
  DEPS='
   {:deps {docopt
             {:git/url "https://github.com/nubank/docopt.clj"
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

## License

[MIT license](LICENSE).
