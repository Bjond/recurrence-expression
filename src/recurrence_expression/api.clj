(ns recurrence-expression.api
  (:require clojure.core)
  (:require [recurrence-expression.core :refer :all])
  (:require [recurrence-expression.data :refer :all])
  (:gen-class :name com.bjondinc.RecurrenceExpression
              :prefix method-
              :main false
              :methods [#^{:static true}
                        [nextTime [org.joda.time.DateTime String] org.joda.time.DateTime]]))

(defn method-nextTime
  [current-time pattern-string]
  (let [pattern (from-json pattern-string)]
    (next-n-times current-time pattern)))
