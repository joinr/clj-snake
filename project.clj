(defproject snake "0.1.0-SNAPSHOT"
  :description "The classic Nokia game wirtten in Clojure."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [quil "3.0.0"]]
  :main snake.main            
  :aot [snake.main]
  :native-image {:name     "clj-snake"
                 :plugins [[io.taylorwood/lein-native-image "0.3.0"]]
                 :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
                 :opts     ["--report-unsupported-elements-at-runtime"
                            "--initialize-at-build-time"
                            "--allow-incomplete-classpath"
                             ;;avoid spawning build server
                            "--no-server"]}
  :profiles {:uberjar {:aot :all}
             :cljs {:dependencies [[org.clojure/clojurescript "1.10.520"]]
                    :plugins [[lein-cljsbuild "1.1.7"]
                              [lein-figwheel "0.5.15"]]
                    :hooks [leiningen.cljsbuild]
                    :clean-targets ^{:protect false} ["resources/public/js"]
                    :cljsbuild
                    {:builds [; development build with figwheel hot swap
                              {:id "development"
                               :source-paths ["src"]
                               :figwheel true
                               :compiler
                               {:main "snake.core"
                                :output-to "resources/public/js/main.js"
                                :output-dir "resources/public/js/development"
                                :asset-path "js/development"}}
                                        ; minified and bundled build for deployment
                              {:id "optimized"
                               :source-paths ["src"]
                               :compiler
                               {:main "snake.core"
                                :output-to "resources/public/js/main.js"
                                :output-dir "resources/public/js/optimized"
                                :asset-path "js/optimized"
                                :optimizations :advanced}}]}}})
  
