(defproject image-downloader "0.1.1-with-property"
  :description "Simple project for downloading images from image boards."
  :url "https://github.com/RagnarR/image-downloader"
  :license {:name "MIT License"
            :url "https://spdx.org/licenses/MIT.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [enlive "1.1.6"]
                 [clj-http "3.12.3"]]
  :main image-downloader.core
  :repl-options {:init-ns image-downloader.core})
