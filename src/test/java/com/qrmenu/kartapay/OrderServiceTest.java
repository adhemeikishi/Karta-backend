package com.qrmenu.kartapay;

import com.qrmenu.common.ConflictException;
import com.qrmenu.common.NotFoundException;
import com.qrmenu.kartapay.OrderDtos.OrderResponse;
import com.qrmenu.kartapay.PublicOrderDtos.CreateOrderRequest;
import com.qrmenu.kartapay.PublicOrderDtos.CreateOrderResponse;
import com.qrmenu.kartapay.PublicOrderDtos.OrderLineRequest;
import com.qrmenu.menu.MenuDtos.SaveCategoryRequest;
import com.qrmenu.menu.MenuDtos.SaveItemRequest;
import com.qrmenu.menu.MenuService;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cœur métier de Karta Pay : le prix n'est jamais celui du client, toujours celui recalculé
 * côté serveur (voir {@link OrderService#createOrder}), et Karta Pay désactivé bloque tout
 * avant même de regarder le contenu du panier.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderServiceTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private RestaurantService restaurantService;
    @Autowired
    private MenuService menuService;
    @Autowired
    private OrderRepository orderRepository;

    private Restaurant payingRestaurant() {
        Restaurant r = restaurantService.create("Resto Pay " + System.nanoTime(), RestaurantOffer.PRO);
        restaurantService.changeKartaPayEnabled(r.getId(), true);
        return r;
    }

    private UUID createItem(UUID restaurantId, String name, int priceCents, boolean available) {
        return createItems(restaurantId, new SaveItemRequest(null, name, null, priceCents, "EUR", null, 0, available))
                .get(0);
    }

    /**
     * L'écriture du menu est un document complet (voir {@code MenuStructureService}) :
     * il faut envoyer tous les produits d'un même restaurant en un seul appel, sinon
     * chaque appel efface les produits créés par le précédent.
     */
    private List<UUID> createItems(UUID restaurantId, SaveItemRequest... items) {
        var menu = menuService.saveStructure(restaurantId, List.of(
                new SaveCategoryRequest(null, "Plats", null, 0, true, List.of(items))));
        return menu.structure().categories().get(0).items().stream().map(i -> i.id()).toList();
    }

    private static CreateOrderRequest oneLine(UUID itemId) {
        return new CreateOrderRequest(FulfillmentType.TAKEAWAY, null, "Client Test",
                List.of(new OrderLineRequest(itemId, 1, null)));
    }

    private OrderResponse reload(UUID restaurantId, String orderNumber) {
        Order order = orderRepository.findByRestaurantIdAndOrderNumber(restaurantId, orderNumber).orElseThrow();
        return orderService.getOrThrow(restaurantId, order.getId());
    }

    // ------------------------------------------------------------------ commande valide

    @Test
    void createsOrderEndToEndWithTotalRecalculatedFromServerPrices() {
        Restaurant r = payingRestaurant();
        List<UUID> items = createItems(r.getId(),
                new SaveItemRequest(null, "Burger", null, 1290, "EUR", null, 0, true),
                new SaveItemRequest(null, "Soda", null, 350, "EUR", null, 1, true));
        UUID burger = items.get(0);
        UUID soda = items.get(1);

        CreateOrderResponse response = orderService.createOrder(r.getId(), new CreateOrderRequest(
                FulfillmentType.TAKEAWAY, null, "Client Test",
                List.of(new OrderLineRequest(burger, 2, null), new OrderLineRequest(soda, 1, null))));

        assertThat(response.totalCents()).isEqualTo(1290 * 2 + 350);
        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

        OrderResponse full = reload(r.getId(), response.orderNumber());
        assertThat(full.totalCents()).isEqualTo(1290 * 2 + 350);
        assertThat(full.subtotalCents()).isEqualTo(full.totalCents());
        assertThat(full.lines()).hasSize(2);
    }

    // ------------------------------------------------------------------ rejets métier

    @Test
    void rejectsUnavailableItem() {
        Restaurant r = payingRestaurant();
        UUID item = createItem(r.getId(), "En rupture", 1000, false);

        assertThatThrownBy(() -> orderService.createOrder(r.getId(), oneLine(item)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsItemBelongingToAnotherRestaurant() {
        Restaurant a = payingRestaurant();
        Restaurant b = payingRestaurant();
        UUID itemOfB = createItem(b.getId(), "Plat de B", 900, true);

        assertThatThrownBy(() -> orderService.createOrder(a.getId(), oneLine(itemOfB)))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * {@link CreateOrderRequest} et {@link OrderLineRequest} n'ont aucun champ de prix
     * (voir PublicOrderDtos) : ce test ne fait que confirmer que le total livré est
     * exactement celui calculé à partir des prix serveur, jamais un autre.
     */
    @Test
    void clientCannotInfluenceThePriceInAnyWay() {
        Restaurant r = payingRestaurant();
        UUID item = createItem(r.getId(), "Menu du jour", 1550, true);

        CreateOrderResponse response = orderService.createOrder(r.getId(), new CreateOrderRequest(
                FulfillmentType.TAKEAWAY, null, "Client Test",
                List.of(new OrderLineRequest(item, 3, null))));

        assertThat(response.totalCents()).isEqualTo(1550 * 3);
    }

    /** Karta Pay désactivé doit bloquer avant toute autre vérification, même un panier invalide. */
    @Test
    void rejectsOrderWhenKartaPayDisabledBeforeAnyOtherCheck() {
        Restaurant r = restaurantService.create("Resto Sans Pay " + System.nanoTime(), RestaurantOffer.PRO);
        UUID itemThatDoesNotExist = UUID.randomUUID();

        assertThatThrownBy(() -> orderService.createOrder(r.getId(), oneLine(itemThatDoesNotExist)))
                .isInstanceOf(ConflictException.class); // pas NotFoundException : le refus Karta Pay passe avant
    }

    // ------------------------------------------------------------------ snapshot figé

    @Test
    void keepsThePriceSnapshotAfterTheSourceItemPriceChangesLater() {
        Restaurant r = payingRestaurant();
        UUID item = createItem(r.getId(), "Menu du jour", 1500, true);

        CreateOrderResponse response = orderService.createOrder(r.getId(), oneLine(item));

        // Le prix du produit change après la commande.
        createItemUpdate(r.getId(), item, "Menu du jour", 2000);

        OrderResponse reloaded = reload(r.getId(), response.orderNumber());
        assertThat(reloaded.lines().get(0).unitPriceCentsSnapshot()).isEqualTo(1500); // prix historique
        assertThat(reloaded.totalCents()).isEqualTo(1500); // pas 2000
    }

    private void createItemUpdate(UUID restaurantId, UUID itemId, String name, int newPriceCents) {
        var current = menuService.getMenu(restaurantId);
        var category = current.structure().categories().get(0);
        menuService.saveStructure(restaurantId, List.of(
                new SaveCategoryRequest(category.id(), category.name(), category.description(),
                        category.sortOrder(), category.visible(), List.of(
                                new SaveItemRequest(itemId, name, null, newPriceCents, "EUR", null, 0, true)))));
    }

    // ------------------------------------------------------------------ numérotation

    @Test
    void orderNumbersAreUniqueIncreasingAndNeverSharedAcrossRestaurants() {
        Restaurant a = payingRestaurant();
        Restaurant b = payingRestaurant();
        UUID itemA = createItem(a.getId(), "Plat A", 500, true);
        UUID itemB = createItem(b.getId(), "Plat B", 700, true);

        CreateOrderResponse a1 = orderService.createOrder(a.getId(), oneLine(itemA));
        CreateOrderResponse a2 = orderService.createOrder(a.getId(), oneLine(itemA));
        CreateOrderResponse b1 = orderService.createOrder(b.getId(), oneLine(itemB));

        assertThat(a1.orderNumber()).isEqualTo("A0001");
        assertThat(a2.orderNumber()).isEqualTo("A0002");
        assertThat(b1.orderNumber()).isEqualTo("A0001"); // séquence propre à B, jamais partagée avec A
    }
}
