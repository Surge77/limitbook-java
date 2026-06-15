package com.limitbook.gateway.web;

import com.limitbook.engine.EngineCommand;
import com.limitbook.engine.EngineService;
import com.limitbook.engine.model.BookSnapshot;
import com.limitbook.engine.model.Order;
import com.limitbook.engine.model.OrderType;
import com.limitbook.engine.model.Side;
import com.limitbook.engine.model.Trade;
import com.limitbook.gateway.web.dto.AmendRequest;
import com.limitbook.gateway.web.dto.CommandAck;
import com.limitbook.gateway.web.dto.OrderRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Write path (REST). Submitting/cancelling/amending only enqueues a command and
 * returns 202 — results flow back over the WebSocket. The read path
 * ({@code /book}, {@code /trades}) serves the lock-free snapshot and tape that the
 * engine thread publishes.
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private static final long DEFAULT_ACCOUNT = 1L;
    private static final int DEFAULT_TRADE_LIMIT = 100;

    private final EngineService engine;
    private final AtomicLong idGenerator;

    public OrderController(EngineService engine, AtomicLong orderIdGenerator) {
        this.engine = engine;
        this.idGenerator = orderIdGenerator;
    }

    @PostMapping("/orders")
    public ResponseEntity<CommandAck> submit(@Valid @RequestBody OrderRequest request) {
        long id = idGenerator.incrementAndGet();
        Order order = toOrder(id, request);
        boolean accepted = engine.offer(new EngineCommand.Submit(order));
        return accepted
                ? ResponseEntity.accepted().body(new CommandAck(true, id))
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new CommandAck(false, id));
    }

    @DeleteMapping("/orders/{id}")
    public ResponseEntity<CommandAck> cancel(@PathVariable long id) {
        boolean accepted = engine.offer(new EngineCommand.Cancel(id));
        return ack(accepted, id);
    }

    @PutMapping("/orders/{id}")
    public ResponseEntity<CommandAck> amend(@PathVariable long id, @Valid @RequestBody AmendRequest request) {
        boolean accepted = engine.offer(new EngineCommand.Amend(id, request.price(), request.quantity()));
        return ack(accepted, id);
    }

    @GetMapping("/book")
    public BookSnapshot book() {
        return engine.latestSnapshot();
    }

    @GetMapping("/trades")
    public List<Trade> trades(@RequestParam(defaultValue = "" + DEFAULT_TRADE_LIMIT) int limit) {
        return engine.recentTrades(limit);
    }

    private ResponseEntity<CommandAck> ack(boolean accepted, long id) {
        return accepted
                ? ResponseEntity.accepted().body(new CommandAck(true, id))
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new CommandAck(false, id));
    }

    private static Order toOrder(long id, OrderRequest r) {
        Side side = Side.valueOf(r.side().trim().toUpperCase());
        OrderType type = OrderType.valueOf(r.type().trim().toUpperCase());
        long account = r.accountId() == null ? DEFAULT_ACCOUNT : r.accountId();
        long price = r.price() == null ? Order.NO_PRICE : r.price();
        long stopPrice = r.stopPrice() == null ? Order.NO_PRICE : r.stopPrice();
        return new Order(id, account, side, type, price, r.quantity(), stopPrice);
    }
}
