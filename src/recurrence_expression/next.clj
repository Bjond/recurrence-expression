;; Copyright (c) 2015--2016 Bjönd, Inc.
;;
;; This file is part of Recurrence Expression.
;;
;; Recurrence Expression is free software: you can redistribute it
;; and/or modify it under the terms of the GNU Lesser General Public
;; License as published by the Free Software Foundation, either
;; version 3 of the License, or (at your option) any later version.
;;
;; Recurrence Expression is distributed in the hope that it will be
;; useful, but WITHOUT ANY WARRANTY; without even the implied warranty
;; of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with Recurrence Expression.  If not, see
;; <http://www.gnu.org/licenses/>.

(ns recurrence-expression.next
  (:require [clojure.pprint :as pp]
            [clj-time.core :as t]
            [recurrence-expression.instant :refer :all]))

(defn next-nth-week [base-time n day-of-week]
  (loop [nth nil
         base-t base-time]
    (if nth
      nth
      (let [year (t/year base-t)
            month (t/month base-t)
            d (nth-week year month n day-of-week)
            day (t/date-time (t/year d)
                        (t/month d)
                        (t/day d)
                        (t/hour base-t)
                        (t/minute base-t)
                        (t/second base-t))]
        (recur
         (if (and day (or (= day base-time) (t/after? day base-time)))
           day
           nil)
         (t/plus base-t (t/months 1)))))))

(defn next-day-of-week [base-time day-of-week]
  (let [base-day-of-week (t/day-of-week base-time)]
    (if (= base-day-of-week day-of-week)
      base-time
      (let [this-many (if (< base-day-of-week day-of-week)
                        (- base-day-of-week day-of-week)
                        (+ (- 7 base-day-of-week) day-of-week))]
        (t/plus base-time (t/days this-many))))))

(defn next-x-of-week [base-time week-pattern]
  (let [day-of-week (value-or-default (get week-pattern :dayOfWeek) 1)
        week-of-month (value-or-default (get week-pattern :weekOfMonth) nil)]
    (if week-of-month
      (next-nth-week base-time week-of-month day-of-week)
      (next-day-of-week base-time day-of-week))))

(defmulti next-day-of-month
  (fn [base-time day-of-month]
    (cond
     (= :last day-of-month) :last
     (and (number? day-of-month)
          (< 0 day-of-month)
          (< day-of-month 32)) :number
     :default (throw (IllegalArgumentException.
                      (str "Invalid day-of-month: " day-of-month))))))

(defmethod next-day-of-month :last [base-time day-of-month]
  (let [next-day (t/plus base-time (t/days 1))]
    (t/last-day-of-the-month (t/year next-day) (t/month next-day))))

(defmethod next-day-of-month :number [base-time day-of-month]
  (if (> day-of-month 31)
    (throw (IllegalArgumentException.
            (str "Invalid day-of-month: " day-of-month))))
  (loop [the-day nil
         base-t base-time]
    (if the-day
      the-day
      (let [base-day (t/day base-t)
            next-month (t/plus base-t (t/months 1))]
        (recur (if (<= base-day day-of-month)
                 (let [diff (- day-of-month base-day)]
                   (safe-create-date (t/year base-t)
                                     (t/month base-t)
                                     (+ base-day diff)
                                     (t/hour base-t)
                                     (t/minute base-t)
                                     (t/second base-t)))
                 (safe-create-date (t/year next-month)
                                   (t/month next-month)
                                   day-of-month
                                   (t/hour next-month)
                                   (t/minute next-month)
                                   (t/second next-month)))
               next-month)))))

(defn next-day-instant [base-time day-pattern]
  (cond
   (or (number? day-pattern)
       (= day-pattern :last)) (next-day-of-month base-time day-pattern)
   (map? day-pattern) (next-x-of-week base-time day-pattern)
   :else (throw (IllegalArgumentException.
                 (str "Invalid day-pattern: " day-pattern)))))

(defmulti next-unit-value (fn [time-unit-key base-time instant-pattern]
                         time-unit-key))

(defmethod next-unit-value :year [time-unit-key base-time instant-pattern]
  (t/date-time (value-or-default (get instant-pattern time-unit-key) (t/year base-time))
               (t/month base-time)
               (t/day base-time)
               (t/hour base-time)
               (t/minute base-time)
               (t/second base-time)))

(defmethod next-unit-value :month [time-unit-key base-time instant-pattern]
  (let [unit-value (value-or-default (get instant-pattern time-unit-key) 1)
        rollover (> (t/month base-time) unit-value)
        t (if rollover
            (t/plus base-time (t/years 1))
            base-time)]
    (t/date-time (t/year t)
                 unit-value
                 (t/day t)
                 (t/hour t)
                 (t/minute t)
                 (t/second t))))

(defmethod next-unit-value :day [time-unit-key base-time instant-pattern]
  (if (contains? instant-pattern time-unit-key)
    (next-day-instant base-time (get instant-pattern time-unit-key))
    (let [current-day (t/day base-time)
          roll-forward (> current-day 1)]
      (if roll-forward (let [t (t/plus base-time (t/months 1))]
                        (t/date-time (t/year t)
                                     (t/month t)
                                     1
                                     (t/hour t)
                                     (t/minute t)
                                     (t/second t)))
          base-time))))

(defmethod next-unit-value :hour [time-unit-key base-time instant-pattern]
  (let [unit-value (value-or-default (get instant-pattern time-unit-key) 0)
        rollover (> (t/hour base-time) unit-value)
        t (if rollover
            (t/plus base-time (t/days 1))
            base-time)]
    (t/date-time (t/year t)
                 (t/month t)
                 (t/day t)
                 unit-value
                 (t/minute t)
                 (t/second t))))

(defmethod next-unit-value :minute [time-unit-key base-time instant-pattern]
  (let [unit-value (value-or-default (get instant-pattern time-unit-key) 0)
        rollover (> (t/minute base-time) unit-value)
        t (if rollover
            (t/plus base-time (t/hours 1))
            base-time)]
    (t/date-time (t/year t)
                 (t/month t)
                 (t/day t)
                 (t/hour t)
                 unit-value
                 (t/second t))))

(defmethod next-unit-value :second [time-unit-key base-time instant-pattern]
  (let [unit-value (value-or-default (get instant-pattern time-unit-key) 0)
        rollover (> (t/second base-time) unit-value)
        t (if rollover (t/plus base-time (t/minutes 1))
              base-time)]
    (t/date-time (t/year t)
                 (t/month t)
                 (t/day t)
                 (t/hour t)
                 (t/minute t)
                 unit-value)))
  
(defmethod next-unit-value :default [time-unit-key base-time instant-pattern]
  (throw (IllegalArgumentException. (str "Invalid time unit: " time-unit-key))))

(defn next-instant [base-time instant-pattern]
  (let [highest-order-property (highest-order-property-defined instant-pattern)
        highest-order-property-index (get instant-property-index-map highest-order-property)]
    (loop [time base-time
           properties instant-property-list]
      (if (or (empty? properties) (< highest-order-property-index
                                     (get instant-property-index-map (first properties))))
        time
        (let [property (first properties)
              next-time (next-unit-value property time instant-pattern)]
          (recur next-time
                 (rest properties)))))))
