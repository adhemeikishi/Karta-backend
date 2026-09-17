package com.qrmenu.kartapay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrmenu.common.ConflictException;
import com.qrmenu.common.NotFoundException;
import com.qrmenu.kartapay.OrderDtos.OrderLineResponse;
import com.qrmenu.kartapay.OrderDtos.OrderResponse;
import com.qrmenu.kartapay.OrderDtos.OrderSummaryResponse;
import com.qrmenu.kartapay.PublicOrderDtos.CreateOrderRequest;
import com.qrmenu.kartapay.PublicOrderDtos.CreateOrderResponse;
import com.qrmenu.kartapay.PublicOrderDtos.OrderLineRequest;
import com.qrmenu.kartapay.PublicOrderDtos.PublicOrderStatusResponse;
import com.qrmenu.menu.MenuCategory;
import com.qrmenu.menu.MenuCategoryRepository;
import com.qrmenu.menu.MenuItem;
import com.qrmenu.menu.MenuItemRepository;
import com.qrmenu.menu.MenuRepository;
import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeRepository;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantRepository;
import com.qrmenu.restaurant.RestaurantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cœur métier de Karta Pay : commande d'un client, du panier à la commande enregistrée.
 *
 * Le prix n'est jamais fait confiance au client : {@link #createOrder} recalcule chaque
 * ligne à partir des données serveur actuelles (prix du produit, delta des options), et
 * fige le résultat dans un snapshot ({@link OrderLine}) qui ne bougera plus jamais, même
 * si le produit source change de prix ensuite.
 */
