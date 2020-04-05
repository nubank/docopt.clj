(def usage "Naval Fate.

Usage:
  naval_fate ship new <name>
  naval_fate ship <name> move <lat> <long> [--speed=<kn>]
  naval_fate mine (set|remove) <lat> <long> [--moored|--drifting]
  naval_fate -h | --help
  naval_fate --version

Options:
  -h --help     Show this screen.
  --version     Show version.
  --speed=<kn>  Speed in knots [default: 10].
  --moored      Moored (anchored) mine.
  --drifting    Drifting mine.")

(docopt/docopt usage
               *command-line-args*
               (fn [arg-map] (clojure.pprint/pprint arg-map)))
