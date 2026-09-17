package com.qrmenu.kartapay;

import com.qrmenu.common.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Machine à états de {@link Order#transitionTo(OrderStatus)}, isolée de toute logique de
 * commande : PENDING -> CONFIRMED -> PREPARING -> READY -> COMPLETED, CANCELLED accessible
 * depuis tout état non terminal, aucun saut d'étape, rien après un état terminal.
 */
class OrderStatusTransitionTest {

    private Order newOrder() {
        return new Order(UUID.randomUUID(), "A0001", FulfillmentType.TAKEAWAY, null,
                "Client Test", 1000, 1000, "EUR");
    }

    @Test
    void newOrderStartsPending() {
        assertThat(newOrder().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void followsTheHappyPathStepByStep() {
        Order order = newOrder();

        order.transitionTo(OrderStatus.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        order.transitionTo(OrderStatus.PREPARING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);

        order.transitionTo(OrderStatus.READY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);

        order.transitionTo(OrderStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "PREPARING", "READY"})
    void canBeCancelledFromAnyNonTerminalState(OrderStatus status) {
        Order order = newOrder();
        advanceTo(order, status);

        order.transitionTo(OrderStatus.CANCELLED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void rejectsSkippingSteps() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.READY))
                .isInstanceOf(ConflictException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void completedOrderAcceptsNoFurtherTransition() {
        Order order = newOrder();
        advanceTo(order, OrderStatus.COMPLETED);

        for (OrderStatus target : OrderStatus.values()) {
            assertThatThrownBy(() -> order.transitionTo(target))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void cancelledOrderAcceptsNoFurtherTransition() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.CANCELLED);

        for (OrderStatus target : OrderStatus.values()) {
            assertThatThrownBy(() -> order.transitionTo(target))
                    .isInstanceOf(ConflictException.class);
        }
    }

    private static void advanceTo(Order order, OrderStatus target) {
        OrderStatus[] path = {OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COMPLETED};
        for (OrderStatus step : path) {
            if (order.getStatus() == target) {
                return;
            }
            order.transitionTo(step);
        }
    }
}
