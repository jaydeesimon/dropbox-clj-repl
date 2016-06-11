(defproject com.jaydeesimon/dropbox-repl "0.1.0"
  :description "Dropbox Clojure REPL"
  :url "https://github.com/jaydeesimon/dropbox-clj-repl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-http "2.2.0"]
                 [cheshire "5.6.1"]
                 [environ "1.0.3"]
                 [clj-time "0.11.0"]]
  :plugins [[lein-environ "1.0.3"]]
  :main ^:skip-aot dropbox-repl.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
