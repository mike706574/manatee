(defproject manatee "0.1.1"
  :license {:name "Eclipse Public License 1.0"
            :url "http://opensource.org/licenses/eclipse-1.0.php"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org/jaudiotagger "2.0.3"]]
  :resource-paths ["resources" "test-resources"]
  :main manatee.core
  :aot :all)
