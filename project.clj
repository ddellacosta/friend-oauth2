(defproject friend-oauth2 "0.1.2"
  :description "OAuth2 workflow for Friend (https://github.com/cemerick/friend). (Bug reports/contributions welcome!)"
  :url "https://github.com/ddellacosta/friend-oauth2"
  :license {:name "MIT License"
            :url "http://dd.mit-license.org"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.cemerick/friend "0.2.0" ;; :exclusions [ring/ring-core slingshot]]
                  :exclusions [org.apache.httpcomponents/httpclient]]
                 [ring "1.3.2"]
                 [ring/ring-codec "1.0.0"]
                 [clj-http "1.0.1"]
                 [cheshire "5.3.1"]
                 [crypto-random "1.2.0"]]

  :plugins [[lein-midje "3.1.2"]
            [codox "0.6.6"]]

  :profiles {:dev
             {:dependencies [[ring-mock "0.1.5"]
                             [org.clojure/tools.nrepl "0.2.5"]
                             [midje "1.5.1"] ;; :exclusions [org.clojure/core.incubator joda-time]]
                             [com.cemerick/url "0.1.0"] ;; :exclusions [org.clojure/core.incubator]]
                             [compojure "1.1.5"]]}})
