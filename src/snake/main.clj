;;shim class for launching main.
(ns snake.main
  (:gen-class :main true))

;;This is the main entry point for our jvm-based
;;sketch.
(defn -main [& args]
  ;;clojure.set isn't imported by default, causing errors when
  ;;aot-compiling in some places.
  ;(require 'clojure.set)
  (binding [*ns* *ns*]
    (require 'snake.core)
    (in-ns 'snake.core)
    ((resolve 'snake.core/play) :exit? true)))
