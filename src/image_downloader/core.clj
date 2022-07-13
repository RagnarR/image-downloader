(ns image-downloader.core
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as html]
            [clojure.tools.cli :as ctools]
            [clojure.java.io :as io])
  (:gen-class)
  (:import (java.io File Reader)
           (java.net URL)
           (java.util Properties)))

(defn- fetch-image!
  "makes an HTTP request and fetches the binary object"
  [url]
  (let [req (client/get url {:as :byte-array :throw-exceptions true})]
    (if (= (:status req) 200)
      (:body req))))

(defn- save-image!
  "download and store the image on the disk"
  [url dir name]
  (let [b (.exists (io/file dir name))]
    (if-not b (some-> (fetch-image! url) (io/copy (io/file dir name))))))

(defn- get-description!
  [url]
  (let [header (html/select-nodes* (html/html-resource url) [(html/attr= :name "description")])
        descr (get-in (first header) [:attrs :content])]
    (.toLowerCase (str/escape (str/trim (first (str/split descr #" - "))) {\  "-" \/ "_" \\ "_" "#" "" \: "_" \. "" \" "" \# "" \? ""}))))

(defn- load-properties!
  [filename]
  (with-open [^Reader reader (clojure.java.io/reader filename)]
    (let [props (Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [(keyword k) (read-string v)]))))
  )

(let [homedir (io/file (System/getProperty "user.home"))
      usersdir (.getParent homedir)]
  (defn home
    "With no arguments, returns the current value of the `user.home` system
     property. If a `user` is passed, returns that user's home directory. It
     is naively assumed to be a directory with the same name as the `user`
     located relative to the parent of the current value of `user.home`."
    ([] homedir)
    ([user] (if (empty? user) homedir (io/file usersdir user)))))

(defn- expand-home!
  "If `path` begins with a tilde (`~`), expand the tilde to the value
  of the `user.home` system property. If the `path` begins with a
  tilde immediately followed by some characters, they are assumed to
  be a username. This is expanded to the path to that user's home
  directory. This is (naively) assumed to be a directory with the same
  name as the user relative to the parent of the current value of
  `user.home`."
  [path]
  (let [path (str path)]
    (if (.startsWith path "~")
      (let [sep (.indexOf path File/separator)]
        (if (neg? sep)
          (home (subs path 1))
          (io/file (home (subs path 1 sep)) (subs path (inc sep)))))
      path)))

(defn fetch-urls-for-images
  "Parsing HTML code from the provided URL in parallel"
  [url dir]
  (let [attrs (html/select (html/html-resource url) [:div.fileText :a])
        dir-path (str dir (get-description! url) "/")]
    (.mkdir (File. dir-path))
    (doall (pmap #(save-image! (str "https:" (get-in % [:attrs :href])) dir-path (first (get % :content))) attrs))))

(defn parse-html-from-url
  "Parsing HTML code from the provided URL"
  ([url] (parse-html-from-url url (str (expand-home! "~/Documents/") "/")))
  ([url dir]
   (let [attrs (html/select (html/html-resource url) [:div.fileText :a])
         dir-path (str dir (get-description! url) "/")]
     (.mkdir (File. dir-path))
     (map #(save-image! (str "https:" (get-in % [:attrs :href])) dir-path (first (get % :content))) attrs))))

(defn -main
  [& args]
  (let [{:keys [options summary]} (ctools/parse-opts args
                                                     [["-h" "--help" "This application used for downloading images from the 4chan threads." :default false]
                                                      ["-l" "--link LINK" "Use one link for download images" :parse-fn #(URL. %)]
                                                      ["-d" "--dir DIRECTORY" "Set output directory" :parse-fn #(str %)]
                                                      ["-p" "--props FILE" "Use properties for downloading images from many links" :parse-fn #(str %)]])]
    (when (:help options)
      (println summary))
    (when (:link options)
      (if (:dir options)
        (time (doall (parse-html-from-url (:link options) (:dir options))))
        (time (doall (parse-html-from-url (:link options))))))
    (when (:props options)
      (let [props (load-properties! (:props options))]
        (time (doall (pmap #(fetch-urls-for-images (URL. %) (get props :base-folder)) (get props :download-urls))))))
    ))