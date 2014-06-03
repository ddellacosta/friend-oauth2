(defproject friend-oauth2 "0.1.2-SNAPSHOT"
  :description "OAuth2 workflow for Friend (https://github.com/cemerick/friend). (Bug reports/contributions welcome!)"
  :url "https://github.com/ddellacosta/friend-oauth2"
  :license {:name "MIT License"
            :url "http://dd.mit-license.org"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.cemerick/friend "0.2.0"];; :exclusions [ring/ring-core slingshot]]
                 [ring "1.2.0"]
                 [ring/ring-codec "1.0.0"]
                 [clj-http "0.7.7"]
                 [cheshire "5.2.0"]
                 [crypto-random "1.1.0"]]

  :plugins [[lein-midje "3.1.2"]
            [codox "0.6.6"]]

  :profiles {:dev
             {:dependencies [[ring-mock "0.1.5"]
                             [midje "1.5.1"];; :exclusions [org.clojure/core.incubator joda-time]]
                             [com.cemerick/url "0.1.0"];; :exclusions [org.clojure/core.incubator]]
                             [compojure "1.1.5"]]}})
