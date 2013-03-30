# docopt.clj

Clojure implementation of the `docopt` language. 
`http://docopt.org/`

## Usage

The `docopt.core` namespace contains the public API:
- `docopt` is a macro which takes two arguments, a docstring and an `args` array, as in `public static void main(String[] args)`. The docstring is parsed at compile-time, while the args are matched at run-time.
- `match` is the run-time equivalent of `docopt`.
- `parse` takes a docstring as argument and returns a map containing all the information extracted from it.

## Tests

Run `lein test` to validate all language-agnostic tests in `testcases.docopt`. As of now, test coverage is complete.

## Further work

- Reduce the number of LOCs further while improving clarity at the same time. A lot of minor changes were accumulated through getting all the tests to pass. I'm also a bit new to Clojure, so there's certainly room for improvement.
- Could it be possible to extract the docstring to parse directly from the `:doc` metadata in `-main`?
- Build a jar and put it on `https://clojars.org/`. I'm not quite sure how this all works right now.

## License

Copyright © 2013 Marius Posta, <mariusposta@gmail.com>.

Distributed under the MIT license.
