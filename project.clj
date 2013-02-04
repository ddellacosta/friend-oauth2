(defproject friend-oauth2 "0.0.2"
  :description "OAuth2 workflow for Friend (https://github.com/cemerick/friend). (Bug reports/contributions welcome!)"
  :url "https://github.com/ddellacosta/friend-oauth2"
  :license {:name "MIT License"
            :url "http://dd.mit-license.org"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.cemerick/friend "0.1.3"]
                 [ring "1.1.6"]
                 [clj-http "0.5.3"]
                 [cheshire "4.0.2"]]
  :plugins [[lein-ring "0.7.5"]
            [lein-midje "2.0.0-SNAPSHOT"]
            [codox "0.6.1"]]
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]
                        [midje "1.5.0-SNAPSHOT"]
                        [com.stuartsierra/lazytest "1.2.3"]]}}
  :repositories {"stuart" "http://stuartsierra.com/maven2"})  ;; For lazytest
