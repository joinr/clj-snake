(defproject snake "0.1.0-SNAPSHOT"
  :description "The classic Nokia game wirtten in Clojure."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [quil "2.7.1"]]
  :main snake.core
  :plugins [[io.taylorwood/lein-native-image "0.3.0"]]            
  ;:aot :all
  :native-image {:name     "clj-snake"
                 :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
                 :opts     ["--report-unsupported-elements-at-runtime"
                            "--initialize-at-build-time"
                            "--allow-incomplete-classpath"
                            ;;avoid spawning build server
                            "--no-server"]})
