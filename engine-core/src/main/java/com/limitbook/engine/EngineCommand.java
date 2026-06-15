package com.limitbook.engine;

import com.limitbook.engine.model.Order;

/** A command handed to the single-writer engine over its bounded queue. */
public sealed interface EngineCommand
        permits EngineCommand.Submit, EngineCommand.Cancel, EngineCommand.Amend {

    record Submit(Order order) implements EngineCommand {}

    record Cancel(long orderId) implements EngineCommand {}

    record Amend(long orderId, long newPrice, long newQuantity) implements EngineCommand {}
}
