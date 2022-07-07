(ns image-downloader.core
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as html]
            [clojure.java.io :as io])
  (:gen-class)
  (:import (java.io File Reader)
           (java.net URL)
           (java.util Properties)))

(defn- fetch-image!
  "makes an HTTP request and fetches the binary object"
  [url]
  (let [req (client/get url {:as :byte-array :throw-exceptions false})]
    (if (= (:status req) 200)
      (:body req))))

(defn- save-image!
  "download and store the image on the disk"
  [url dir name]
  (let [b (.exists (io/file dir name))]
    (if-not b (some-> (fetch-image! url) (io/copy (io/file dir name))))))

(defn- get-description!
  [url]
  (let [header (html/select-nodes* (html/html-resource (URL. url)) [(html/attr= :name "description")])
        descr (get-in (first header) [:attrs :content])]
    (.toLowerCase (str/escape (str/trim (first (str/split descr #" - "))) {\  "-" \/ "_" \\ "_" "#" "" \: "_" \. "" \" "" \# "" \? ""}))))

(defn- load-properties!
  [filename]
  (with-open [^Reader reader (clojure.java.io/reader filename)]
    (let [props (Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [(keyword k) (read-string v)]))))
  )

(defn fetch-urls-for-images
  "Parsing HTML code from the provided URL in parallel"
  [url dir]
  (let [attrs (html/select (html/html-resource (URL. url)) [:div.fileText :a])
        dir-path (str dir (get-description! url) "/")]
    (.mkdir (File. dir-path))
    (pmap #(save-image! (str "https:" (get-in % [:attrs :href])) dir-path (first (get % :content))) attrs)))

(defn parse-html-from-url
  "Parsing HTML code from the provided URL"
  ([url] (parse-html-from-url url "~/Documents/"))
  ([url dir]
   (let [attrs (html/select (html/html-resource (URL. url)) [:div.fileText :a])
         dir-path (str dir (get-description! url) "/")]
     (.mkdir (File. dir-path))
     (map #(save-image! (str "https:" (get-in % [:attrs :href])) dir-path (first (get % :content))) attrs))))

(defn -main
  [file & args]
   (let [props (load-properties! file)]
     (time (doall (pmap #(fetch-urls-for-images % (get props :base-folder)) (get props :download-urls)))))
  )