@Service
public class OrderService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestaurantService restaurantService;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository itemRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuRepository menuRepository;
    private final ModifierGroupRepository groupRepository;
    private final ModifierOptionRepository optionRepository;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final PaymentService paymentService;
    private final QrCodeRepository qrCodeRepository;

    public OrderService(
            RestaurantService restaurantService,
            RestaurantRepository restaurantRepository,
            MenuItemRepository itemRepository,
            MenuCategoryRepository categoryRepository,
            MenuRepository menuRepository,
            ModifierGroupRepository groupRepository,
            ModifierOptionRepository optionRepository,
            OrderRepository orderRepository,
            OrderLineRepository orderLineRepository,
            PaymentService paymentService,
            QrCodeRepository qrCodeRepository
    ) {
        this.restaurantService = restaurantService;
        this.restaurantRepository = restaurantRepository;
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.menuRepository = menuRepository;
        this.groupRepository = groupRepository;
        this.optionRepository = optionRepository;
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.paymentService = paymentService;
        this.qrCodeRepository = qrCodeRepository;
    }

    // ---------------------------------------------------------------- écriture

    @Transactional
    public CreateOrderResponse createOrder(UUID restaurantId, CreateOrderRequest request) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        if (!restaurant.isKartaPayEnabled()) {
            throw new ConflictException("Karta Pay n'est pas activé pour ce restaurant.");
        }

        List<LineComputation> computations = new ArrayList<>();
        for (OrderLineRequest lineRequest : request.lines()) {
            computations.add(computeLine(restaurantId, lineRequest));
        }

        String currency = computations.get(0).currency();
        int total = computations.stream().mapToInt(LineComputation::lineTotalCents).sum();

        String orderNumber = restaurant.nextOrderNumber();
        restaurantRepository.save(restaurant);

        Order order = new Order(
                restaurantId, orderNumber, request.fulfillmentType(), request.tableNumber(),
                request.customerName(), total, total, currency);
        orderRepository.save(order);

        for (int i = 0; i < computations.size(); i++) {
            LineComputation c = computations.get(i);
            orderLineRepository.save(new OrderLine(
                    order.getId(), c.itemId(), c.itemName(), c.unitPriceCents(),
                    c.quantity(), c.lineTotalCents(), c.modifiersSnapshotJson(), i));
        }

        paymentService.createPending(order);

        return new CreateOrderResponse(order.getOrderNumber(), order.getTotalCents(), order.getCurrency(), order.getStatus());
    }

    @Transactional
    public OrderResponse updateStatus(UUID restaurantId, UUID orderId, OrderStatus newStatus) {
        Order order = getOrderEntity(restaurantId, orderId);
        order.transitionTo(newStatus);
        orderRepository.save(order);
        return toResponse(order);
    }

    // ---------------------------------------------------------------- lecture

    /**
     * Historique des commandes d'un client. Par défaut ({@code allHistory = false}), seules
     * les commandes du jour courant (fuseau {@link ZoneId#systemDefault()}, même convention
     * que {@code QrScanService.restaurantStats}) sont retournées : l'onglet "Commandes" du
     * back-office se vide ainsi naturellement à minuit, sans purge ni tâche planifiée — rien
     * n'est jamais supprimé, seule la vue par défaut change.
     */
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> findByRestaurant(UUID restaurantId, OrderStatus statusFilter, boolean allHistory) {
        restaurantService.getOrThrow(restaurantId);
        List<Order> orders;
        if (allHistory) {
            orders = statusFilter == null
                    ? orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                    : orderRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, statusFilter);
        } else {
            OffsetDateTime startOfToday = startOfToday();
            orders = statusFilter == null
                    ? orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            restaurantId, startOfToday)
                    : orderRepository.findByRestaurantIdAndStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            restaurantId, statusFilter, startOfToday);
        }
        return orders.stream().map(this::toSummary).toList();
    }

    private static OffsetDateTime startOfToday() {
        ZoneId zone = ZoneId.systemDefault();
        return LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrThrow(UUID restaurantId, UUID orderId) {
        return toResponse(getOrderEntity(restaurantId, orderId));
    }

    /** Suivi public d'une commande : résout le restaurant via le code QR, comme {@code PublicMenuService.findPublic}. */
    @Transactional(readOnly = true)
    public PublicOrderStatusResponse findByOrderNumberPublic(String restaurantCode, String orderNumber) {
        UUID restaurantId = resolveRestaurantId(restaurantCode);
        Order order = orderRepository.findByRestaurantIdAndOrderNumber(restaurantId, orderNumber)
                .orElseThrow(() -> new NotFoundException("Commande introuvable: " + orderNumber));
        return new PublicOrderStatusResponse(
                order.getOrderNumber(), order.getStatus(), order.getTotalCents(), order.getCurrency(), order.getCreatedAt());
    }

    /** Résout un restaurant à partir de son code QR public, comme {@code PublicMenuService.findPublic}. */
    public UUID resolveRestaurantId(String restaurantCode) {
        return qrCodeRepository.findByCode(restaurantCode)
                .filter(QrCode::isActive)
                .map(QrCode::getRestaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant introuvable."));
    }

    // ---------------------------------------------------------------- internes

    private Order getOrderEntity(UUID restaurantId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Commande introuvable: " + orderId));
        if (!order.getRestaurantId().equals(restaurantId)) {
            throw new NotFoundException("Commande introuvable: " + orderId);
        }
        return order;
    }

    private LineComputation computeLine(UUID restaurantId, OrderLineRequest lineRequest) {
        MenuItem item = itemRepository.findById(lineRequest.itemId())
                .filter(candidate -> belongsToRestaurant(restaurantId, candidate))
                .orElseThrow(() -> new NotFoundException("Produit introuvable: " + lineRequest.itemId()));
        if (!item.isAvailable()) {
            throw new ConflictException("Produit indisponible: " + item.getName());
        }

        List<ModifierGroup> groups = groupRepository.findByItemIdOrderBySortOrderAscNameAsc(item.getId());
        Map<UUID, ModifierOption> optionsById = groups.isEmpty()
                ? Map.of()
                : optionRepository.findByGroupIdInOrderBySortOrderAscNameAsc(
                                groups.stream().map(ModifierGroup::getId).toList()).stream()
                        .collect(Collectors.toMap(ModifierOption::getId, Function.identity()));

        List<UUID> selectedIds = lineRequest.selectedOptionIds() == null ? List.of() : lineRequest.selectedOptionIds();
        List<ModifierOption> selected = new ArrayList<>();
        for (UUID optionId : selectedIds) {
            ModifierOption option = optionsById.get(optionId);
            if (option == null) {
                throw new NotFoundException("Option introuvable pour ce produit: " + optionId);
            }
            if (!option.isAvailable()) {
                throw new ConflictException("Option indisponible: " + option.getName());
            }
            selected.add(option);
        }

        Map<UUID, List<ModifierOption>> selectedByGroup = selected.stream()
                .collect(Collectors.groupingBy(ModifierOption::getGroupId));
        for (ModifierGroup group : groups) {
            int count = selectedByGroup.getOrDefault(group.getId(), List.of()).size();
            if (count < group.getMinSelect()) {
                throw new ConflictException("Sélection incomplète pour le groupe \"" + group.getName() + "\".");
            }
            if (group.getMaxSelect() != null && count > group.getMaxSelect()) {
                throw new ConflictException("Trop d'options sélectionnées pour le groupe \"" + group.getName() + "\".");
            }
        }

        int unitPriceCents = item.getPriceCents() + selected.stream().mapToInt(ModifierOption::getPriceDeltaCents).sum();
        int quantity = lineRequest.quantity();
        int lineTotalCents = unitPriceCents * quantity;

        return new LineComputation(
                item.getId(), item.getName(), unitPriceCents, quantity, lineTotalCents,
                snapshotJson(selected), item.getCurrency());
    }

    /** Le produit appartient-il à ce restaurant, via sa catégorie -> son menu ? */
    private boolean belongsToRestaurant(UUID restaurantId, MenuItem item) {
        return categoryRepository.findById(item.getCategoryId())
                .map(MenuCategory::getMenuId)
                .flatMap(menuRepository::findById)
                .map(menu -> menu.getRestaurantId().equals(restaurantId))
                .orElse(false);
    }

    private String snapshotJson(List<ModifierOption> selected) {
        if (selected.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, Object>> snapshot = selected.stream()
                    .map(o -> Map.<String, Object>of("name", o.getName(), "priceDeltaCents", o.getPriceDeltaCents()))
                    .toList();
            return JSON.writeValueAsString(snapshot);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Options de commande non sérialisables", e);
        }
    }

    private OrderSummaryResponse toSummary(Order order) {
        return new OrderSummaryResponse(
                order.getId(), order.getOrderNumber(), order.getStatus(), order.getFulfillmentType(),
                order.getTableNumber(), order.getCustomerName(), order.getTotalCents(), order.getCurrency(),
                order.getCreatedAt());
    }

    private OrderResponse toResponse(Order order) {
        List<OrderLineResponse> lines = orderLineRepository.findByOrderIdOrderBySortOrderAsc(order.getId()).stream()
                .map(line -> new OrderLineResponse(
                        line.getId(), line.getItemId(), line.getItemNameSnapshot(), line.getUnitPriceCentsSnapshot(),
                        line.getQuantity(), line.getLineTotalCents(), line.getModifiersSnapshot()))
                .toList();
        return new OrderResponse(
                order.getId(), order.getOrderNumber(), order.getStatus(), order.getFulfillmentType(),
                order.getTableNumber(), order.getCustomerName(), order.getSubtotalCents(), order.getTotalCents(),
                order.getCurrency(), order.getCreatedAt(), order.getUpdatedAt(), lines);
    }

    private record LineComputation(
            UUID itemId, String itemName, int unitPriceCents, int quantity, int lineTotalCents,
            String modifiersSnapshotJson, String currency
    ) {
    }
}
