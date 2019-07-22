(ns spectacular.coerce
  (:require [stigmergy.tily :as util]))

(defn convert-if-valid-number [a-str convert-fn]
  (if-let [invalid-char (re-find #"[^0-9.]" a-str)] 
    (let [msg (str a-str " contains invalid chacter '" invalid-char "'")]
      #?(:cljs (throw (js/Error. msg)))
      #?(:clj (throw (Exception. msg))))
    (convert-fn a-str)))


(defn str->int [a-str]
  (if (string? a-str)
    #?(:clj  (convert-if-valid-number (str a-str) (fn [a]
                                                    (java.lang.Long/parseLong a))))
    #?(:cljs (convert-if-valid-number (str a-str) js/parseInt))
    a-str))

(defn str->float [a-str]
  (if (string? a-str)
    #?(:clj (convert-if-valid-number (str a-str) #(java.lang.Float/parseFloat %)))
    #?(:cljs (convert-if-valid-number (str a-str) js/parseFloat))
    a-str))

(defn str->bigdec [a-str]
  #?(:clj (bigdec a-str))
  #?(:cljs (str->float a-str)))

(defn str->bigint [a-str]
  #?(:clj (bigint a-str))
  #?(:cljs (str->int a-str)))


(defn ->enum [e vector-of-enums]
  (let [kw (keyword e)]
    (if (util/some-in? kw vector-of-enums)
      kw
      (let [msg (str kw " not a member of " vector-of-enums)]
        #?(:clj (throw (Exception. msg)))
        #?(:cljs (throw (js/Error. msg)))))))
