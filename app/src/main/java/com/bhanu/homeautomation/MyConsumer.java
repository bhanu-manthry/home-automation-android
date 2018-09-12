package com.bhanu.homeautomation;

/**
 * Created by bhanu on 8/2/18.
 */

public interface MyConsumer<T> {
    void accept(T t);
}

