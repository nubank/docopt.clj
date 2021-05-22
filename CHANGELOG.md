# Changelog

## 0.6.1-fix7
- While parsing argvs with `--`, returns all matches if no matches with
  `--` is found

## 0.6.1-fix6
- Use `clojure.string/escape` instead of `clojure.string/replace` to convert
  the characters to placeholders. It still uses `clojure.string/replace` to
  convert the placeholders back to the original characters, but using
  `clojure.string/escape` should be (slightly) faster and allows customization
  of the characters that needs conversion. Use `binding` on
  `docopt.match/*sep-table*` if you need to customize this behavior
- Downgrade Clojure to version 1.8.0, the newest version that still run
  the test suite succesfully

## 0.6.1-fix5
- Fix parsing of short and long opts with spaces/tabs/newlines, e.g.
  `foo -f "file with spaces.txt"`

## 0.6.1-fix4 - BROKEN RELEASE, DO NOT USE IT
- Substitute `\u00A0` to `__DOCOPT_SPACE_SEP__` as the placeholder character
  in the space workaround to avoid triggering it by mistake
- Apply the same space workaround for newlines and tabs too

## 0.6.1-fix3 - BROKEN RELEASE, DO NOT USE IT
- Workaround issue while parsing space in arguments, e.g.
  `foo "file with spaces.txt"`

## 0.6.1-fix2
- Update deps
- Lint fixes
- Workaround issue while parsing `--`
- Add error metadata using `ex-info`
- Improve documentation

## 0.6.1-fix1 - Not available on Clojars
- Forked from @signalpillar fork
- Add modifications to make it work with Babashka
