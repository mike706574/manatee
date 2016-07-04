(ns manatee.core
  (:gen-class)
  (:refer-clojure :exclude [replace])
  (:require [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [clojure.string :refer [lower-case
                                    upper-case
                                    replace
                                    trim
                                    split]])
  (:import [org.jaudiotagger.tag FieldKey]
           [org.jaudiotagger.audio AudioFileIO]
           [java.util.logging Logger Level]))

(defn FieldKey->key
  [fk]
  (-> (.name fk)
      (lower-case)
      (replace #"_" "-")
      (keyword)))

(defn key->FieldKey
  [k]
  (-> (name k)
      (upper-case)
      (replace #"-" "_")
      (FieldKey/valueOf)))

(def all-fields (map FieldKey->key (.getEnumConstants FieldKey)))
(def killer (into {} (map #(vector % nil) all-fields)))

(defn read-tag
  [f]
  (let [tag (.getTag (AudioFileIO/read f))
        value (fn [fk]
                (let [v (.getFirst tag fk)]
                  (when-not (empty? v)
                    (vector (FieldKey->key fk) v))))
        fields (.getEnumConstants FieldKey)]
    (into {} (remove nil? (map value fields)))))

(defn write-tag!
  [file m]
  (let [audio-file (AudioFileIO/read file)]
    (when-let [tag (.getTag audio-file)]
      (doseq [[k v] m]
        (let [field-key (key->FieldKey k)]
          (if v
            (.setField tag field-key v)
            (.deleteField tag field-key))))
      (.commit audio-file)
      (read-tag file))))

(defn prompt
  [label]
  (print (str label ": "))
  (flush)
  (read-line))

(defn ok? []
  (print "OK? ")
  (flush)
  (contains? #{"y" "Y" "yes" "YES"} (read-line)))

(defn apply-pattern
  [pattern name]
  (second (re-find (re-matcher (re-pattern pattern) name))))

(defn try-pattern
  [pattern name]
  (try
    (apply-pattern pattern name)
    (catch Exception e (.getName (.getClass e)))))

(defn assoc-title
  [file pattern]
  (assoc file :title (apply-pattern pattern (:name file))))

(defn print-application
  [{:keys [track name]} pattern]
  (println (str track " " name " ->"))
  (println (str "  " (try-pattern pattern name))))

(defn print-file
  [{:keys [track name]}]
  (println (str track " " name)))

;; macro?
(defn parse-mode
  [mode]
  (case (lower-case mode)
    "s" :single
    "single" :single
    "a" :all
    "all" :all
    nil))

(defn parse-single-mode
  [single-mode]
  (case (lower-case single-mode)
    "m" :manual
    "manual" :manual
    "p" :pattern
    "pattern" :pattern
    nil))

(defn resolve-title
  [file]
  (print-file file)
  (loop [single-mode (prompt "Single Mode (manual or pattern)")]
    (case (parse-single-mode single-mode)
      :manual (assoc file :title (prompt "Title"))
      :pattern (loop [pattern (prompt "Pattern")]
                 (print-application file pattern)
                 (if (ok?)
                   (assoc-title file pattern)
                   (recur (prompt "Pattern"))))
      (do (println "I don't know what that means.")
          (recur (prompt "Single Mode (manual or pattern)"))))))

(defn resolve-titles
  [files]
  (doseq [file files] (print-file file))
  (loop [mode (prompt "Mode (single or all)")]
    (case (parse-mode mode)
      :single (for [file files] (resolve-title file))
      :all (loop [pattern (prompt "Pattern")]
             (doseq [file files] (print-application file pattern))
             (if (ok?)
               (map assoc-title files)
               (recur (prompt "Pattern"))))
      (do (println "I don't know what that means.")
          (recur (prompt "Mode (single or all)"))))))

(defn split-by-extension
  [path]
  (let [base (.getName (io/file path))
        i(.lastIndexOf base ".")]
    (if (pos? i)
      [(subs base 0 i) (subs base i)]
      [base nil])))

(defn split-file
  [file]
  (let [[name extension] (split-by-extension (.getName file))
        [number name] (split name #"-" 2)]
    {:file file
     :track number
     :parent (.getParent file)
     :name (trim name)
     :extension extension}))

(defn -main
  [& [artist album path]]
  (try
    (.setLevel (Logger/getLogger "org.jaudiotagger") Level/OFF)
    (let [files (resolve-titles (map split-file (filter #(.isFile %) (file-seq (io/file path)))))
          track-total (str (count files))]
      (doseq [{:keys [title file parent extension track]} files]
        (let [path (str parent "/" track " " title extension)]
          (write-tag! file (merge killer {:artist artist
                                          :album album
                                          :title title
                                          :track track
                                          :track-total track-total}))
          (.renameTo file (io/file path))))
      (System/exit 0))
    (catch Exception e
      (println "Processing failed.")
      (.printStackTrace e)
      (System/exit 1))))
