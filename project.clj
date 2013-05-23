(defproject friend-oauth2 "0.0.3"
  :description "OAuth2 workflow for Friend (https://github.com/cemerick/friend). (Bug reports/contributions welcome!)"
  :url "https://github.com/ddellacosta/friend-oauth2"
  :license {:name "MIT License"
            :url "http://dd.mit-license.org"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.cemerick/friend "0.1.5" :exclusions [[ring/ring-core] slingshot]]
                 [ring "1.2.0-beta2"]
                 [ring/ring-codec "1.0.0"]
                 [clj-http "0.6.5" :exclusions [org.apache.httpcomponents/httpclient slingshot]]
                 [cheshire "5.0.2"]]
  :plugins [[lein-ring "0.8.3" :exclusions [org.clojure/clojure]]
            [lein-midje "3.0.0"]
            [codox "0.6.4"]]
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]
                        [midje "1.5.0" :exclusions [org.clojure/core.incubator joda-time]]]}})
